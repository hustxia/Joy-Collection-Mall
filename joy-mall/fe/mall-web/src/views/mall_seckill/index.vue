<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="商品">
          <el-select v-model="query.goodsId" placeholder="全部" filterable clearable style="width:220px" @change="search">
            <el-option v-for="g in goodsOptions" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px" @change="search">
            <el-option label="未开始" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已结束" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
          <el-button type="success" icon="el-icon-plus" @click="openCreate">新建秒杀</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt">
      <template #header><div>
        <span>秒杀活动</span>
        <span class="hint">库存 Redis 预扣（Lua 原子）+ available_stock 乐观锁兜底；修改总量时已售量保留</span>
      </div></template>
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无秒杀活动">
        <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
        <el-table-column prop="goodsName" label="商品" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.goodsName || ('商品 ' + row.goodsId) }}</template>
        </el-table-column>
        <el-table-column label="原价" width="100" align="right">
          <template #default="{ row }">¥{{ fenToYuan(row.originPrice) }}</template>
        </el-table-column>
        <el-table-column label="秒杀价" width="100" align="right">
          <template #default="{ row }"><span style="color:#f56c6c">¥{{ fenToYuan(row.seckillPrice) }}</span></template>
        </el-table-column>
        <el-table-column label="库存(总/剩)" width="110" align="center">
          <template #default="{ row }">{{ row.totalStock }} / {{ row.availableStock }}</template>
        </el-table-column>
        <el-table-column prop="perLimit" label="限购" width="70" align="center" />
        <el-table-column label="时间窗" min-width="200">
          <template #default="{ row }">{{ short(row.startTime) }} ~ {{ short(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="mini" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="right">
          <template #default="{ row }">
            <el-button type="text" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="Number(row.status) !== 2" type="text" style="color:#e6a23c" @click="close(row)">结束</el-button>
            <el-button type="text" style="color:#f56c6c" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" v-model:current-page="query.pageNum" :page-size="query.pageSize" @current-change="load" />
    </el-card>

    <el-dialog :title="form.id ? '编辑秒杀活动' : '新建秒杀活动'" v-model="visible" width="600px">
      <el-form ref="form" :model="form" label-width="100px" size="small">
        <el-form-item label="商品" required>
          <el-select v-model="form.goodsId" filterable clearable placeholder="选择商品" style="width:100%" @change="onGoodsChange">
            <el-option v-for="g in goodsOptions" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="SKU" required>
          <el-select v-model="form.skuId" filterable clearable placeholder="先选商品，再选规格" style="width:100%" @change="onSkuChange">
            <el-option v-for="s in skuOptions" :key="s.id" :label="skuLabel(s)" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="原价" required>
          <el-input v-model="form.originPriceYuan" style="width:160px"><template #append>元</template></el-input>
        </el-form-item>
        <el-form-item label="秒杀价" required>
          <el-input v-model="form.seckillPriceYuan" style="width:160px"><template #append>元</template></el-input>
        </el-form-item>
        <el-form-item label="总库存" required>
          <el-input-number v-model="form.totalStock" :min="1" size="small" />
        </el-form-item>
        <el-form-item label="每人限购">
          <el-input-number v-model="form.perLimit" :min="1" size="small" />
        </el-form-item>
        <el-form-item label="时间窗" required>
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="开始" value-format="yyyy-MM-dd HH:mm:ss" style="width:200px" />
          <span class="dash">~</span>
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="结束" value-format="yyyy-MM-dd HH:mm:ss" style="width:200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:160px">
            <el-option label="未开始" :value="0" />
            <el-option label="进行中" :value="1" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><span>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </span></template>
    </el-dialog>
  </div>
</template>

<script>
import { pageSeckill, getSeckill, saveSeckill, closeSeckill, removeSeckill } from '@/api/mall_seckill'
import { getMallGoodsList } from '@/api/mall_goods'
import { getMallSkuList } from '@/api/mall_sku'
import { fenToYuan, yuanToFen } from '@/utils/format'

export default {
  name: 'MallSeckill',
  data() {
    return {
      query: { goodsId: '', status: null, pageNum: 1, pageSize: 10 },
      list: [],
      total: 0,
      loading: false,
      visible: false,
      saving: false,
      form: {},
      goodsOptions: [],
      allSkus: [],
      skuOptions: []
    }
  },
  created() {
    this.load()
    this.loadOptions()
  },
  methods: {
    fenToYuan,
    skuLabel(s) {
      let spec = ''
      try {
        const arr = JSON.parse(s.specJson || '[]')
        spec = (Array.isArray(arr) ? arr : []).map(x => (typeof x === 'string' ? x : [x.k, x.v].filter(Boolean).join(': '))).join(' / ')
      } catch (e) { spec = '' }
      return [s.skuCode || 'SKU', spec, s.price != null ? '¥' + fenToYuan(s.price) : '', s.stock != null ? '库存 ' + s.stock : ''].filter(Boolean).join(' | ')
    },
    async loadOptions() {
      const [g, s] = await Promise.all([
        getMallGoodsList({ page: 1, size: 200 }),
        getMallSkuList({ page: 1, size: 500 })
      ])
      this.goodsOptions = (g.data && g.data.list) || []
      this.allSkus = (s.data && s.data.list) || []
    },
    onGoodsChange() {
      this.form.skuId = ''
      this.skuOptions = this.allSkus.filter(s => String(s.goodsId) === String(this.form.goodsId))
    },
    onSkuChange() {
      const sku = this.allSkus.find(s => String(s.id) === String(this.form.skuId))
      if (sku && sku.price != null) {
        this.form.originPriceYuan = fenToYuan(sku.price)
      }
    },
    short(t) {
      return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
    },
    statusText(s) {
      return { 0: '未开始', 1: '进行中', 2: '已结束' }[Number(s)] || '未知'
    },
    statusTag(s) {
      return { 0: 'info', 1: 'success', 2: 'danger' }[Number(s)] || 'info'
    },
    async load() {
      this.loading = true
      try {
        const res = await pageSeckill({
          pageNum: this.query.pageNum,
          pageSize: this.query.pageSize,
          status: this.query.status === '' ? null : this.query.status,
          goodsId: this.query.goodsId || null
        })
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
    openCreate() {
      this.form = { goodsId: '', skuId: '', originPriceYuan: '', seckillPriceYuan: '', totalStock: 100, perLimit: 1, startTime: '', endTime: '', status: 0 }
      this.skuOptions = []
      this.visible = true
    },
    async openEdit(row) {
      const res = await getSeckill(row.id)
      const d = res.data || {}
      this.form = {
        id: d.id,
        goodsId: d.goodsId,
        skuId: d.skuId,
        originPriceYuan: d.originPrice != null ? fenToYuan(d.originPrice) : '',
        seckillPriceYuan: d.seckillPrice != null ? fenToYuan(d.seckillPrice) : '',
        totalStock: d.totalStock || 1,
        perLimit: d.perLimit || 1,
        startTime: d.startTime,
        endTime: d.endTime,
        status: Number(d.status) === 2 ? 1 : Number(d.status)
      }
      this.skuOptions = this.allSkus.filter(s => String(s.goodsId) === String(d.goodsId))
      this.visible = true
    },
    async submit() {
      if (!this.form.goodsId || !this.form.skuId) {
        this.$message.warning('请选择商品与SKU')
        return
      }
      if (!this.form.startTime || !this.form.endTime) {
        this.$message.warning('请选择活动时间窗')
        return
      }
      const payload = {
        id: this.form.id || undefined,
        goodsId: this.form.goodsId,
        skuId: this.form.skuId,
        originPrice: yuanToFen(this.form.originPriceYuan),
        seckillPrice: yuanToFen(this.form.seckillPriceYuan),
        totalStock: this.form.totalStock,
        perLimit: this.form.perLimit,
        startTime: this.form.startTime,
        endTime: this.form.endTime,
        status: this.form.status
      }
      if (payload.seckillPrice > payload.originPrice) {
        this.$message.warning('秒杀价不能高于原价')
        return
      }
      this.saving = true
      try {
        await saveSeckill(payload)
        this.$message.success('已保存')
        this.visible = false
        this.load()
      } finally {
        this.saving = false
      }
    },
    async close(row) {
      await this.$confirm(`结束秒杀活动「${row.goodsName || row.goodsId}」？结束买家不可再抢。`, '结束确认', { type: 'warning' })
      await closeSeckill(row.id)
      this.$message.success('已结束')
      this.load()
    },
    async remove(row) {
      await this.$confirm(`删除秒杀活动「${row.goodsName || row.goodsId}」？`, '删除确认', { type: 'warning' })
      await removeSeckill(row.id)
      this.$message.success('已删除')
      this.load()
    }
  }
}
</script>

<style scoped>
.mt { margin-top: 14px; }
.hint { color: #a8abb2; font-size: 12px; margin-left: 8px; }
.dash { margin: 0 6px; color: #c0c4cc; }
.pager { margin-top: 12px; text-align: right; }
</style>
