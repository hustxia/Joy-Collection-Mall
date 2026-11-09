<template>
  <div class="page-wrap">
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">我的积分</span>
        <span class="pt-balance">当前积分 <em>{{ balance }}</em></span>
      </div>

      <div v-loading="loading">
        <div v-for="it in list" :key="it.id" class="pt-row">
          <div class="pt-mid">
            <div class="pt-title">{{ typeText(it.changeType) }}</div>
            <div class="pt-sub">{{ it.remark || it.createTime }}</div>
          </div>
          <div class="pt-right">
            <div class="pt-num" :class="{ minus: it.points < 0 }">{{ it.points > 0 ? '+' + it.points : it.points }}</div>
            <div class="pt-after">余额 {{ it.balanceAfter }}</div>
          </div>
        </div>
        <el-empty v-if="!loading && !list.length" description="还没有积分记录" :image-size="90" />
      </div>

      <div v-if="total > query.pageSize" style="text-align:right;margin-top:12px">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          v-model:current-page="query.pageNum"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script>
import { getPointBalance, getPointLogs } from '@/api/point'

// 变动类型：1消费返 2签到 3下单抵扣 4退款扣回
const TYPE_TEXT = { 1: '消费返积分', 2: '签到奖励', 3: '下单抵扣', 4: '退款扣回' }

export default {
  name: 'Point',
  data() {
    return {
      balance: 0,
      loading: false,
      list: [],
      total: 0,
      query: { pageNum: 1, pageSize: 10 }
    }
  },
  created() {
    this.loadBalance()
    this.load()
  },
  methods: {
    typeText(t) {
      return TYPE_TEXT[t] || '积分变动'
    },
    async loadBalance() {
      try {
        const data = await getPointBalance()
        this.balance = (data && data.points) || 0
      } catch (e) {
        this.balance = 0
      }
    },
    async load() {
      this.loading = true
      try {
        const data = await getPointLogs(this.query)
        this.list = (data && data.list) || []
        this.total = (data && data.total) || 0
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.pt-balance {
  font-size: 13px;
  color: #909399;
}
.pt-balance em {
  font-style: normal;
  font-size: 20px;
  font-weight: 700;
  color: #e1251b;
}
.pt-row {
  display: flex;
  align-items: center;
  padding: 14px 6px;
  border-bottom: 1px solid #f0f0f0;
}
.pt-row:last-child {
  border-bottom: none;
}
.pt-mid {
  flex: 1;
  min-width: 0;
}
.pt-title {
  font-size: 14px;
  color: #303133;
}
.pt-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
.pt-right {
  text-align: right;
}
.pt-num {
  font-size: 17px;
  font-weight: 600;
  color: #e1251b;
}
.pt-num.minus {
  color: #67c23a;
}
.pt-after {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
