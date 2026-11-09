<template>
  <div v-loading="loading">
    <el-row :gutter="14">
      <el-col v-for="c in cards" :key="c.key" :span="6">
        <el-card shadow="never" class="stat">
          <p class="label">{{ c.label }}</p>
          <p class="value">{{ c.value }}</p>
          <p class="hint">{{ c.hint }}</p>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14" class="mt">
      <el-col :span="12">
        <el-card shadow="never">
<template #header>          <div>待办事项</div></template>
          <ul class="todo">
            <li>
              <span>待发货订单</span>
              <el-badge :value="data.pendingShipCount || 0" :max="99" type="danger">
                <el-button size="mini" type="primary" plain @click="$router.push('/merchant/order')">去发货</el-button>
              </el-badge>
            </li>
            <li>
              <span>待审核售后</span>
              <el-badge :value="data.pendingRefundCount || 0" :max="99" type="warning">
                <el-button size="mini" type="warning" plain @click="$router.push('/merchant/refund')">去审核</el-button>
              </el-badge>
            </li>
            <li>
              <span>待了解结算单</span>
              <el-badge :value="data.settlementPendingCount || 0" :max="99" type="info">
                <el-button size="mini" plain @click="$router.push('/merchant/fund')">去查看</el-button>
              </el-badge>
            </li>
          </ul>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
<template #header>          <div>资金概况</div></template>
          <ul class="fund">
            <li><span>可用余额</span><b class="red">¥{{ fenToYuan(data.accountBalance) }}</b></li>
            <li><span>累计已结算</span><b>¥{{ fenToYuan(data.settledTotal) }}</b></li>
            <li><span>平台结算比例</span><b>{{ data.settleRate != null ? data.settleRate + '%' : '—' }}</b></li>
          </ul>
          <p class="note">结算比例由平台配置；提现申请即时冻结余额，平台审核拒绝后自动恢复。</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { overview } from '@/api/merchant'
import { fenToYuan } from '@/utils/format'

export default {
  name: 'MerchantOverview',
  data() {
    return { data: {}, loading: false }
  },
  computed: {
    cards() {
      const d = this.data
      return [
        { key: 'goods', label: '在售商品', value: d.onSaleCount || 0, hint: `全部商品 ${d.goodsCount || 0} 个` },
        { key: 'today', label: '今日订单', value: d.todayOrderCount || 0, hint: `今日成交 ¥${fenToYuan(d.todayPayAmount)}` },
        { key: 'month', label: '本月成交', value: '¥' + fenToYuan(d.monthPayAmount), hint: '按支付时间统计' },
        { key: 'ship', label: '待发货', value: d.pendingShipCount || 0, hint: '含部分发货订单' }
      ]
    }
  },
  created() {
    this.load()
  },
  methods: {
    fenToYuan,
    async load() {
      this.loading = true
      try {
        const res = await overview()
        this.data = res.data || {}
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.stat { text-align: left; }
.stat .label { margin: 0; color: #909399; font-size: 13px; }
.stat .value { margin: 6px 0 2px; font-size: 26px; font-weight: 600; color: #303133; }
.stat .hint { margin: 0; color: #c0c4cc; font-size: 12px; }
.mt { margin-top: 14px; }
.todo, .fund { list-style: none; margin: 0; padding: 0; }
.todo li, .fund li { display: flex; align-items: center; justify-content: space-between; padding: 9px 0; border-bottom: 1px dashed #ebeef5; color: #606266; font-size: 13px; }
.todo li:last-child, .fund li:last-child { border-bottom: none; }
.fund .red { color: #e1251b; font-size: 16px; }
.note { color: #a8abb2; font-size: 12px; margin: 8px 0 0; }
</style>
