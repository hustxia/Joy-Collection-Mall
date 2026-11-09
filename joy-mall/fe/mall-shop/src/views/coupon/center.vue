<template>
  <div class="page">
    <van-nav-bar title="领券中心" left-arrow @click-left="router.back()" />

    <div v-for="c in list" :key="c.id" class="coupon" :class="{ off: c.left <= 0 }">
      <div class="c-left">
        <div class="c-amt"><span class="symbol">¥</span>{{ fenToYuan(c.amount) }}</div>
        <div class="c-th">{{ Number(c.thresholdAmount) > 0 ? `满${fenToYuan(c.thresholdAmount)}可用` : '无门槛' }}</div>
      </div>
      <div class="c-mid">
        <div class="c-name">{{ c.name }}</div>
        <div class="c-time text-gray">{{ (c.endTime || '').slice(0, 10) }} 到期</div>
        <div class="c-time text-gray">剩余 {{ c.left }} 张</div>
      </div>
      <div class="c-right">
        <van-button
          size="small"
          round
          :color="c.left > 0 ? '#ee5340' : '#c8c9cc'"
          :disabled="c.left <= 0"
          :loading="receivingId === c.id"
          @click="onReceive(c)"
        >
          {{ c.left > 0 ? '领取' : '已抢光' }}
        </van-button>
      </div>
    </div>
    <van-empty v-if="!list.length" description="暂无可领优惠券" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getCouponCenter, receiveCoupon } from '@/api/coupon'
import { fenToYuan } from '@/utils/format'

const router = useRouter()
const list = ref([])
const receivingId = ref(null)

const load = async () => {
  list.value = await getCouponCenter()
}

// 限领/抢光等后端会报错，拦截器已 toast；成功后刷新剩余量
const onReceive = async c => {
  receivingId.value = c.id
  try {
    await receiveCoupon(c.id)
    showToast('领取成功')
    await load()
  } catch (e) {
    // 报错信息已由拦截器 toast
  } finally {
    receivingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.coupon {
  display: flex;
  margin: 10px 10px 0;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.coupon.off {
  opacity: 0.5;
}

.c-left {
  width: 96px;
  flex: none;
  background: linear-gradient(135deg, #ff7a59, #ee5340);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  padding: 14px 0;
}

.c-amt {
  font-size: 22px;
  font-weight: 700;
}

.c-amt .symbol {
  font-size: 13px;
}

.c-th {
  font-size: 10px;
  margin-top: 4px;
  opacity: 0.85;
}

.c-mid {
  flex: 1;
  padding: 12px;
  min-width: 0;
}

.c-name {
  font-size: 14px;
  font-weight: 600;
}

.c-time {
  font-size: 11px;
  margin-top: 6px;
}

.c-right {
  width: 72px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
