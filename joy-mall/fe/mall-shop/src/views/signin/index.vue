<template>
  <div class="page">
    <van-nav-bar title="每日签到" left-arrow fixed placeholder @click-left="router.back()" />

    <!-- 签到操作区 -->
    <div class="sg-head">
      <div class="sg-tip">连续签到天数越多，送得越多（每日上限 7 分）</div>
      <van-button
        round
        block
        color="#fff"
        class="sg-btn"
        :loading="signing"
        :disabled="todaySigned"
        @click="onSign"
      >
        <span :class="{ signed: todaySigned }">{{ todaySigned ? '今日已签到' : '立即签到' }}</span>
      </van-button>
    </div>

    <!-- 月历 -->
    <div class="sg-card">
      <div class="sg-month">
        <van-icon name="arrow-left" @click="shiftMonth(-1)" />
        <span>{{ month }}</span>
        <van-icon name="arrow" @click="shiftMonth(1)" />
      </div>
      <div class="sg-grid sg-week">
        <span v-for="w in ['一','二','三','四','五','六','日']" :key="w">{{ w }}</span>
      </div>
      <div class="sg-grid">
        <span v-for="n in firstOffset" :key="'e' + n" />
        <span
          v-for="d in daysInMonth"
          :key="d"
          class="sg-day"
          :class="{ on: signedSet.has(dayStr(d)), today: d === todayDay && isCurrentMonth }"
        >
          {{ d }}
          <van-icon v-if="signedSet.has(dayStr(d))" name="success" class="sg-ick" />
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { signIn, getSignCalendar } from '@/api/signin'

const router = useRouter()
const now = new Date()
const month = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const signedSet = ref(new Set())
const todaySigned = ref(false)
const signing = ref(false)

const yearMonth = computed(() => {
  const [yy, mm] = month.value.split('-').map(Number)
  return { yy, mm }
})
const daysInMonth = computed(() => new Date(yearMonth.value.yy, yearMonth.value.mm, 0).getDate())
// 周一开头：getDay() 周日=0 → 偏移 (day+6)%7
const firstOffset = computed(() => (new Date(yearMonth.value.yy, yearMonth.value.mm - 1, 1).getDay() + 6) % 7)
const isCurrentMonth = computed(() => month.value === `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const todayDay = now.getDate()

const dayStr = d => `${month.value}-${String(d).padStart(2, '0')}`

const load = async () => {
  const data = await getSignCalendar(month.value)
  signedSet.value = new Set(data.days || [])
  todaySigned.value = !!data.todaySigned
}

const shiftMonth = delta => {
  const { yy, mm } = yearMonth.value
  const d = new Date(yy, mm - 1 + delta, 1)
  month.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  load()
}

const onSign = async () => {
  signing.value = true
  try {
    const data = await signIn()
    showToast(`签到成功，连签 ${data.continuousDays} 天 +${data.points} 分`)
    todaySigned.value = true
    await load()
  } finally {
    signing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}
.sg-head {
  background: linear-gradient(135deg, #4a6cf7, #7a9bff);
  padding: 20px 16px 26px;
}
.sg-tip {
  color: #fff;
  font-size: 12px;
  opacity: 0.9;
  text-align: center;
  margin-bottom: 14px;
}
.sg-btn.signed,
.sg-btn .signed {
  color: #4a6cf7;
}
.sg-btn {
  color: #4a6cf7;
  font-weight: 600;
}
.sg-card {
  background: #fff;
  margin: -14px 10px 0;
  border-radius: 8px;
  padding: 14px 12px 18px;
  position: relative;
}
.sg-month {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  padding: 0 8px 10px;
  color: #323233;
}
.sg-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
}
.sg-week {
  font-size: 12px;
  color: #969799;
  padding-bottom: 6px;
}
.sg-day {
  position: relative;
  height: 44px;
  line-height: 44px;
  font-size: 13px;
  color: #323233;
}
.sg-day.on {
  color: #4a6cf7;
  font-weight: 600;
}
.sg-day.today {
  color: #e1251b;
}
.sg-ick {
  position: absolute;
  right: 6px;
  top: 6px;
  font-size: 10px;
  color: #07c160;
}
</style>
