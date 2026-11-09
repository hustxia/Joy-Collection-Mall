<template>
  <div class="page">
    <van-nav-bar title="我的积分" left-arrow fixed placeholder @click-left="router.back()" />

    <!-- 余额头 -->
    <div class="pt-head">
      <div class="pt-num">{{ balance }}</div>
      <div class="pt-label">当前积分</div>
      <van-button round size="small" plain color="#fff" class="pt-sign-btn" @click="router.push('/signin')">
        签到赚积分
      </van-button>
    </div>

    <div class="pt-sec">积分明细</div>
    <van-empty v-if="!loading && !list.length" description="还没有积分记录" />

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
      <div v-for="it in list" :key="it.id" class="pt-item">
        <div class="pt-mid">
          <div class="pt-title">{{ typeText(it.changeType) }}</div>
          <div class="pt-time">{{ it.remark || it.createTime }}</div>
        </div>
        <div class="pt-right">
          <div class="pt-points" :class="{ minus: it.points < 0 }">{{ it.points > 0 ? '+' + it.points : it.points }}</div>
          <div class="pt-bal">余额 {{ it.balanceAfter }}</div>
        </div>
      </div>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getPointBalance, getPointLogs } from '@/api/point'

const router = useRouter()
const balance = ref(0)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const finished = ref(false)

// 变动类型：1消费返 2签到 3下单抵扣 4退款扣回
const TYPE_TEXT = { 1: '消费返积分', 2: '签到奖励', 3: '下单抵扣', 4: '退款扣回' }
const typeText = t => TYPE_TEXT[t] || '积分变动'

const onLoad = async () => {
  try {
    const data = await getPointLogs({ pageNum: page.value, pageSize: size })
    const rows = data.list || []
    total.value = data.total || 0
    list.value = page.value === 1 ? rows : list.value.concat(rows)
    page.value += 1
    finished.value = list.value.length >= total.value
  } catch (e) {
    finished.value = true
  } finally {
    loading.value = false
  }
}

getPointBalance().then(d => { balance.value = d.points || 0 }).catch(() => {})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}
.pt-head {
  background: linear-gradient(135deg, #4a6cf7, #7a9bff);
  color: #fff;
  padding: 24px 16px 28px;
  text-align: center;
  position: relative;
}
.pt-num {
  font-size: 34px;
  font-weight: 700;
}
.pt-label {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 4px;
}
.pt-sign-btn {
  position: absolute;
  right: 16px;
  top: 20px;
  border: 1px solid #fff;
  background: transparent;
}
.pt-sec {
  padding: 12px 14px 6px;
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}
.pt-item {
  display: flex;
  align-items: center;
  padding: 12px 14px;
  background: #fff;
  border-bottom: 1px solid #f2f3f5;
}
.pt-mid {
  flex: 1;
  min-width: 0;
}
.pt-title {
  font-size: 14px;
  color: #323233;
}
.pt-time {
  margin-top: 4px;
  font-size: 11px;
  color: #969799;
}
.pt-right {
  text-align: right;
}
.pt-points {
  font-size: 16px;
  font-weight: 600;
  color: #e1251b;
}
.pt-points.minus {
  color: #07c160;
}
.pt-bal {
  margin-top: 4px;
  font-size: 11px;
  color: #969799;
}
</style>
