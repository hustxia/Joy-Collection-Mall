<template>
  <div>
    <el-card shadow="never">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="商品名称">
          <el-input v-model="query.keyword" placeholder="输入名称搜索" clearable style="width:200px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px" @change="search">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
            <el-option label="草稿" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
          <el-button type="success" icon="el-icon-plus" @click="openCreate">发布商品</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt">
      <el-table v-loading="loading" :data="list" stripe empty-text="本店暂无商品，点「发布商品」新建">
        <el-table-column label="商品" min-width="280">
          <template #default="{ row }">
            <div class="goods-cell">
              <img :src="thumb(row.mainImage, 80)" class="thumb" alt="" />
              <div>
                <p class="name">{{ row.name }}</p>
                <p class="sub">{{ row.goodsNo }}<span v-if="row.subTitle"> · {{ row.subTitle }}</span></p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="140">
          <template #default="{ row }">
            <span class="price">¥{{ fenToYuan(row.price) }}</span>
            <span v-if="row.maxPrice && Number(row.maxPrice) > Number(row.price)" class="range">~{{ fenToYuan(row.maxPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sales" label="销量" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="mini" :type="Number(row.status) === 1 ? 'success' : (Number(row.status) === 0 ? 'info' : 'warning')">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="right">
          <template #default="{ row }">
            <el-button type="text" @click="openSkus(row)">SKU/库存</el-button>
            <el-button v-if="Number(row.status) === 1" type="text" style="color:#e6a23c" @click="setStatus(row, 0)">下架</el-button>
            <el-button v-else type="text" @click="setStatus(row, 1)">上架</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        v-model:current-page="query.pageNum"
        :page-size="query.pageSize"
        @current-change="load"
      />
    </el-card>

    <!-- SKU 与库存 -->
    <el-dialog title="SKU 与库存" v-model="skuVisible" width="720px">
      <p class="dialog-tip">库存改动立即生效；下架商品不影响已下单用户。</p>
      <el-table :data="skus" size="mini" empty-text="该商品暂无 SKU">
        <el-table-column prop="specJson" label="规格" min-width="180" />
        <el-table-column label="价格" width="110">
          <template #default="{ row }">¥{{ fenToYuan(row.price) }}</template>
        </el-table-column>
        <el-table-column label="库存" width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.stock" :min="0" :step="1" size="mini" controls-position="right" style="width:130px" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="text" :loading="row._saving" @click="saveStock(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 发布商品 -->
    <el-dialog title="发布商品" v-model="createVisible" width="620px">
      <el-form ref="form" :model="form" label-width="90px" size="small">
        <el-form-item label="商品名称" required>
          <el-input v-model="form.name" placeholder="如：悦集 X7 Pro 旗舰手机" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subTitle" placeholder="一句话卖点（选填）" />
        </el-form-item>
        <el-form-item label="类目ID">
          <el-input v-model.number="form.categoryId" placeholder="如 201" />
        </el-form-item>
        <el-form-item label="品牌ID">
          <el-input v-model.number="form.brandId" placeholder="选填" />
        </el-form-item>
        <el-form-item label="主图">
          <el-input v-model="form.mainImage" placeholder="/img/goods/xxx.png" />
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input v-model="form.priceYuan" placeholder="元，如 2999" style="width:160px" />
          <span class="unit">元</span>
        </el-form-item>
        <el-form-item label="库存" required>
          <el-input-number v-model="form.stock" :min="0" :step="1" size="small" />
        </el-form-item>
        <el-form-item label="上架">
          <el-switch v-model="form.onSale" />
        </el-form-item>
      </el-form>
      <template #footer><span>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">发布</el-button>
      </span></template>
    </el-dialog>
  </div>
</template>

<script>
import { goodsPage, goodsDetail, goodsStatus, skuStock, goodsSave } from '@/api/merchant'
import { fenToYuan, yuanToFen, thumb } from '@/utils/format'

export default {
  name: 'MerchantGoods',
  data() {
    return {
      query: { keyword: '', status: null, pageNum: 1, pageSize: 10 },
      list: [],
      total: 0,
      loading: false,
      skuVisible: false,
      skus: [],
      createVisible: false,
      saving: false,
      form: { name: '', subTitle: '', categoryId: null, brandId: null, mainImage: '', priceYuan: '', stock: 10, onSale: true }
    }
  },
  created() {
    this.load()
  },
  methods: {
    fenToYuan,
    thumb,
    statusText(s) {
      return { 1: '上架', 0: '下架', 2: '草稿' }[Number(s)] || '未知'
    },
    async load() {
      this.loading = true
      try {
        const res = await goodsPage(this.query)
        const d = res.data || {}
        this.list = d.records || []
        this.total = d.total || 0
      } finally {
        this.loading = false
      }
    },
    search() {
      this.query.pageNum = 1
      this.load()
    },
    async setStatus(row, status) {
      await goodsStatus(row.id, status)
      this.$message.success(status === 1 ? '已上架' : '已下架')
      this.load()
    },
    async openSkus(row) {
      const res = await goodsDetail(row.id)
      const d = res.data || {}
      this.skus = (d.skus || []).map(s => ({ ...s, _saving: false }))
      this.skuVisible = true
    },
    async saveStock(row) {
      row._saving = true
      try {
        await skuStock(row.id, row.stock)
        this.$message.success('库存已更新')
      } finally {
        row._saving = false
      }
    },
    openCreate() {
      this.form = { name: '', subTitle: '', categoryId: null, brandId: null, mainImage: '', priceYuan: '', stock: 10, onSale: true }
      this.createVisible = true
    },
    async submitCreate() {
      if (!this.form.name) {
        this.$message.warning('请填写商品名称')
        return
      }
      const price = yuanToFen(this.form.priceYuan)
      if (price <= 0) {
        this.$message.warning('请填写正确的价格')
        return
      }
      this.saving = true
      try {
        await goodsSave({
          name: this.form.name,
          subTitle: this.form.subTitle,
          categoryId: this.form.categoryId,
          brandId: this.form.brandId,
          mainImage: this.form.mainImage,
          status: this.form.onSale ? 1 : 2,
          // 单规格商品：SKU 由服务端补编码，价格/库存在这里给
          skus: [{ specJson: '默认规格', price, stock: this.form.stock, image: this.form.mainImage }]
        })
        this.$message.success('发布成功')
        this.createVisible = false
        this.search()
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.mt { margin-top: 14px; }
.goods-cell { display: flex; align-items: center; gap: 10px; }
.thumb { width: 40px; height: 40px; border-radius: 4px; object-fit: cover; background: #f5f7fa; }
.name { margin: 0; font-size: 13px; color: #303133; }
.sub { margin: 2px 0 0; font-size: 12px; color: #a8abb2; }
.price { color: #e1251b; font-weight: 600; }
.range { color: #a8abb2; font-size: 12px; margin-left: 2px; }
.pager { margin-top: 12px; text-align: right; }
.dialog-tip { margin: 0 0 10px; color: #909399; font-size: 12px; }
.unit { margin-left: 6px; color: #909399; }
</style>
