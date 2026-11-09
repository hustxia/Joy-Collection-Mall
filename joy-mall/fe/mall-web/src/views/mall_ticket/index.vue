<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="工单号 / 标题 / 订单号" clearable style="width:220px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:130px" @change="search">
            <el-option label="待处理" :value="1" />
            <el-option label="已回复" :value="2" />
            <el-option label="已关闭" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt">
      <template #header><div>
        <span>客服工单</span>
        <span class="hint">超过 24 小时未首次回复的工单会自动标记「待处理超时」</span>
      </div></template>
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无工单">
        <el-table-column prop="ticketNo" label="工单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ categoryText(row.category) }}</template>
        </el-table-column>
        <el-table-column prop="orderNo" label="关联订单" width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag size="mini" :type="Number(row.status) === 1 ? 'warning' : (Number(row.status) === 2 ? 'success' : 'info')">
              {{ statusText(row.status) }}
            </el-tag>
            <el-tag v-if="Number(row.timeoutFlag) === 1" size="mini" type="danger" style="margin-left:4px">超时</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="170" />
        <el-table-column label="操作" width="110" align="right">
          <template #default="{ row }">
            <el-button type="text" @click="openReply(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" v-model:current-page="query.pageNum" :page-size="query.pageSize" @current-change="load" />
    </el-card>

    <el-dialog title="处理工单" v-model="visible" width="680px">
      <template v-if="detail.id">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="工单号">{{ detail.ticketNo }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ detail.categoryText || categoryText(detail.category) }}</el-descriptions-item>
          <el-descriptions-item label="会员">{{ detail.memberId }}</el-descriptions-item>
          <el-descriptions-item label="订单">{{ detail.orderNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        </el-descriptions>
        <div class="thread">
          <div v-for="(r, i) in detail.replies || []" :key="i" :class="['bubble', Number(r.replierType) === 2 ? 'staff' : 'member']">
            <span class="who">{{ Number(r.replierType) === 2 ? '客服' : '会员' }}</span>
            <span class="content">{{ r.content }}</span>
            <span class="time">{{ r.createTime }}</span>
          </div>
          <p v-if="!detail.replies || !detail.replies.length" class="empty">会员留言：{{ detail.content }}</p>
        </div>
        <el-form size="small">
          <el-form-item label="回复内容">
            <el-input v-model="replyContent" type="textarea" :rows="3" placeholder="回复会员（支持多行）" />
          </el-form-item>
          <el-form-item label="处理状态">
            <el-radio-group v-model="nextStatus">
              <el-radio :label="2">已回复</el-radio>
              <el-radio :label="3">关闭工单</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </template>
      <template #footer><span>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">提交</el-button>
      </span></template>
    </el-dialog>
  </div>
</template>

<script>
import { pageTicket, getTicket, replyTicket, ticketStatus } from '@/api/mall_ticket'

export default {
  name: 'MallTicket',
  data() {
    return {
      query: { keyword: '', status: null, pageNum: 1, pageSize: 10 },
      list: [],
      total: 0,
      loading: false,
      visible: false,
      saving: false,
      detail: {},
      replyContent: '',
      nextStatus: 2
    }
  },
  created() {
    this.load()
  },
  methods: {
    categoryText(c) {
      return { 1: '商品咨询', 2: '订单售后', 3: '物流', 4: '支付', 5: '其他' }[Number(c)] || '其他'
    },
    statusText(s) {
      return { 1: '待处理', 2: '已回复', 3: '已关闭' }[Number(s)] || '未知'
    },
    async load() {
      this.loading = true
      try {
        const res = await pageTicket(this.query)
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
    async openReply(row) {
      const res = await getTicket(row.id)
      this.detail = res.data || {}
      this.replyContent = ''
      this.nextStatus = Number(row.status) === 1 ? 2 : Number(row.status)
      this.visible = true
    },
    async submit() {
      if (!this.replyContent.trim()) {
        this.$message.warning('请填写回复内容')
        return
      }
      this.saving = true
      try {
        const id = this.detail.id
        await replyTicket(id, this.replyContent.trim())
        // 回复后同步状态：只改回复不改状态会让工单永远停在「待处理」
        await ticketStatus(id, this.nextStatus)
        this.$message.success('已回复')
        this.visible = false
        this.load()
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.mt { margin-top: 14px; }
.hint { color: #a8abb2; font-size: 12px; margin-left: 10px; }
.pager { margin-top: 12px; text-align: right; }
.thread { max-height: 220px; overflow-y: auto; margin: 12px 0; padding: 10px; background: #fafbfc; border-radius: 4px; }
.bubble { margin-bottom: 8px; font-size: 13px; color: #303133; }
.bubble.staff .who { color: #409eff; }
.bubble .who { font-weight: 600; margin-right: 6px; }
.bubble .time { color: #c0c4cc; font-size: 12px; margin-left: 8px; }
.empty { color: #909399; font-size: 13px; margin: 0; }
</style>
