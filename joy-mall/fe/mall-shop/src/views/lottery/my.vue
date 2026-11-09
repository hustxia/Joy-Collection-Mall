<template>
  <div class="page">
    <van-nav-bar title="我的抽奖记录" left-arrow fixed placeholder @click-left="router.back()" />

    <van-cell-group inset class="ltm-list">
      <div v-for="r in records" :key="r.id" class="ltm-item">
        <div class="ltm-icon" :class="{ win: r.isWin === 1 }">{{ r.isWin === 1 ? prizeIcon(r.prizeType) : '谢' }}</div>
        <div class="ltm-info">
          <div class="ltm-name van-ellipsis">{{ r.activityName || '抽奖活动' }}</div>
          <div class="ltm-prize" :class="{ lose: r.isWin !== 1 }">{{ r.prizeName }}</div>
          <div class="ltm-time">{{ r.createTime }}</div>
        </div>
        <van-tag :type="r.isWin === 1 ? 'success' : 'default'">{{ r.isWin === 1 ? '已中奖' : '未中奖' }}</van-tag>
      </div>
      <van-empty v-if="!loading && !records.length" description="还没有抽奖记录" />
    </van-cell-group>

    <van-loading v-if="loading" class="ltm-loading" vertical>加载中...</van-loading>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getMyLotteryRecords } from '@/api/lottery'

const records = ref([])
const loading = ref(true)

const prizeIcon = t => ({ 0: '积分', 1: '券', 2: '实物' }[t] || '奖')

onMounted(async () => {
  try {
    records.value = (await getMyLotteryRecords()) || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}
.ltm-list {
  margin-top: 12px;
}
.ltm-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #f2f3f5;
}
.ltm-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #f2f3f5;
  color: #969799;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ltm-icon.win {
  background: #fff1f0;
  color: #e1251b;
}
.ltm-info {
  flex: 1;
  min-width: 0;
}
.ltm-name {
  font-size: 13px;
  color: #646566;
}
.ltm-prize {
  font-size: 14px;
  color: #323233;
  margin: 2px 0;
}
.ltm-prize.lose {
  color: #969799;
}
.ltm-time {
  font-size: 11px;
  color: #c8c9cc;
}
.ltm-loading {
  margin-top: 40px;
}
</style>
