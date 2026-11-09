<template>
  <div class="app-container">
    <!-- <gen:begin key="vue-template"> -->
    <!-- 页头：一眼看清「我在哪个页面、这页能干什么」 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">收货地址</h2>
        <p class="page-desc">维护收货地址数据，可按条件筛选、编辑与导出</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 条记录</span>
      </div>
    </div>
    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <div slot="header" class="card-header">
        <div class="card-title">
          <span class="title">收货地址</span>
          <span class="subtitle">共 {{ total }} 条</span>
        </div>
        <div class="card-actions">
          <el-button v-hasPermi="['mall_mall_address:add']" type="primary" icon="el-icon-plus" size="small" @click="openDialog()">新增收货地址</el-button>
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
        <el-table-column prop="memberId" label="会员ID 关联mall_member" width="260" align="right" show-overflow-tooltip />
        <el-table-column prop="receiverName" label="收货人" min-width="140" show-overflow-tooltip />
        <el-table-column prop="receiverPhone" label="联系电话" min-width="140" show-overflow-tooltip />
        <el-table-column prop="province" label="省" min-width="140" show-overflow-tooltip />
        <el-table-column prop="city" label="市" min-width="140" show-overflow-tooltip />
        <el-table-column prop="district" label="区" min-width="140" show-overflow-tooltip />
        <el-table-column prop="detail" label="详细地址" min-width="140" show-overflow-tooltip />
        <el-table-column prop="isDefault" label="默认地址" min-width="208" show-overflow-tooltip
          :formatter="(row) => enumText('isDefault', row.isDefault)" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="170" align="center" />
        <el-table-column label="操作" width="216" fixed="right" align="center">
          <template slot-scope="{ row }">
            <el-button type="text" @click="openDetail(row)">详情</el-button>
            <el-button v-hasPermi="['mall_mall_address:edit']" type="text" @click="openDialog(row)">编辑</el-button>
            <el-button v-hasPermi="['mall_mall_address:remove']" type="text" class="danger-btn" @click="handleDelete(row)">删除</el-button>
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
    <el-dialog :title="form.id ? '编辑收货地址' : '新增收货地址'" :visible.sync="dialogVisible" width="760px" top="6vh" class="form-dialog">
      <el-form ref="form" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="20">
          <el-col :span="12">
        <el-form-item label="会员ID 关联mall_member" prop="memberId">
          <el-input-number v-model="form.memberId" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="收货人" prop="receiverName">
          <el-input v-model="form.receiverName" placeholder="请输入收货人" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="联系电话" prop="receiverPhone">
          <el-input v-model="form.receiverPhone" placeholder="请输入联系电话" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="省" prop="province">
          <el-input v-model="form.province" placeholder="请输入省" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="市" prop="city">
          <el-input v-model="form.city" placeholder="请输入市" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="区" prop="district">
          <el-input v-model="form.district" placeholder="请输入区" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="详细地址" prop="detail">
          <el-input v-model="form.detail" placeholder="请输入详细地址" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="默认地址" prop="isDefault">
          <el-select v-model="form.isDefault" placeholder="请选择默认地址" clearable style="width: 100%">
            <el-option v-for="d in enumMap.isDefault || []" :key="d.value" :label="d.label" :value="d.value" />
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
    <el-drawer title="收货地址详情" :visible.sync="detailVisible" size="420px">
      <div class="detail-body">
        <div class="detail-row">
          <span class="detail-label">ID</span>
          <span class="detail-value">{{ detail.id }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">会员ID 关联mall_member</span>
          <span class="detail-value">{{ detail.memberId }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">收货人</span>
          <span class="detail-value">{{ detail.receiverName }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">联系电话</span>
          <span class="detail-value">{{ detail.receiverPhone }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">省</span>
          <span class="detail-value">{{ detail.province }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">市</span>
          <span class="detail-value">{{ detail.city }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">区</span>
          <span class="detail-value">{{ detail.district }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">详细地址</span>
          <span class="detail-value">{{ detail.detail }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">默认地址</span>
          <span class="detail-value">{{ enumText('isDefault', detail.isDefault) }}</span>
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
  getMallAddressList,
  exportMallAddress,
  createMallAddress,
  updateMallAddress,
  deleteMallAddress
} from '@/api/mall_address'

const defaultForm = () => ({
  id: undefined,
  memberId: undefined,
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: '',
  remark: '',
})

export default {
  name: 'MallAddress',
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
      // 注释枚举内联在页面里：不依赖后端字典接口，页面秒开即可回显
      enumMap: {
        isDefault: [{value: 1, label: '默认'}, {value: 0, label: '非默认'}]
      },
      rules: {
        memberId: [{ required: true, message: '请输入会员ID 关联mall_member', trigger: 'blur' }],
        receiverName: [{ required: true, message: '请输入收货人', trigger: 'blur' }, { max: 50, message: '收货人长度不能超过50个字符', trigger: 'blur' }],
        receiverPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }, { max: 20, message: '联系电话长度不能超过20个字符', trigger: 'blur' }],
        province: [{ required: true, message: '请输入省', trigger: 'blur' }, { max: 50, message: '省长度不能超过50个字符', trigger: 'blur' }],
        city: [{ required: true, message: '请输入市', trigger: 'blur' }, { max: 50, message: '市长度不能超过50个字符', trigger: 'blur' }],
        district: [{ required: true, message: '请输入区', trigger: 'blur' }, { max: 50, message: '区长度不能超过50个字符', trigger: 'blur' }],
        detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }, { max: 255, message: '详细地址长度不能超过255个字符', trigger: 'blur' }],
        isDefault: [{ required: true, message: '请选择默认地址', trigger: 'change' }],
        remark: [{ max: 255, message: '备注长度不能超过255个字符', trigger: 'blur' }],
      },
      form: defaultForm()
      // <gen:end key="vue-data">
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    // <gen:begin key="vue-methods">
    // 枚举翻译：值可能是数字也可能是字符串，统一按字符串比，避免 1 与 '1' 对不上
    enumText(field, value) {
      const hit = (this.enumMap[field] || []).find(d => String(d.value) === String(value))
      return hit ? hit.label : (value === undefined || value === null || value === '' ? '' : value)
    },
    async fetchList() {
      this.loading = true
      try {
        const res = await getMallAddressList(this.query)
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
          await updateMallAddress(this.form.id, this.form)
        } else {
          await createMallAddress(this.form)
        }
        this.$message.success(this.form.id ? '更新成功' : '创建成功')
        this.dialogVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.submitting = false
      }
    },
    handleDelete(row) {
      this.$confirm(`确定删除该收货地址吗？`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteMallAddress(row.id)
          this.$message.success('删除成功')
          if (this.list.length === 1 && this.query.page > 1) this.query.page--
          this.fetchList()
        })
        .catch(() => {})
    },
    async handleExport() {
      try {
        const res = await exportMallAddress(this.query)
        const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = '收货地址.xlsx'
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
