<template>
  <div class="page">
    <van-nav-bar title="幸运抽奖" left-arrow fixed placeholder @click-left="router.back()" />

    <div v-for="a in activities" :key="a.id" class="lt-card" @click="router.push(`/lottery/${a.id}`)">
      <div class="lt-head">
        <span class="lt-name">{{ a.name }}</span>
        <van-tag type="danger">进行中</van-tag>
      </div>
      <div class="lt-meta">
        <span>每日限 {{ a.dailyLimit }} 次</span>
        <span>{{ a.consumePoints > 0 ? `每次消耗 ${a.consumePoints} 积分` : '免费参与' }}</span>
      </div>
      <div class="lt-prizes">
        <div v-for="p in a.prizes" :key="p.id" class="lt-prize">
          <span class="lt-prize-icon">{{ prizeIcon(p.prizeType) }}</span>
          <span class="lt-prize-name van-ellipsis">{{ p.name }}</span>
        </div>
      </div>
      <van-button round block type="primary" color="#e1251b" size="small" class="lt-go">去抽奖</van-button>
    </div>

    <van-empty v-if="!loading && !activities.length" description="暂无进行中的抽奖活动" />
    <van-loading v-if="loading" class="lt-loading" vertical>加载中...</van-loading>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getLotteryActivities } from '@/api/lottery'

const router = useRouter()
const activities = ref([])
const loading = ref(true)

const prizeIcon = t => ({ 0: '积分', 1: '优惠券', 2: '实物', 3: '谢谢' }[t] || '奖')

onMounted(async () => {
  try {
    activities.value = (await getLotteryActivities()) || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}
.lt-card {
  position: relative;
  background: #fff;
  margin: 12px;
  border-radius: 10px;
  padding: 14px 14px 52px;
}
.lt-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.lt-name {
  font-size: 16px;
  font-weight: 600;
}
.lt-meta {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: #969799;
  margin: 6px 0 10px;
}
.lt-prizes {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.lt-prize {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  background: #fff7f6;
  border-radius: 6px;
  padding: 8px 4px;
  min-width: 0;
}
.lt-prize-icon {
  font-size: 16px;
  font-weight: 700;
  color: #e1251b;
}
.lt-prize-name {
  font-size: 11px;
  color: #646566;
  max-width: 100%;
}
.lt-go {
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 12px;
}
.lt-loading {
  margin-top: 40px;
}
</style>
