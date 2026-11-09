<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:130px" @change="search">
            <el-option label="机器人" :value="0" />
            <el-option label="已转人工" :value="1" />
            <el-option label="已结束" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="意图">
          <el-select v-model="query.intent" placeholder="全部" clearable style="width:130px" @change="search">
            <el-option v-for="(t, v) in intentMap" :key="v" :label="t" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="摘要 / 会员ID" clearable style="width:200px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt">
      <template #header><div>
        <span>AI 客服会话记录</span>
        <span class="hint">机器人会话可自动应答；转人工后可在「客服工单」中跟进</span>
      </div></template>
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无会话">
        <el-table-column prop="id" label="会话ID" min-width="160" show-overflow-tooltip />
        <el-table-column prop="memberId" label="会员ID" width="140" show-overflow-tooltip />
        <el-table-column label="渠道" width="80">
          <template #default="{ row }">{{ row.channel }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="mini" :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="意图" width="100">
          <template #default="{ row }">{{ intentMap[row.intent] || row.intent || '—' }}</template>
        </el-table-column>
        <el-table-column prop="msgCount" label="消息数" width="80" />
        <el-table-column prop="summary" label="会话摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="lastActiveTime" label="最后活跃" width="170" />
        <el-table-column prop="ticketId" label="关联工单" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ticketId || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="right">
          <template #default="{ row }">
            <el-button type="text" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" v-model:current-page="query.pageNum" :page-size="query.pageSize" @current-change="load" />
    </el-card>

    <el-drawer title="会话详情" v-model="visible" size="720px">
      <div v-loading="detailLoading" class="drawer-body">
        <template v-if="detail.sessionId">
          <el-descriptions :column="3" size="small" border class="head-desc">
            <el-descriptions-item label="会员">{{ detail.memberId }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusText(detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="关联工单">{{ detail.ticketId || '—' }}</el-descriptions-item>
          </el-descriptions>
          <div class="thread">
            <template v-for="(m, i) in detail.messages || []" :key="m.id || i">
              <!-- system：灰色居中 -->
              <div v-if="m.role === 'system'" class="msg system">
                <span class="sys-text">{{ m.content }}</span>
                <span class="time">{{ m.createTime }}</span>
              </div>
              <!-- user 右对齐 / ai 左对齐 -->
              <div v-else :class="['msg', m.role]">
                <div :class="['bubble', m.role]">
                  <span class="who">{{ m.role === 'user' ? '会员' : 'AI 客服' }}</span>
                  <el-tag v-if="m.fallback" size="mini" type="danger" class="fb-tag">兜底</el-tag>
                  <span class="time">{{ m.createTime }}</span>
                  <div class="content">{{ m.content }}</div>
                  <div v-if="m.cards && m.cards.length" class="cards">
                    <div v-for="(c, ci) in m.cards" :key="ci" class="card">
                      <div class="card-title">{{ c.title }}<el-tag size="mini" type="info" class="card-type">{{ c.type }}</el-tag></div>
                      <div v-if="c.fields && c.fields.length" class="card-fields">
                        <div v-for="(f, fi) in c.fields" :key="fi" class="field">
                          <span class="label">{{ f.label }}</span>
                          <span class="value">{{ f.value }}</span>
                        </div>
                      </div>
                      <el-button v-if="c.action && c.action.url" type="text" class="card-btn" @click="goAction(c.action.url)">
                        {{ c.action.label || '查看详情' }} <i class="el-icon-right" />
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { pageChatSession, getChatSession } from '@/api/mall_chat'

export default {
  name: 'MallChatSession',
  data() {
    return {
      intentMap: { order: '订单咨询', logistics: '物流查询', refund: '退款售后', faq: '常见问题', chitchat: '闲聊', human: '转人工', guard: '敏感拦截' },
      query: { status: null, intent: null, keyword: '', pageNum: 1, pageSize: 10 },
      list: [],
      total: 0,
      loading: false,
      visible: false,
      detailLoading: false,
      detail: {}
    }
  },
  created() {
    this.load()
  },
  methods: {
    statusText(s) {
      return { 0: '机器人', 1: '已转人工', 2: '已结束' }[Number(s)] || '未知'
    },
    statusTagType(s) {
      return { 0: 'primary', 1: 'warning', 2: 'info' }[Number(s)] || 'info'
    },
    async load() {
      this.loading = true
      try {
        // 关键词留空不传，避免后端按空串过滤
        const params = { pageNum: this.query.pageNum, pageSize: this.query.pageSize }
        if (this.query.status !== null && this.query.status !== '') params.status = this.query.status
        if (this.query.intent) params.intent = this.query.intent
        if (this.query.keyword && this.query.keyword.trim()) params.keyword = this.query.keyword.trim()
        const res = await pageChatSession(params)
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
    async openDetail(row) {
      this.visible = true
      this.detailLoading = true
      this.detail = {}
      try {
        const res = await getChatSession(row.id)
        this.detail = res.data || {}
      } finally {
        this.detailLoading = false
      }
    },
    goAction(url) {
      // 站内路径走路由跳转，外链兜底新窗口打开
      if (/^https?:\/\//i.test(url)) {
        window.open(url, '_blank')
      } else {
        this.$router.push(url).catch(() => {})
      }
    }
  }
}
</script>

<style scoped>
.mt { margin-top: 14px; }
.hint { color: #a8abb2; font-size: 12px; margin-left: 10px; }
.pager { margin-top: 12px; text-align: right; }
.drawer-body { padding: 0 16px 16px; }
.head-desc { margin-bottom: 12px; }
.thread { display: flex; flex-direction: column; }
.msg { display: flex; margin-bottom: 12px; }
.msg.user { justify-content: flex-end; }
.msg.ai { justify-content: flex-start; }
.msg.system { justify-content: center; }
.sys-text { background: #f0f2f5; color: #909399; font-size: 12px; padding: 3px 10px; border-radius: 10px; }
.bubble { max-width: 78%; background: #fafbfc; border: 1px solid #ebeef5; border-radius: 6px; padding: 8px 10px; font-size: 13px; color: #303133; }
.bubble.user { background: #ecf5ff; border-color: #d9ecff; }
.bubble .who { font-weight: 600; margin-right: 6px; }
.bubble.user .who { color: #409eff; }
.bubble.ai .who { color: #67c23a; }
.fb-tag { margin-left: 4px; }
.time { color: #c0c4cc; font-size: 12px; }
.content { margin-top: 4px; white-space: pre-wrap; word-break: break-all; }
.cards { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.card { background: #fff; border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; }
.card-title { font-weight: 600; font-size: 13px; margin-bottom: 6px; }
.card-type { margin-left: 6px; }
.card-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 12px; }
.field { font-size: 12px; display: flex; gap: 6px; }
.field .label { color: #909399; flex-shrink: 0; }
.field .value { color: #303133; word-break: break-all; }
.card-btn { padding: 0; margin-top: 4px; font-size: 12px; }
</style>
