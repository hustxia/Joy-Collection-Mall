<template>
  <div>
    <div class="order-tabs">
      <div
        v-for="t in tabs"
        :key="t.label"
        class="order-tab"
        :class="{ on: status === t.value }"
        @click="switchTab(t.value)"
      >{{ t.label }}</div>
    </div>

    <div v-loading="loading">
      <div v-for="o in orders" :key="o.id" class="order-card">
        <div class="order-card-head">
          <span>{{ shortTime(o.createTime) }}</span>
          <span>订单号 {{ o.orderNo }}</span>
          <span v-if="o.merchantName" class="shop-tag" @click.stop="$router.push('/merchant/' + o.merchantId)">
            <i class="el-icon-s-shop" />{{ o.merchantName }}
          </span>
          <span class="status" :style="{ color: statusColor(o.status) }">{{ orderStatusText(o.status) }}</span>
          <span v-if="o.refundStatus" class="rf-badge" @click.stop="$router.push('/refund/detail/' + o.refundId)">售后</span>
        </div>
        <div class="order-card-body">
          <div v-for="(it, i) in o.items" :key="i" class="co-row">
            <pc-image :src="it.skuImage" :img-style="{ width: '64px', height: '64px', objectFit: 'cover', borderRadius: '6px', display: 'block' }" />
            <div class="co-row-mid">
              <div class="co-row-name">{{ it.goodsName }}</div>
            </div>
            <div class="co-row-right">
              <div>¥{{ $price(it.price) }}</div>
              <div style="margin-top:6px">x{{ it.quantity }}</div>
            </div>
          </div>
        </div>
        <div class="order-card-foot">
          <span class="sum">
            共 {{ itemCount(o) }} 件，实付 <span class="money">¥{{ $price(o.payAmount) }}</span>
            <span v-if="Number(o.discountAmount) > 0" class="muted">（已优惠 ¥{{ $price(o.discountAmount) }}）</span>
          </span>
          <el-button size="small" @click="$router.push('/order/detail/' + o.id)">订单详情</el-button>
          <el-button v-if="Number(o.status) === 10" size="small" @click="onCancel(o)">取消订单</el-button>
          <el-button v-if="Number(o.status) === 10" size="small" type="danger" @click="goPay(o)">去支付</el-button>
          <el-button v-if="Number(o.status) === 30" size="small" type="danger" @click="onReceipt(o)">确认收货</el-button>
        </div>
      </div>

      <div v-if="!loading && !orders.length" class="page-card">
        <el-empty description="暂无相关订单">
          <el-button type="danger" size="small" @click="$router.push('/')">去逛逛</el-button>
        </el-empty>
      </div>
    </div>

    <div style="text-align:right;margin-top:14px">
      <el-button size="small" type="text" class="rf-link" @click="$router.push('/refund/list')">我的售后单 &gt;</el-button>
    </div>

    <div v-if="total > pageSize" style="text-align:center;margin-top:18px">
      <el-pagination
        background
        layout="prev, pager, next, total"
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script>
import { getOrderPage, cancelOrder, confirmReceipt } from '@/api/order'
import { orderStatusText, orderStatusType, shortTime } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'
import store from '@/store'

const STATUS_COLOR = { danger: '#f56c6c', warning: '#e6a23c', primary: '#409eff', success: '#67c23a', info: '#909399' }

export default {
  name: 'OrderList',
  components: { PcImage },
  data() {
    return {
      tabs: [
        { label: '全部', value: null },
        { label: '待付款', value: 10 },
        { label: '待发货', value: 20 },
        { label: '已发货', value: 30 },
        { label: '已完成', value: 40 }
      ],
      status: this.$route.query.status ? Number(this.$route.query.status) : null,
      orders: [],
      total: 0,
      pageNum: 1,
      pageSize: 5,
      loading: false
    }
  },
  created() {
    this.load()
  },
  methods: {
    orderStatusText,
    shortTime,
    statusColor(status) {
      return STATUS_COLOR[orderStatusType(status)] || '#909399'
    },
    itemCount(o) {
      return (o.items || []).reduce((sum, i) => sum + Number(i.quantity), 0)
    },
    switchTab(value) {
      this.status = value
      this.pageNum = 1
      this.load()
    },
    async load() {
      this.loading = true
      try {
        const res = await getOrderPage({
          status: this.status === null ? undefined : this.status,
          pageNum: this.pageNum,
          pageSize: this.pageSize
        })
        this.orders = (res && res.records) || []
        this.total = (res && Number(res.total)) || 0
      } finally {
        this.loading = false
      }
    },
    goPay(o) {
      this.$router.push({ path: '/pay/' + o.orderNo, query: { payAmount: o.payAmount } })
    },
    async onCancel(o) {
      await this.$confirm('取消后库存与优惠券会回补，确定取消该订单？', '取消订单', { type: 'warning' })
      await cancelOrder(o.id)
      this.$message.success('订单已取消')
      this.load()
      store.refreshCartCount()
    },
    async onReceipt(o) {
      await this.$confirm('确认已经收到商品？', '确认收货', { type: 'warning' })
      await confirmReceipt(o.id)
      this.$message.success('已确认收货，订单完成')
      this.load()
    }
  }
}
</script>

<style scoped>
.shop-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #8a6d3b;
  background: #fff8e6;
  border-radius: 10px;
  padding: 1px 8px;
  cursor: pointer;
}
.shop-tag:hover { color: #e1251b; background: #fff1f0; }
.rf-badge {
  font-size: 12px;  color: #e6a23c;
  border: 1px solid #e6a23c;
  border-radius: 3px;
  padding: 0 6px;
  cursor: pointer;
  line-height: 18px;
}
.rf-badge:hover { background: #fdf6ec; }
.rf-link { color: #606266; padding-right: 0; }
.rf-link:hover { color: #e1251b; }
</style>
