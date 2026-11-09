<template>
  <div class="page">
    <van-nav-bar title="我的优惠券" left-arrow @click-left="router.back()" />

    <van-tabs v-model:active="active" color="#4a6cf7" @change="load">
      <van-tab title="未使用" />
      <van-tab title="已使用" />
      <van-tab title="已过期" />
    </van-tabs>

    <div v-for="c in list" :key="c.recordId" class="coupon" :class="{ off: active !== 0 }">
      <div class="c-left">
        <div class="c-amt"><span class="symbol">¥</span>{{ fenToYuan(c.amount) }}</div>
        <div class="c-th">{{ Number(c.thresholdAmount) > 0 ? `满${fenToYuan(c.thresholdAmount)}可用` : '无门槛' }}</div>
      </div>
      <div class="c-mid">
        <div class="c-name">{{ c.name }}</div>
        <div class="c-time text-gray">{{ (c.endTime || '').slice(0, 10) }} 到期</div>
        <div v-if="c.locked" class="c-time text-gray">订单锁定中</div>
      </div>
      <div class="c-right">
        <van-button v-if="active === 0" size="small" round color="#ee5340" @click="router.push('/home')">去使用</van-button>
        <span v-else class="text-gray c-state">{{ active === 1 ? '已使用' : '已过期' }}</span>
      </div>
    </div>
    <van-empty v-if="!list.length" description="暂无优惠券" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyCoupons } from '@/api/coupon'
import { fenToYuan } from '@/utils/format'

const router = useRouter()
// tab 顺序对应后端 status：1 未用 / 2 已用 / 3 过期
const active = ref(0)
const list = ref([])

const load = async () => {
  list.value = await getMyCoupons(active.value + 1)
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

.c-state {
  font-size: 12px;
}
</style>
