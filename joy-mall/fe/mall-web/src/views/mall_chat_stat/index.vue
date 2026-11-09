<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div class="flex">
        <span>AI 客服用量看板</span>
        <el-radio-group v-model="days" size="mini" @change="load">
          <el-radio-button :label="7">近 7 天</el-radio-button>
          <el-radio-button :label="14">近 14 天</el-radio-button>
          <el-radio-button :label="30">近 30 天</el-radio-button>
        </el-radio-group>
      </div></template>

      <el-row :gutter="12" v-loading="loading">
        <el-col v-for="c in statCards" :key="c.label" :sm="8" :md="4">
          <div class="stat-card">
            <div class="stat-label">{{ c.label }}</div>
            <div class="stat-value">{{ c.value }}</div>
            <div v-if="c.sub" class="stat-sub">{{ c.sub }}</div>
          </div>
        </el-col>
      </el-row>

      <div class="section-title">近 {{ days }} 天趋势（柱高按消息数）</div>
      <div v-loading="loading" class="chart">
        <div v-if="!daily.length" class="chart-empty">暂无趋势数据</div>
        <div v-for="d in daily" :key="d.date" class="chart-col">
          <div class="chart-bars">
            <div class="bar session" :style="{ height: barHeight(d.sessionCount) }" :title="`会话 ${d.sessionCount}`" />
            <div class="bar message" :style="{ height: barHeight(d.messageCount) }" :title="`消息 ${d.messageCount}`" />
          </div>
          <div class="chart-num">{{ d.messageCount }}</div>
          <div class="chart-date">{{ shortDate(d.date) }}</div>
        </div>
      </div>
      <div class="legend">
        <span><i class="dot session" /> 会话数</span>
        <span><i class="dot message" /> 消息数</span>
      </div>
    </el-card>

    <el-card shadow="never" class="mt">
      <template #header><div>
        <span>FAQ 命中 TOP5</span>
        <span class="hint">命中次数最高的知识条目，可用于优化答案与同义问法</span>
      </div></template>
      <el-table v-loading="loading" :data="topFaq" size="small" stripe empty-text="暂无命中数据">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="question" label="标准问题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column prop="hitCount" label="命中次数" width="100" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="mini" :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { chatStatOverview } from '@/api/mall_chat'

export default {
  name: 'MallChatStat',
  data() {
    return {
      days: 7,
      loading: false,
      stat: {},
      daily: [],
      topFaq: []
    }
  },
  computed: {
    statCards() {
      const s = this.stat
      return [
        { label: '会话数', value: s.sessionCount || 0 },
        { label: '消息数', value: s.messageCount || 0 },
        { label: 'AI 回复数', value: s.aiReplyCount || 0 },
        { label: '转人工数', value: s.transferCount || 0 },
        { label: '自助解决率', value: s.selfSolveRate != null ? `${s.selfSolveRate}%` : '—', sub: `转人工率 ${s.transferRate != null ? s.transferRate + '%' : '—'}` },
        { label: 'Token 消耗', value: s.tokens || 0, sub: `预估成本 ¥${s.costEstimate != null ? s.costEstimate : '—'}` }
      ]
    },
    maxMessage() {
      return Math.max(1, ...this.daily.map(d => Number(d.messageCount) || 0))
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await chatStatOverview({ days: this.days })
        const d = res.data || {}
        this.stat = d
        this.daily = d.daily || []
        this.topFaq = (d.topFaq || []).slice(0, 5)
      } finally {
        this.loading = false
      }
    },
    barHeight(v) {
      const n = Number(v) || 0
      return `${Math.round((n / this.maxMessage) * 120) + 2}px`
    },
    shortDate(date) {
      return typeof date === 'string' && date.length >= 10 ? date.slice(5) : date
    }
  }
}
</script>

<style scoped>
.flex { display: flex; justify-content: space-between; align-items: center; }
.mt { margin-top: 14px; }
.hint { color: #a8abb2; font-size: 12px; margin-left: 10px; }
.stat-card { background: #fafbfc; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; text-align: center; margin-bottom: 12px; }
.stat-label { color: #909399; font-size: 12px; }
.stat-value { font-size: 22px; font-weight: 600; color: #303133; margin-top: 4px; }
.stat-sub { color: #c0c4cc; font-size: 12px; margin-top: 2px; }
.section-title { font-size: 14px; font-weight: 600; color: #303133; margin: 12px 0 8px; }
.chart { display: flex; align-items: flex-end; gap: 6px; min-height: 180px; padding: 0 4px; overflow-x: auto; }
.chart-empty { width: 100%; text-align: center; color: #c0c4cc; padding: 40px 0; }
.chart-col { flex: 1; min-width: 34px; display: flex; flex-direction: column; align-items: center; }
.chart-bars { display: flex; align-items: flex-end; gap: 3px; height: 130px; }
.bar { width: 10px; border-radius: 2px 2px 0 0; }
.bar.session { background: #a0cfff; }
.bar.message { background: #409eff; }
.chart-num { font-size: 11px; color: #909399; margin-top: 2px; }
.chart-date { font-size: 11px; color: #c0c4cc; margin-top: 2px; white-space: nowrap; }
.legend { margin-top: 10px; font-size: 12px; color: #909399; display: flex; gap: 16px; }
.dot { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 4px; }
.dot.session { background: #a0cfff; }
.dot.message { background: #409eff; }
</style>
