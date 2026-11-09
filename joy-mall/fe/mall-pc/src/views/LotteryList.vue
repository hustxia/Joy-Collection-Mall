<template>
  <div class="page-wrap">
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">幸运抽奖</span>
        <span class="lt-tip">每日限量抽奖，好礼带回家</span>
      </div>

      <div v-loading="loading">
        <div v-if="activities.length" class="lt-grid">
          <div v-for="a in activities" :key="a.id" class="lt-card">
            <div class="lt-name">{{ a.name }}</div>
            <div class="lt-meta">
              <span>每日限 {{ a.dailyLimit }} 次</span>
              <span v-if="a.consumePoints > 0">每次消耗 {{ a.consumePoints }} 积分</span>
              <span v-else>免费参与</span>
            </div>
            <div class="lt-meta">{{ shortTime(a.endTime) }} 截止</div>
            <div class="lt-prizes">
              <el-tag v-for="p in a.prizes" :key="p.id" size="mini" effect="plain" class="lt-prize-tag">
                {{ prizeTypeText(p.prizeType) }}·{{ p.name }}
              </el-tag>
            </div>
            <el-button type="danger" size="small" round class="lt-btn" @click="$router.push(`/lottery/${a.id}`)">
              马上抽奖
            </el-button>
          </div>
        </div>
        <el-empty v-else-if="loaded" description="暂无进行中的抽奖活动" />
      </div>
    </div>
  </div>
</template>

<script>
import { getLotteryActivities } from '@/api/marketing'
import { shortTime } from '@/utils/format'

// 奖品类型：0积分 1优惠券 2实物 3谢谢参与
const PRIZE_TYPE = { 0: '积分', 1: '优惠券', 2: '实物', 3: '谢谢参与' }

export default {
  name: 'LotteryList',
  data() {
    return { activities: [], loading: false, loaded: false }
  },
  created() {
    this.load()
  },
  methods: {
    shortTime,
    prizeTypeText(t) {
      return PRIZE_TYPE[t] || '奖品'
    },
    async load() {
      this.loading = true
      try {
        this.activities = (await getLotteryActivities()) || []
      } catch (e) {
        this.activities = []
      } finally {
        this.loading = false
        this.loaded = true
      }
    }
  }
}
</script>

<style scoped>
.lt-tip {
  font-size: 13px;
  color: #909399;
}
.lt-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.lt-card {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  flex-direction: column;
}
.lt-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.lt-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 12px;
}
.lt-prizes {
  margin-top: 12px;
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.lt-prize-tag {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}
.lt-btn {
  margin-top: 14px;
  width: 100%;
}
</style>
