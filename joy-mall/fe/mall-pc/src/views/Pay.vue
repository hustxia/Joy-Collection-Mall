<template>
  <div class="cash-card">
    <div class="page-title" style="text-align:center;margin-bottom:6px">收银台</div>
    <div class="muted">支付金额（元）</div>
    <div class="cash-amount">¥{{ fenToYuan(payAmount) }}</div>
    <div class="cash-no">
      订单号 {{ orderNo }}
      <span v-if="payNo"> · 支付单 {{ payNo }}</span>
      <span v-else> · 待生成支付单</span>
    </div>

    <div class="cash-methods">
      <div
        v-for="m in methods"
        :key="m.code"
        class="cash-method"
        :class="{ on: channel === m.code, off: !m.configured }"
        @click="select(m)"
      >
        <i :class="m.icon" class="cash-method-icon" :style="{ color: m.configured ? '#e1251b' : '#909399' }" />
        <div style="flex:1">
          <div class="cash-method-title">
            {{ m.name }}
            <span v-if="!m.configured" class="muted" style="font-size:12px">未配置商户参数</span>
          </div>
          <div class="cash-method-desc">{{ m.desc }}</div>
        </div>
        <i v-if="channel === m.code" class="el-icon-success" style="color:#e1251b;font-size:18px" />
      </div>
    </div>

    <el-button
      type="danger"
      style="width:100%;height:44px;margin-top:12px"
      :loading="paying"
      :disabled="!currentConfigured"
      @click="onPay"
    >
      确认支付 ¥{{ fenToYuan(payAmount) }}
    </el-button>
    <div class="muted" style="margin-top:14px">
      支付遇到问题？<router-link to="/order/list" style="color:#e1251b">回到我的订单</router-link>
    </div>
  </div>
</template>

<script>
import { prepay, getPayChannels, mockPay } from '@/api/pay'
import { fenToYuan } from '@/utils/format'

// 渠道清单：可用性由后端 /pay/channels 决定，前端不硬编码「能不能付」
const METHODS = [
  { code: 'sandbox', name: '沙箱支付（秒到账）', desc: '演示渠道，走真实支付单与回调链路', icon: 'el-icon-wallet' },
  { code: 'wxpay', name: '微信支付', desc: '需配置商户号与证书后才能使用', icon: 'el-icon-chat-dot-round' },
  { code: 'alipay', name: '支付宝', desc: '需配置应用私钥与公钥后才能使用', icon: 'el-icon-money' }
]

export default {
  name: 'Pay',
  data() {
    return {
      orderNo: this.$route.params.orderNo,
      payAmount: Number(this.$route.query.payAmount) || 0,
      channel: 'sandbox',
      availability: {},
      payNo: '',
      paying: false
    }
  },
  computed: {
    methods() {
      return METHODS.map(m => ({ ...m, configured: this.availability[m.code] !== false }))
    },
    currentConfigured() {
      return this.availability[this.channel] !== false
    }
  },
  created() {
    this.loadChannels()
  },
  methods: {
    fenToYuan,
    async loadChannels() {
      try {
        this.availability = (await getPayChannels()) || {}
      } catch (e) {
        // 拿不到可用性时按「沙箱可用、其余不可用」保守展示
        this.availability = { sandbox: true, wxpay: false, alipay: false }
      }
    },
    select(m) {
      if (!m.configured) {
        this.$message.warning(`${m.name} 未配置商户参数，暂不可用`)
        return
      }
      this.channel = m.code
    },
    async onPay() {
      this.paying = true
      try {
        // 1) 预支付：服务端核价并落支付单（金额以服务端为准，覆盖 URL 传参）
        const info = await prepay({ orderNo: this.orderNo, channel: this.channel, tradeType: 'NATIVE' })
        this.payNo = info.payNo
        this.payAmount = info.amount
        // 2) 沙箱渠道直接完成支付（内部走同一条回调链路）；真实渠道此处应轮询支付结果
        const res = await mockPay(this.orderNo)
        if (res.replay) {
          this.$message.info('该订单已支付过，无需重复支付')
        } else {
          await this.$alert('模拟支付成功，等待商家发货', '支付成功', {
            confirmButtonText: '查看订单',
            type: 'success'
          }).catch(() => {})
        }
        this.$router.replace('/order/list')
      } finally {
        this.paying = false
      }
    }
  }
}
</script>
