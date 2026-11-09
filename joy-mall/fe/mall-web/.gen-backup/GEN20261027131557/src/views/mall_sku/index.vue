<template>
  <div class="app-container">
    <!-- <gen:begin key="vue-template"> -->
    <!-- 页头：一眼看清「我在哪个页面、这页能干什么」 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">商品SKU</h2>
        <p class="page-desc">维护商品SKU数据，可按条件筛选、编辑与导出</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 条记录</span>
      </div>
    </div>
    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <div slot="header" class="card-header">
        <div class="card-title">
          <span class="title">商品SKU</span>
          <span class="subtitle">共 {{ total }} 条</span>
        </div>
        <div class="card-actions">
          <el-button v-hasPermi="['mall_mall_sku:add']" type="primary" icon="el-icon-plus" size="small" @click="openDialog()">新增商品SKU</el-button>
          <el-button icon="el-icon-download" size="small" @click="handleExport">导出</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        class="data-table"
        stripe
        header-cell-class-name="table-head-cell"
        empty-text="暂无数据"
      >
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="goodsId" label="商品ID 关联mall_goods" width="260" align="right" show-overflow-tooltip />
        <el-table-column prop="skuCode" label="SKU编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="specJson" label="规格JSON 如[{"名称"" min-width="260" show-overflow-tooltip />
        <el-table-column prop="price" label="售价" width="110" align="right" show-overflow-tooltip />
        <el-table-column prop="stock" label="库存" width="110" align="right" show-overflow-tooltip />
        <el-table-column prop="sales" label="销量" width="124" align="right" show-overflow-tooltip />
        <el-table-column prop="image" label="SKU图URL" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" min-width="166" show-overflow-tooltip
          :formatter="(row) => enumText('status', row.status)" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="170" align="center" />
        <el-table-column label="操作" width="216" fixed="right" align="center">
          <template slot-scope="{ row }">
            <el-button type="text" @click="openDetail(row)">详情</el-button>
            <el-button v-hasPermi="['mall_mall_sku:edit']" type="text" @click="openDialog(row)">编辑</el-button>
            <el-button v-hasPermi="['mall_mall_sku:remove']" type="text" class="danger-btn" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :page-size="query.size"
        :current-page.sync="query.page"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="form.id ? '编辑商品SKU' : '新增商品SKU'" :visible.sync="dialogVisible" width="760px" top="6vh" class="form-dialog">
      <el-form ref="form" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="20">
          <el-col :span="12">
        <el-form-item label="商品ID 关联mall_goods" prop="goodsId">
          <el-input-number v-model="form.goodsId" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="SKU编码" prop="skuCode">
          <el-input v-model="form.skuCode" placeholder="请输入SKU编码" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="规格JSON 如[{"名称"" prop="specJson">
          <el-input v-model="form.specJson" placeholder="请输入规格JSON 如[{"名称"" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="售价" prop="price">
          <el-input-number v-model="form.price" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="销量" prop="sales">
          <el-input-number v-model="form.sales" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="SKU图URL" prop="image">
          <el-input v-model="form.image" placeholder="请输入SKU图URL" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" clearable style="width: 100%">
            <el-option v-for="d in enumMap.status || []" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" placeholder="请输入备注" />
        </el-form-item>
          </el-col>
        </el-row>
      </el-form>


      <div slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </div>
    </el-dialog>

    <!-- 详情抽屉（只读）。主子表：宽抽屉 + el-tabs，tab1 主表字段，tab2..N 每张明细一张只读表；
         数据复用 /with-children 一次拉全，不逐 tab 发请求 -->
    <el-drawer title="商品SKU详情" :visible.sync="detailVisible" size="420px">
      <div class="detail-body">
        <div class="detail-row">
          <span class="detail-label">ID</span>
          <span class="detail-value">{{ detail.id }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">商品ID 关联mall_goods</span>
          <span class="detail-value">{{ detail.goodsId }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">SKU编码</span>
          <span class="detail-value">{{ detail.skuCode }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">规格JSON 如[{"名称"</span>
          <span class="detail-value">{{ detail.specJson }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">售价</span>
          <span class="detail-value">{{ detail.price }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">库存</span>
          <span class="detail-value">{{ detail.stock }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">销量</span>
          <span class="detail-value">{{ detail.sales }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">SKU图URL</span>
          <span class="detail-value">{{ detail.image }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">状态</span>
          <span class="detail-value">{{ enumText('status', detail.status) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">备注</span>
          <span class="detail-value">{{ detail.remark }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">创建时间</span>
          <span class="detail-value">{{ detail.createTime }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">更新时间</span>
          <span class="detail-value">{{ detail.updateTime }}</span>
        </div>
      </div>
    </el-drawer>

    <!-- <gen:end key="vue-template"> -->
  </div>
</template>

<script>
import {
  getMallSkuList,
  exportMallSku,
  createMallSku,
  updateMallSku,
  deleteMallSku
} from '@/api/mall_sku'
// 字典兜底、唯一性查重、导入失败明细下载都直接用通用 request，不依赖宿主是否提供专用 api 模块
import request from '@/utils/request'

const defaultForm = () => ({
  id: undefined,
  goodsId: undefined,
  skuCode: '',
  specJson: '',
  price: undefined,
  stock: undefined,
  sales: undefined,
  image: '',
  status: '',
  remark: '',
})

export default {
  name: 'MallSku',
  data() {
    return {
      // <gen:begin key="vue-data">
      loading: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10 },
      dialogVisible: false,
      submitting: false,
      detailVisible: false,
      detail: {},
      dicts: { sys_status: [] },
      // 注释枚举内联在页面里：不依赖后端字典接口，页面秒开即可回显
      enumMap: {
        status: [{value: 1, label: '启用'}, {value: 0, label: '停用'}]
      },
      rules: {
        goodsId: [{ required: true, message: '请输入商品ID 关联mall_goods', trigger: 'blur' }],
        skuCode: [{ required: true, message: '请输入SKU编码', trigger: 'blur' }, { max: 32, message: 'SKU编码长度不能超过32个字符', trigger: 'blur' }],
        specJson: [{ max: 500, message: '规格JSON 如[{"名称"长度不能超过500个字符', trigger: 'blur' }],
        price: [{ required: true, message: '请输入售价', trigger: 'blur' }],
        stock: [{ required: true, message: '请输入库存', trigger: 'blur' }],
        sales: [{ required: true, message: '请输入销量', trigger: 'blur' }],
        image: [{ max: 500, message: 'SKU图URL长度不能超过500个字符', trigger: 'blur' }],
        status: [{ required: true, message: '请选择状态', trigger: 'change' }],
        remark: [{ max: 255, message: '备注长度不能超过255个字符', trigger: 'blur' }],
      },
      form: defaultForm()
      // <gen:end key="vue-data">
    }
  },
  created() {
    this.loadDicts()
    this.fetchList()
  },
  methods: {
    // <gen:begin key="vue-methods">
    async loadDicts() {
      const codes = ['sys_status']
      await Promise.all(codes.map(async code => {
        try {
          // 直接走通用 request，不依赖宿主工程是否提供 @/api/dict；
          // 宿主没有字典接口时这里会抛错，下面 catch 把选项降级为空数组，页面照常可用
          const res = await request({ url: '/api/system/dict/data/type/' + code, method: 'get' })
          this.$set(this.dicts, code, (res && res.data) || [])
        } catch (e) {
          this.$set(this.dicts, code, [])
        }
      }))
    },
    dictText(code, value) {
      const hit = (this.dicts[code] || []).find(d => String(d.value) === String(value))
      return hit ? hit.label : (value === undefined || value === null ? '' : value)
    },
    // 枚举翻译：值可能是数字也可能是字符串，统一按字符串比，避免 1 与 '1' 对不上
    enumText(field, value) {
      const hit = (this.enumMap[field] || []).find(d => String(d.value) === String(value))
      return hit ? hit.label : (value === undefined || value === null || value === '' ? '' : value)
    },
    async fetchList() {
      this.loading = true
      try {
        const res = await getMallSkuList(this.query)
        this.list = res.data.list || []
        this.total = res.data.total || 0
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    handleSearch() {
      this.query.page = 1
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: this.query.size }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.query.page = 1
      this.fetchList()
    },
    openDetail(row) {
      this.detail = row || {}
      this.detailVisible = true
    },
    openDialog(row) {
      this.form = row ? { ...defaultForm(), ...row } : defaultForm()
      this.dialogVisible = true
      this.$nextTick(() => {
        if (this.$refs.form) this.$refs.form.clearValidate()
      })
    },
    async submitForm() {
      const valid = await this.$refs.form.validate().catch(() => false)
      if (!valid) return
      this.submitting = true
      try {
        if (this.form.id) {
          await updateMallSku(this.form.id, this.form)
        } else {
          await createMallSku(this.form)
        }
        this.$message.success(this.form.id ? '更新成功' : '创建成功')
        this.dialogVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.submitting = false
      }
    },
    handleDelete(row) {
      this.$confirm(`确定删除该商品SKU吗？`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteMallSku(row.id)
          this.$message.success('删除成功')
          if (this.list.length === 1 && this.query.page > 1) this.query.page--
          this.fetchList()
        })
        .catch(() => {})
    },
    async handleExport() {
      try {
        const res = await exportMallSku(this.query)
        const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = '商品SKU.xlsx'
        a.click()
        window.URL.revokeObjectURL(url)
      } catch (e) { /* 拦截器已提示 */ }
    }
    // <gen:end key="vue-methods">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
  }
}
</script>

<style scoped>
/* <gen:begin key="vue-style"> */
.app-container {
  padding: 0;
  background: transparent;
}

/* ===== 页头 ===== */
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
  letter-spacing: 0.2px;
}

.page-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: #8a94a6;
}

.page-head__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 4px;
}

.meta-pill {
  padding: 4px 12px;
  border-radius: 20px;
  background: #eef2ff;
  color: #4d7cfe;
  font-size: 12px;
  font-weight: 500;
}

/* ===== 卡片 ===== */
.filter-card,
.table-card {
  border: none;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04), 0 6px 20px rgba(16, 24, 40, 0.05);
}

.filter-card {
  margin-bottom: 16px;
}

.filter-card >>> .el-card__body {
  padding: 18px 18px 4px;
}

.filter-card >>> .el-form-item {
  margin-right: 16px;
  margin-bottom: 14px;
}

.filter-card >>> .el-form-item__label {
  color: #5a6478;
  font-size: 13px;
}

.table-card >>> .el-card__header {
  padding: 16px 18px;
  border-bottom: 1px solid #eef1f7;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  display: flex;
  align-items: baseline;
}

.card-title .title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.card-title .subtitle {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.card-actions >>> .el-button + .el-button {
  margin-left: 8px;
}

/* ===== 表格 ===== */
.data-table >>> .table-head-cell {
  background: #f7f9fc !important;
  color: #5c6270;
  font-weight: 600;
}

.data-table >>> td {
  padding: 11px 0;
}

.data-table >>> .el-table__row:hover > td {
  background: #f5f9ff;
}

.data-table >>> .el-button--text {
  padding: 4px 6px;
}

/* ===== 分页 ===== */
.pagination {
  margin-top: 18px;
  text-align: right;
}

.danger-btn {
  color: #f56c6c;
}

.child-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.child-tip {
  color: #909399;
  font-size: 12px;
}

.import-summary {
  margin-bottom: 8px;
}

.import-tpl-tip {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: #606266;
}

.import-summary .ok {
  color: #67c23a;
}

.import-summary .bad {
  color: #f56c6c;
}

.import-fail-box {
  max-height: 200px;
  overflow: auto;
  padding: 8px;
  background: #fdf6ec;
  border-radius: 4px;
}

.import-fail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  font-weight: 600;
}

.import-fail {
  color: #f56c6c;
  font-size: 12px;
  line-height: 20px;
}

.import-fail-more {
  color: #909399;
  font-size: 12px;
}

.child-table {
  margin-bottom: 8px;
}

.detail-body {
  padding: 0 20px;
}

.detail-row {
  display: flex;
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
}

.detail-label {
  width: 110px;
  color: #909399;
  flex-shrink: 0;
}

.detail-value {
  color: #303133;
  word-break: break-all;
}

.import-result {
  margin-top: 12px;
  font-size: 13px;
}

.import-fail {
  color: #f56c6c;
}
</style>
/* <gen:end key="vue-style"> */
