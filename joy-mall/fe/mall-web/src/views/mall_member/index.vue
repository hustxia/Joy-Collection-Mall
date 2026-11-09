<template>
  <div class="app-container">
    <!-- <gen:begin key="vue-template"> -->
    <!-- 页头：一眼看清「我在哪个页面、这页能干什么」 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">会员</h2>
        <p class="page-desc">维护会员数据，可按条件筛选、编辑与导出</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 条记录</span>
      </div>
    </div>
    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <template #header><div class="card-header">
        <div class="card-title">
          <span class="title">会员</span>
          <span class="subtitle">共 {{ total }} 条</span>
        </div>
        <div class="card-actions">
          <el-button v-hasPermi="['mall_mall_member:add']" type="primary" icon="el-icon-plus" size="small" @click="openDialog()">新增会员</el-button>
          <el-button icon="el-icon-download" size="small" @click="handleExport">导出</el-button>
        </div>
      </div></template>

      <el-table
        v-loading="loading"
        :data="list"
        class="data-table"
        stripe
        header-cell-class-name="table-head-cell"
        empty-text="暂无数据"
      >
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="nickname" label="昵称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="avatar" label="头像" width="110">
          <template #default="{ row }">
            <el-image v-if="row.avatar" :src="row.avatar" :preview-src-list="[row.avatar]" fit="cover" style="width: 80px; height: 80px; border-radius: 50%; display: block;" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="openid" label="微信openid" min-width="152" show-overflow-tooltip />
        <el-table-column prop="unionid" label="微信unionid" min-width="166" show-overflow-tooltip />
        <el-table-column prop="gender" label="性别" min-width="180" show-overflow-tooltip
          :formatter="(row) => enumText('gender', row.gender)" />
        <el-table-column prop="source" label="注册来源" min-width="208" show-overflow-tooltip
          :formatter="(row) => enumText('source', row.source)" />
        <el-table-column prop="points" label="积分" width="110" align="right" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" min-width="166" show-overflow-tooltip
          :formatter="(row) => enumText('status', row.status)" />
        <el-table-column prop="lastLoginTime" label="最后登录时间" width="170" align="center" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="170" align="center" />
        <el-table-column label="操作" width="216" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="text" @click="openDetail(row)">详情</el-button>
            <el-button v-hasPermi="['mall_mall_member:edit']" type="text" @click="openDialog(row)">编辑</el-button>
            <el-button v-hasPermi="['mall_mall_member:remove']" type="text" class="danger-btn" @click="handleDelete(row)">删除</el-button>
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
        v-model:current-page="query.page"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="form.id ? '编辑会员' : '新增会员'" v-model="dialogVisible" width="760px" top="6vh" class="form-dialog">
      <el-form ref="form" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="20">
          <el-col :span="12">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="头像URL" prop="avatar">
          <image-upload v-model="form.avatar" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="微信openid" prop="openid">
          <el-input v-model="form.openid" placeholder="请输入微信openid" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="微信unionid" prop="unionid">
          <el-input v-model="form.unionid" placeholder="请输入微信unionid" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="性别" prop="gender">
          <el-select v-model="form.gender" placeholder="请选择性别" clearable style="width: 100%">
            <el-option v-for="d in enumMap.gender || []" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="注册来源" prop="source">
          <el-select v-model="form.source" placeholder="请选择注册来源" clearable style="width: 100%">
            <el-option v-for="d in enumMap.source || []" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="积分" prop="points">
          <el-input-number v-model="form.points" controls-position="right" style="width: 100%" />
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
        <el-form-item label="最后登录时间" prop="lastLoginTime">
          <el-date-picker v-model="form.lastLoginTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择最后登录时间" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" placeholder="请输入备注" />
        </el-form-item>
          </el-col>
        </el-row>
      </el-form>


      <template #footer><div>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </div></template>
    </el-dialog>

    <!-- 详情抽屉（只读）。主子表：宽抽屉 + el-tabs，tab1 主表字段，tab2..N 每张明细一张只读表；
         数据复用 /with-children 一次拉全，不逐 tab 发请求 -->
    <el-drawer title="会员详情" v-model="detailVisible" size="420px">
      <div class="detail-body">
        <div class="detail-row">
          <span class="detail-label">ID</span>
          <span class="detail-value">{{ detail.id }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">昵称</span>
          <span class="detail-value">{{ detail.nickname }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">头像URL</span>
          <el-image v-if="detail.avatar" :src="detail.avatar" :preview-src-list="[detail.avatar]" fit="cover" style="width: 120px; height: 120px; border-radius: 50%; vertical-align: middle;" />
          <span v-else class="detail-value">-</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">手机号</span>
          <span class="detail-value">{{ detail.phone }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">微信openid</span>
          <span class="detail-value">{{ detail.openid }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">微信unionid</span>
          <span class="detail-value">{{ detail.unionid }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">性别</span>
          <span class="detail-value">{{ enumText('gender', detail.gender) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">注册来源</span>
          <span class="detail-value">{{ enumText('source', detail.source) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">积分</span>
          <span class="detail-value">{{ detail.points }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">状态</span>
          <span class="detail-value">{{ enumText('status', detail.status) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">最后登录时间</span>
          <span class="detail-value">{{ detail.lastLoginTime }}</span>
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
  getMallMemberList,
  exportMallMember,
  createMallMember,
  updateMallMember,
  deleteMallMember
} from '@/api/mall_member'
// 字典兜底、唯一性查重、导入失败明细下载都直接用通用 request，不依赖宿主是否提供专用 api 模块
import request from '@/utils/request'
import ImageUpload from '@/components/ImageUpload.vue'

const defaultForm = () => ({
  id: undefined,
  nickname: '',
  avatar: '',
  phone: '',
  openid: '',
  unionid: '',
  gender: '',
  source: '',
  points: undefined,
  status: '',
  lastLoginTime: undefined,
  remark: '',
})

export default {
  name: 'MallMember',
  components: { ImageUpload },
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
        gender: [{value: 1, label: '男'}, {value: 2, label: '女'}, {value: 0, label: '未知'}],
        source: [{value: 1, label: '小程序'}, {value: 2, label: 'H'}],
        status: [{value: 1, label: '正常'}, {value: 0, label: '禁用'}]
      },
      rules: {
        nickname: [{ max: 50, message: '昵称长度不能超过50个字符', trigger: 'blur' }],
        avatar: [{ max: 500, message: '头像URL长度不能超过500个字符', trigger: 'blur' }],
        phone: [{ max: 20, message: '手机号长度不能超过20个字符', trigger: 'blur' }],
        openid: [{ max: 64, message: '微信openid长度不能超过64个字符', trigger: 'blur' }],
        unionid: [{ max: 64, message: '微信unionid长度不能超过64个字符', trigger: 'blur' }],
        gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
        source: [{ required: true, message: '请选择注册来源', trigger: 'change' }],
        points: [{ required: true, message: '请输入积分', trigger: 'blur' }],
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
          const res = await request({ url: '/system/dict/data/type/' + code, method: 'get' })
          this.dicts[code] = (res && res.data) || []
        } catch (e) {
          this.dicts[code] = []
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
        const res = await getMallMemberList(this.query)
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
          await updateMallMember(this.form.id, this.form)
        } else {
          await createMallMember(this.form)
        }
        this.$message.success(this.form.id ? '更新成功' : '创建成功')
        this.dialogVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.submitting = false
      }
    },
    handleDelete(row) {
      this.$confirm(`确定删除该会员吗？`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteMallMember(row.id)
          this.$message.success('删除成功')
          if (this.list.length === 1 && this.query.page > 1) this.query.page--
          this.fetchList()
        })
        .catch(() => {})
    },
    async handleExport() {
      try {
        const res = await exportMallMember(this.query)
        const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = '会员.xlsx'
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

.filter-card :deep(.el-card__body){
  padding: 18px 18px 4px;
}

.filter-card :deep(.el-form-item){
  margin-right: 16px;
  margin-bottom: 14px;
}

.filter-card :deep(.el-form-item__label){
  color: #5a6478;
  font-size: 13px;
}

.table-card :deep(.el-card__header){
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

.card-actions :deep(.el-button + .el-button){
  margin-left: 8px;
}

/* ===== 表格 ===== */
.data-table :deep(.table-head-cell){
  background: #f7f9fc !important;
  color: #5c6270;
  font-weight: 600;
}

.data-table :deep(td){
  padding: 11px 0;
}

.data-table :deep(.el-table__row:hover > td){
  background: #f5f9ff;
}

.data-table :deep(.el-button--text){
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
