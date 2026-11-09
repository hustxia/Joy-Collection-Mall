<template>
  <div class="page">
    <van-nav-bar title="收银台" left-arrow @click-left="router.replace('/order/list')" />

    <div class="cash">
      <div class="text-gray cash-label">支付金额（元）</div>
      <div class="cash-amount">¥{{ fenToYuan(payAmount) }}</div>
      <div class="cash-no text-gray">
        订单号 {{ orderNo }}<span v-if="payNo"> · 支付单 {{ payNo }}</span>
      </div>
    </div>

    <van-cell-group inset class="block">
      <van-cell
        v-for="m in methods"
        :key="m.code"
        :title="m.name"
        :class="{ 'pw-off': !m.configured }"
        @click="select(m)"
      >
        <template #icon>
          <van-icon :name="m.icon" class="pw-icon" :color="m.configured ? '#4a6cf7' : '#c8c9cc'" />
        </template>
        <template #label>
          <span v-if="!m.configured">未配置商户参数</span>
        </template>
        <template #right-icon>
          <van-icon v-if="channel === m.code" name="success" color="#4a6cf7" />
        </template>
      </van-cell>
    </van-cell-group>

    <div class="pay-btn-wrap">
      <van-button
        block
        round
        color="#4a6cf7"
        :loading="paying"
        :disabled="!currentConfigured"
        @click="onPay"
      >确认支付</van-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showDialog, showToast } from 'vant'
import { prepay, getPayChannels, mockPay } from '@/api/pay'
import { fenToYuan } from '@/utils/format'

// 渠道清单：可用性由后端 /shop/pay/channels 决定，前端不硬编码「能不能付」
const METHODS = [
  { code: 'sandbox', name: '沙箱支付（秒到账）', icon: 'balance-pay' },
  { code: 'wxpay', name: '微信支付', icon: 'wechat-pay' },
  { code: 'alipay', name: '支付宝', icon: 'alipay' },
]

const route = useRoute()
const router = useRouter()
const orderNo = route.params.orderNo
const payAmount = ref(Number(route.query.payAmount) || 0)
const channel = ref('sandbox')
const availability = ref({})
const payNo = ref('')
const paying = ref(false)

const methods = computed(() => METHODS.map(m => ({ ...m, configured: availability.value[m.code] !== false })))
const currentConfigured = computed(() => availability.value[channel.value] !== false)

const loadChannels = async () => {
  try {
    availability.value = (await getPayChannels()) || {}
  } catch (e) {
    // 拿不到可用性时保守展示：沙箱可用，其余不可用
    availability.value = { sandbox: true, wxpay: false, alipay: false }
  }
}

const select = m => {
  if (!m.configured) return showToast(`${m.name} 未配置，暂不可用`)
  channel.value = m.code
}

const onPay = async () => {
  paying.value = true
  try {
    // 1) 预支付：服务端核价并落支付单（金额以服务端为准）
    const info = await prepay({ orderNo, channel: channel.value, tradeType: 'H5' })
    payNo.value = info.payNo
    payAmount.value = info.amount
    // 2) 沙箱渠道直接完成；真实渠道此处应唤起 SDK 后轮询支付结果
    const res = await mockPay(orderNo)
    await showDialog({
      title: res.replay ? '提示' : '支付成功',
      message: res.replay ? '该订单已支付过，无需重复支付' : '模拟支付成功，等待商家发货',
      confirmButtonColor: '#4a6cf7',
    })
    router.replace('/order/list')
  } finally {
    paying.value = false
  }
}

onMounted(loadChannels)
</script>

<style scoped>
.cash {
  padding: 36px 20px 8px;
  text-align: center;
}

.cash-label {
  font-size: 13px;
}

.cash-amount {
  font-size: 32px;
  font-weight: 700;
  color: #ee5340;
  margin-top: 6px;
}

.cash-no {
  font-size: 11px;
  margin-top: 8px;
}

.block {
  margin-top: 16px;
}

.pw-icon {
  font-size: 20px;
  margin-right: 10px;
  align-self: center;
}

.pw-off {
  opacity: 0.4;
}

.pay-btn-wrap {
  padding: 20px 16px;
}
</style>
