<template>
  <div class="page">
    <van-nav-bar title="秒杀详情" left-arrow fixed placeholder @click-left="router.back()" />

    <template v-if="activity">
      <!-- 商品头图 -->
      <van-image v-if="activity.mainImage" :src="activity.mainImage" width="100%" height="280" fit="cover" />

      <!-- 价格条 -->
      <div class="skd-price-bar">
        <div>
          <span class="skd-price">￥{{ fenToYuan(activity.seckillPrice) }}</span>
          <span class="skd-origin">￥{{ fenToYuan(activity.originPrice) }}</span>
        </div>
        <div class="skd-cd">
          <span>{{ activity.onSale ? '距结束' : '未开始' }}</span>
          <van-count-down v-if="activity.onSale" :time="remainMs(activity.endTime)" format="HH:mm:ss" class="skd-cd-num" />
        </div>
      </div>

      <!-- 商品信息 -->
      <div class="skd-card">
        <div class="skd-name">{{ activity.goodsName }}</div>
        <van-progress
          :percentage="progress"
          stroke-width="8"
          color="#e1251b"
          track-color="#ffece8"
          :show-pivot="false"
        />
        <div class="skd-stock-row">
          <span>总量 {{ activity.totalStock }} 件</span>
          <span>剩余 {{ activity.availableStock }} 件</span>
          <span>限购 {{ activity.perLimit }} 件</span>
          <span v-if="activity.myCount > 0">我已购 {{ activity.myCount }} 件</span>
        </div>
        <div class="skd-time">活动时间：{{ activity.startTime }} ~ {{ activity.endTime }}</div>
      </div>

      <div class="skd-card skd-tip">
        <van-icon name="info-o" color="#4a6cf7" />
        抢购成功后自动生成待付款订单，请前往「我的订单」完成支付。
      </div>

      <!-- 底部抢购按钮 -->
      <div class="skd-bar">
        <van-button
          round
          block
          type="primary"
          color="#e1251b"
          :disabled="!activity.onSale || activity.availableStock <= 0"
          :loading="buying"
          @click="showPicker = true"
        >
          {{ !activity.onSale ? '未开始' : activity.availableStock <= 0 ? '已抢光' : '立即抢购' }}
        </van-button>
      </div>
    </template>
    <van-loading v-else-if="loading" class="skd-loading" vertical>加载中...</van-loading>
    <van-empty v-else-if="loadError" description="活动不存在" />

    <!-- 地址选择 -->
    <AddressPicker v-model:show="showPicker" @select="onBuy" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showDialog } from 'vant'
import { getSeckillDetail, buySeckill } from '@/api/seckill'
import { fenToYuan, parseTime } from '@/utils/format'
import AddressPicker from '@/components/AddressPicker.vue'

const route = useRoute()
const router = useRouter()
// id 为字符串，禁止 Number() 转换
const id = route.params.id

const activity = ref(null)
const loading = ref(true)
const loadError = ref(false)
const buying = ref(false)
const showPicker = ref(false)

const remainMs = endTime => {
  const d = parseTime(endTime)
  const t = (d ? d.getTime() : 0) - Date.now()
  return t > 0 ? t : 0
}
const progress = computed(() => {
  const a = activity.value
  if (!a || !a.totalStock) return 0
  const sold = Math.max(0, (a.totalStock || 0) - (a.availableStock || 0))
  return Math.min(100, Math.round((sold / a.totalStock) * 100))
})

const load = async () => {
  try {
    activity.value = await getSeckillDetail(id)
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const onBuy = async address => {
  buying.value = true
  try {
    // 失败时 request 拦截器已 toast 后端 msg（如「已达限购数量」）
    const data = await buySeckill(id, { addressId: address.id })
    showDialog({
      title: '抢购成功',
      message: `订单号 ${data.orderNo}\n应付 ￥${fenToYuan(data.payAmount)}`,
      theme: 'round-button',
      confirmButtonColor: '#e1251b',
    }).then(() => {
      router.push(`/pay/${data.orderNo}`)
    })
  } catch (e) {
    // 保持静默：toast 已在拦截器统一弹出
  } finally {
    buying.value = false
    showPicker.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 76px;
}
.skd-price-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #e1251b, #ff6b61);
  padding: 12px 16px;
}
.skd-price {
  color: #fff;
  font-size: 24px;
  font-weight: 700;
}
.skd-origin {
  color: rgba(255, 255, 255, 0.8);
  font-size: 13px;
  text-decoration: line-through;
  margin-left: 8px;
}
.skd-cd {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #fff;
  font-size: 12px;
}
.skd-cd-num {
  color: #fff;
  font-weight: 600;
}
.skd-card {
  background: #fff;
  margin: 10px;
  border-radius: 8px;
  padding: 14px;
}
.skd-name {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 12px;
}
.skd-stock-row {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: #969799;
  margin-top: 8px;
}
.skd-time {
  font-size: 12px;
  color: #969799;
  margin-top: 8px;
}
.skd-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #646566;
}
.skd-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);
}
.skd-loading {
  margin-top: 60px;
}
</style>
