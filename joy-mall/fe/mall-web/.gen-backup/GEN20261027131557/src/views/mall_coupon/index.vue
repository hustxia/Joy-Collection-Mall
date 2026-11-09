<template>
  <div class="app-container">
    <!-- <gen:begin key="vue-template"> -->
    <!-- 页头：一眼看清「我在哪个页面、这页能干什么」 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">优惠券</h2>
        <p class="page-desc">维护优惠券数据，可按条件筛选、编辑与导出</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 条记录</span>
      </div>
    </div>
    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <div slot="header" class="card-header">
        <div class="card-title">
          <span class="title">优惠券</span>
          <span class="subtitle">共 {{ total }} 条</span>
        </div>
        <div class="card-actions">
          <el-button v-hasPermi="['mall_mall_coupon:add']" type="primary" icon="el-icon-plus" size="small" @click="openDialog()">新增优惠券</el-button>
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
        <el-table-column prop="name" label="券名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" min-width="194" show-overflow-tooltip
          :formatter="(row) => enumText('type', row.type)" />
        <el-table-column prop="amount" label="面额" width="110" align="right" show-overflow-tooltip />
        <el-table-column prop="discountRate" label="折扣率 如95=9.5折" width="208" align="right" show-overflow-tooltip />
        <el-table-column prop="thresholdAmount" label="使用门槛" width="222" align="right" show-overflow-tooltip />
        <el-table-column prop="totalCount" label="发放总量" width="110" align="right" show-overflow-tooltip />
        <el-table-column prop="receivedCount" label="已领取数" width="110" align="right" show-overflow-tooltip />
        <el-table-column prop="perLimit" label="每人限领" width="110" align="right" show-overflow-tooltip />
        <el-table-column prop="startTime" label="生效时间" width="170" align="center" show-overflow-tooltip />
        <el-table-column prop="endTime" label="失效时间" width="170" align="center" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" min-width="166" show-overflow-tooltip
          :formatter="(row) => enumText('status', row.status)" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="170" align="center" />
        <el-table-column label="操作" width="216" fixed="right" align="center">
          <template slot-scope="{ row }">
            <el-button type="text" @click="openDetail(row)">详情</el-button>
            <el-button v-hasPermi="['mall_mall_coupon:edit']" type="text" @click="openDialog(row)">编辑</el-button>
            <el-button v-hasPermi="['mall_mall_coupon:remove']" type="text" class="danger-btn" @click="handleDelete(row)">删除</el-button>
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
    <el-dialog :title="form.id ? '编辑优惠券' : '新增优惠券'" :visible.sync="dialogVisible" width="760px" top="6vh" class="form-dialog">
      <el-form ref="form" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="20">
          <el-col :span="12">
        <el-form-item label="券名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入券名称" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型" clearable style="width: 100%">
            <el-option v-for="d in enumMap.type || []" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="面额" prop="amount">
          <el-input-number v-model="form.amount" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="折扣率 如95=9.5折" prop="discountRate">
          <el-input-number v-model="form.discountRate" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="使用门槛" prop="thresholdAmount">
          <el-input-number v-model="form.thresholdAmount" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="发放总量" prop="totalCount">
          <el-input-number v-model="form.totalCount" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="已领取数" prop="receivedCount">
          <el-input-number v-model="form.receivedCount" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="每人限领" prop="perLimit">
          <el-input-number v-model="form.perLimit" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="生效时间" prop="startTime">
          <el-date-picker v-model="form.startTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择生效时间" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="失效时间" prop="endTime">
          <el-date-picker v-model="form.endTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择失效时间" style="width: 100%" />
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
    <el-drawer title="优惠券详情" :visible.sync="detailVisible" size="420px">
      <div class="detail-body">
        <div class="detail-row">
          <span class="detail-label">ID</span>
          <span class="detail-value">{{ detail.id }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">券名称</span>
          <span class="detail-value">{{ detail.name }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">类型</span>
          <span class="detail-value">{{ enumText('type', detail.type) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">面额</span>
          <span class="detail-value">{{ detail.amount }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">折扣率 如95=9.5折</span>
          <span class="detail-value">{{ detail.discountRate }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">使用门槛</span>
          <span class="detail-value">{{ detail.thresholdAmount }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">发放总量</span>
          <span class="detail-value">{{ detail.totalCount }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">已领取数</span>
          <span class="detail-value">{{ detail.receivedCount }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">每人限领</span>
          <span class="detail-value">{{ detail.perLimit }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">生效时间</span>
          <span class="detail-value">{{ detail.startTime }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">失效时间</span>
          <span class="detail-value">{{ detail.endTime }}</span>
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
  getMallCouponList,
  exportMallCoupon,
  createMallCoupon,
  updateMallCoupon,
  deleteMallCoupon
} from '@/api/mall_coupon'
// 字典兜底、唯一性查重、导入失败明细下载都直接用通用 request，不依赖宿主是否提供专用 api 模块
import request from '@/utils/request'

const defaultForm = () => ({
  id: undefined,
  name: '',
  type: '',
  amount: undefined,
  discountRate: undefined,
  thresholdAmount: undefined,
  totalCount: undefined,
  receivedCount: undefined,
  perLimit: undefined,
  startTime: undefined,
  endTime: undefined,
  status: '',
  remark: '',
})

export default {
  name: 'MallCoupon',
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
      dicts: { mall_mall_coupon_type: [], sys_status: [] },
      // 注释枚举内联在页面里：不依赖后端字典接口，页面秒开即可回显
      enumMap: {
        type: [{value: 1, label: '满减券'}, {value: 2, label: '折扣券'}],
        status: [{value: 1, label: '上架'}, {value: 0, label: '下架'}]
      },
      rules: {
        name: [{ required: true, message: '请输入券名称', trigger: 'blur' }, { max: 50, message: '券名称长度不能超过50个字符', trigger: 'blur' }],
        type: [{ required: true, message: '请选择类型', trigger: 'change' }],
        amount: [{ required: true, message: '请输入面额', trigger: 'blur' }],
        thresholdAmount: [{ required: true, message: '请输入使用门槛', trigger: 'blur' }],
        totalCount: [{ required: true, message: '请输入发放总量', trigger: 'blur' }],
        receivedCount: [{ required: true, message: '请输入已领取数', trigger: 'blur' }],
        perLimit: [{ required: true, message: '请输入每人限领', trigger: 'blur' }],
        startTime: [{ required: true, message: '请选择生效时间', trigger: 'change' }],
        endTime: [{ required: true, message: '请选择失效时间', trigger: 'change' }],
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
      const codes = ['mall_mall_coupon_type', 'sys_status']
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
        const res = await getMallCouponList(this.query)
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
          await updateMallCoupon(this.form.id, this.form)
        } else {
          await createMallCoupon(this.form)
        }
        this.$message.success(this.form.id ? '更新成功' : '创建成功')
        this.dialogVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.submitting = false
      }
    },
    handleDelete(row) {
      this.$confirm(`确定删除该优惠券吗？`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteMallCoupon(row.id)
          this.$message.success('删除成功')
          if (this.list.length === 1 && this.query.page > 1) this.query.page--
          this.fetchList()
        })
        .catch(() => {})
    },
    async handleExport() {
      try {
        const res = await exportMallCoupon(this.query)
        const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = '优惠券.xlsx'
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
