<template>
  <div class="page">
    <van-nav-bar title="限时秒杀" left-arrow fixed placeholder @click-left="router.back()" />

    <div v-if="!loading" class="sk-group-title">
      <van-icon name="clock-o" color="#e1251b" />
      进行中
      <span class="sk-sub">共 {{ activities.length }} 场</span>
    </div>

    <div v-for="a in activities" :key="a.id" class="sk-card" @click="router.push(`/seckill/${a.id}`)">
      <van-image :src="thumb(a.mainImage, 300)" width="96" height="96" radius="6" fit="cover">
        <template #error><div class="sk-img-ph">图</div></template>
      </van-image>
      <div class="sk-info">
        <div class="sk-name van-multi-ellipsis--l2">{{ a.goodsName || '商品名加载中' }}</div>
        <div class="sk-countdown">
          <span v-if="a.onSale">距结束</span>
          <van-count-down :time="remainMs(a.endTime)" format="HH:mm:ss" class="sk-cd-num" />
        </div>
        <div class="sk-price-row">
          <span class="sk-price">￥{{ fenToYuan(a.seckillPrice) }}</span>
          <span class="sk-origin">￥{{ fenToYuan(a.originPrice) }}</span>
        </div>
        <van-progress
          :percentage="progress(a)"
          stroke-width="6"
          color="#e1251b"
          track-color="#ffece8"
          :show-pivot="false"
        />
        <div class="sk-stock">{{ sold(a) > 0 ? `已抢 ${sold(a)} 件` : '即将开抢' }} · 限购 {{ a.perLimit }} 件</div>
      </div>
      <van-button round type="primary" color="#e1251b" size="small" class="sk-buy">去抢购</van-button>
    </div>

    <!-- 即将开始（V15）：scope=upcoming，未开抢不可下单 -->
    <template v-if="!loading && upcoming.length">
      <div class="sk-group-title sk-upcoming-title">
        <van-icon name="underway-o" color="#4a6cf7" />
        即将开始
        <span class="sk-sub">共 {{ upcoming.length }} 场</span>
      </div>
      <div v-for="a in upcoming" :key="a.id" class="sk-card" @click="router.push(`/seckill/${a.id}`)">
        <van-image :src="thumb(a.mainImage, 300)" width="96" height="96" radius="6" fit="cover">
          <template #error><div class="sk-img-ph">图</div></template>
        </van-image>
        <div class="sk-info">
          <div class="sk-name van-multi-ellipsis--l2">{{ a.goodsName || '商品名加载中' }}</div>
          <div class="sk-countdown">
            <span>开抢</span>
            <span class="sk-start">{{ (a.startTime || '').slice(0, 16) }}</span>
          </div>
          <div class="sk-price-row">
            <span class="sk-price">￥{{ fenToYuan(a.seckillPrice) }}</span>
            <span class="sk-origin">￥{{ fenToYuan(a.originPrice) }}</span>
          </div>
          <div class="sk-stock">库存 {{ a.availableStock }} 件 · 限购 {{ a.perLimit }} 件</div>
        </div>
        <van-button round plain color="#e1251b" size="small" class="sk-buy" disabled>未开始</van-button>
      </div>
    </template>

    <van-empty v-if="!loading && !activities.length && !upcoming.length" description="暂无秒杀活动" />
    <van-loading v-if="loading" class="sk-loading" vertical>加载中...</van-loading>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getSeckillActivities } from '@/api/seckill'
import { fenToYuan, thumb, parseTime } from '@/utils/format'

const router = useRouter()
const activities = ref([])
const upcoming = ref([])
const loading = ref(true)

// 时间戳 tick 一次即可驱动所有倒计时（van-count-down 自行走秒）
const load = async () => {
  try {
    // 进行中 + 即将开始两组并行拉取；upcoming 需后端 scope 参数（旧版服务忽略该参数时返回空）
    const [ongoing, future] = await Promise.all([
      getSeckillActivities(),
      getSeckillActivities('upcoming').catch(() => []),
    ])
    activities.value = ongoing || []
    upcoming.value = (future || []).filter(a => !a.onSale)
  } finally {
    loading.value = false
  }
}

const remainMs = endTime => {
  const d = parseTime(endTime)
  const t = (d ? d.getTime() : 0) - Date.now()
  return t > 0 ? t : 0
}
const sold = a => Math.max(0, (a.totalStock || 0) - (a.availableStock || 0))
const progress = a => {
  const total = a.totalStock || 0
  return total > 0 ? Math.round((sold(a) / total) * 100) : 0
}

let timer
onMounted(async () => {
  await load()
  // 定时刷新剩余库存/活动状态
  timer = setInterval(load, 30000)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}
.sk-group-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 16px;
  font-weight: 600;
  padding: 14px 16px 6px;
}
.sk-sub {
  font-size: 12px;
  font-weight: 400;
  color: #969799;
}
.sk-upcoming-title {
  margin-top: 8px;
}
.sk-start {
  color: #4a6cf7;
  font-weight: 600;
}
.sk-card {
  position: relative;
  display: flex;
  gap: 10px;
  background: #fff;
  margin: 10px;
  border-radius: 8px;
  padding: 12px;
}
.sk-img-ph {
  width: 96px;
  height: 96px;
  line-height: 96px;
  text-align: center;
  background: #f2f3f5;
  color: #c8c9cc;
}
.sk-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-right: 60px;
}
.sk-name {
  font-size: 14px;
  color: #323233;
}
.sk-countdown {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #969799;
}
.sk-cd-num {
  color: #e1251b;
  font-weight: 600;
}
.sk-price-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.sk-price {
  color: #e1251b;
  font-size: 18px;
  font-weight: 700;
}
.sk-origin {
  color: #969799;
  font-size: 12px;
  text-decoration: line-through;
}
.sk-stock {
  font-size: 11px;
  color: #969799;
}
.sk-buy {
  position: absolute;
  right: 12px;
  bottom: 12px;
  padding: 0 14px;
}
.sk-loading {
  margin-top: 40px;
}
</style>
