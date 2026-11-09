<template>
  <div class="page">
    <van-nav-bar title="我的订单" left-arrow @click-left="router.back()" />

    <!-- 状态 tab：全部/待付款/待发货/待收货/已完成 -->
    <van-tabs v-model:active="active" color="#4a6cf7" @change="reload">
      <van-tab v-for="t in tabs" :key="t.label" :title="t.label" />
    </van-tabs>

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
      <div v-for="o in orders" :key="o.id" class="o-card" @click="router.push(`/order/${o.id}`)">
        <div class="o-head">
          <span class="text-gray">{{ o.orderNo }}</span>
          <span class="o-status">
            <span v-if="o.refundStatus" class="refund-badge" :class="`rb-${o.refundStatus}`">{{ refundBadgeText(o) }}</span>
            {{ orderStatusText(o.status) }}
          </span>
        </div>
        <div v-if="o.merchantName" class="o-shop">
          <van-icon name="shop-o" />
          <span class="van-ellipsis">{{ o.merchantName }}</span>
        </div>
        <div v-for="(it, i) in o.items" :key="i" class="o-item">
          <van-image :src="thumb(it.skuImage, 80)" fit="cover" class="oi-img" />
          <div class="oi-mid">
            <div class="van-ellipsis">{{ it.goodsName }}</div>
            <div class="text-gray oi-sub">
              <span class="price">¥{{ fenToYuan(it.price) }}</span> × {{ it.quantity }}
            </div>
          </div>
        </div>
        <div class="o-amount">
          实付 <span class="price">¥{{ fenToYuan(o.payAmount) }}</span>
        </div>
        <div class="o-foot" @click.stop>
          <!-- 待付款：取消 + 去支付 -->
          <template v-if="o.status === 10">
            <van-button size="small" round plain :loading="actingId === o.id" @click="onCancel(o)">取消</van-button>
            <van-button size="small" round color="#4a6cf7" @click="router.push(`/pay/${o.orderNo}?payAmount=${o.payAmount}`)">去支付</van-button>
          </template>
          <!-- 已发货：确认收货 -->
          <van-button
            v-else-if="o.status === 30"
            size="small"
            round
            color="#4a6cf7"
            :loading="actingId === o.id"
            @click="onConfirmReceipt(o)"
          >
            确认收货
          </van-button>
        </div>
      </div>
      <van-empty v-if="finished && !orders.length" description="暂无相关订单" />
    </van-list>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getOrderPage, cancelOrder, confirmReceipt } from '@/api/order'
import { fenToYuan, orderStatusText, refundStatusText, thumb } from '@/utils/format'

// 角标文案：售后中（1/2）显示「售后中」，4 显示「已退款」，其余显示具体状态
const refundBadgeText = o => (o.refundStatus === 1 || o.refundStatus === 2 ? '售后中' : refundStatusText(o.refundStatus))

const route = useRoute()
const router = useRouter()

const tabs = [
  { label: '全部', status: undefined },
  { label: '待付款', status: 10 },
  { label: '待发货', status: 20 },
  { label: '待收货', status: 30 },
  { label: '已完成', status: 40 },
]

// 支持从「我的」带 status 进来定位 tab
const initIdx = tabs.findIndex(t => String(t.status) === String(route.query.status))
const active = ref(initIdx >= 0 ? initIdx : 0)

const orders = ref([])
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
const actingId = ref(null)

const onLoad = async () => {
  try {
    const page = await getOrderPage({ status: tabs[active.value].status, pageNum: pageNum.value, pageSize })
    orders.value.push(...(page.records || []))
    pageNum.value += 1
    if (!page.records || orders.value.length >= page.total) {
      finished.value = true
    }
  } finally {
    loading.value = false
  }
}

const reload = () => {
  orders.value = []
  pageNum.value = 1
  finished.value = false
  loading.value = true
  onLoad()
}

const onCancel = async o => {
  await showConfirmDialog({ title: '取消订单', message: '确定取消该订单吗？', confirmButtonColor: '#4a6cf7' })
  actingId.value = o.id
  try {
    await cancelOrder(o.id)
    showToast('已取消')
    reload()
  } finally {
    actingId.value = null
  }
}

const onConfirmReceipt = async o => {
  await showConfirmDialog({ title: '确认收货', message: '确认已收到商品吗？', confirmButtonColor: '#4a6cf7' })
  actingId.value = o.id
  try {
    await confirmReceipt(o.id)
    showToast('已确认收货')
    reload()
  } finally {
    actingId.value = null
  }
}

onMounted(() => {
  // van-list 会自动触发首次 onLoad
})
</script>

<style scoped>
.o-card {
  background: #fff;
  margin: 8px 10px 0;
  border-radius: 8px;
  padding: 12px;
}

.o-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 10px;
}

.o-status {
  color: #ee9f2e;
}

.o-shop {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  margin-bottom: 8px;
  padding: 1px 8px;
  border-radius: 8px;
  background: #fff8e6;
  color: #8a6d3b;
  font-size: 11px;
}

.refund-badge {
  display: inline-block;
  font-size: 10px;
  padding: 0 5px;
  border-radius: 3px;
  margin-right: 5px;
  background: #fff3e6;
  color: #ff976a;
}

.refund-badge.rb-4 {
  background: #e8f8ef;
  color: #07c160;
}

.o-item {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
}

.oi-img {
  width: 56px;
  height: 56px;
  border-radius: 6px;
  overflow: hidden;
  flex: none;
}

.oi-mid {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}

.oi-sub {
  margin-top: 6px;
  font-size: 12px;
}

.o-amount {
  text-align: right;
  font-size: 12px;
  color: #646566;
}

.o-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
</style>
