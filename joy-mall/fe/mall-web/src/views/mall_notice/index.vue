<template>
  <div class="app-container">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">站内消息台账</h2>
        <p class="page-desc">订单/售后/营销消息由业务状态机自动推送，本页只读查询，不提供修改</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 条记录</span>
      </div>
    </div>

    <!-- 筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="small" @submit.prevent>
        <el-form-item label="会员ID">
          <el-input v-model.trim="query.memberId" placeholder="会员ID" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width: 120px">
            <el-option label="订单" :value="1" />
            <el-option label="售后" :value="2" />
            <el-option label="营销" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="读取状态">
          <el-select v-model="query.readStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="未读" :value="0" />
            <el-option label="已读" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表（只读） -->
    <el-card shadow="never" class="table-card">
      <template #header><div class="card-header">
        <div class="card-title">
          <span class="title">站内消息</span>
          <span class="subtitle">共 {{ total }} 条</span>
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
        <el-table-column prop="id" label="ID" width="170" />
        <el-table-column prop="memberId" label="会员ID" width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="mini">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column prop="bizId" label="关联业务号" width="180" show-overflow-tooltip />
        <el-table-column label="读取状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.readStatus === 1 ? 'success' : 'warning'" size="mini">
              {{ row.readStatus === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="推送时间" width="170" align="center" />
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
  </div>
</template>

<script>
import { getMallNoticePage } from '@/api/mall_notice'

const TYPE_TEXT = { 1: '订单', 2: '售后', 3: '营销' }
const TYPE_TAG = { 1: '', 2: 'warning', 3: 'success' }

export default {
  name: 'MallNotice',
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10, memberId: '', type: undefined, readStatus: undefined }
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    typeText(t) {
      return TYPE_TEXT[t] || '未知'
    },
    typeTag(t) {
      return TYPE_TAG[t] || 'info'
    },
    async fetchList() {
      this.loading = true
      try {
        // 空字符串不带上，避免 Long 转换失败
        const params = { ...this.query }
        if (!params.memberId) delete params.memberId
        const res = await getMallNoticePage(params)
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
      this.query = { page: 1, size: this.query.size, memberId: '', type: undefined, readStatus: undefined }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.query.page = 1
      this.fetchList()
    }
  }
}
</script>

<style scoped>
.app-container {
  padding: 0;
  background: transparent;
}

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

.pagination {
  margin-top: 18px;
  text-align: right;
}
</style>
