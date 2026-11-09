<template>
  <div class="page-card">
    <div class="page-head">
      <span class="page-title">我的优惠券</span>
      <router-link to="/coupon/center" style="color:#e1251b;font-size:13px">去领券中心 ›</router-link>
    </div>

    <div class="order-tabs" style="margin-bottom:18px">
      <div
        v-for="t in tabs"
        :key="t.label"
        class="order-tab"
        :class="{ on: status === t.value }"
        @click="switchTab(t.value)"
      >{{ t.label }}</div>
    </div>

    <div v-loading="loading" class="coupon-grid">
      <div v-for="c in list" :key="c.recordId" class="coupon-card" :class="{ used: Number(c.status) !== 1 }">
        <div class="coupon-left">
          <div class="coupon-amount"><small>¥</small>{{ $price(c.amount) }}</div>
          <div class="coupon-threshold">
            {{ Number(c.thresholdAmount) > 0 ? '满 ' + $price(c.thresholdAmount) + ' 可用' : '无门槛' }}
          </div>
        </div>
        <div class="coupon-right">
          <div>
            <div class="coupon-name">{{ c.name }}</div>
            <div class="coupon-time">有效期至 {{ (c.endTime || '').slice(0, 10) }}</div>
            <div class="coupon-time">
              <el-tag size="mini" :type="statusTagType(c)">{{ statusText(c) }}</el-tag>
            </div>
          </div>
          <div style="text-align:right">
            <el-button v-if="Number(c.status) === 1" size="mini" type="danger" plain @click="$router.push('/')">去使用</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && !list.length" description="还没有优惠券，去领券中心看看" />
  </div>
</template>

<script>
import { getMyCoupons } from '@/api/coupon'

export default {
  name: 'CouponMine',
  data() {
    return {
      tabs: [
        { label: '未使用', value: 1 },
        { label: '已使用', value: 2 },
        { label: '已过期', value: 3 },
        { label: '全部', value: null }
      ],
      status: 1,
      list: [],
      loading: false
    }
  },
  created() {
    this.load()
  },
  methods: {
    switchTab(value) {
      this.status = value
      this.load()
    },
    async load() {
      this.loading = true
      try {
        this.list = (await getMyCoupons(this.status === null ? undefined : this.status)) || []
      } finally {
        this.loading = false
      }
    },
    statusText(c) {
      if (Number(c.status) === 2) return '已使用'
      if (Number(c.status) === 3) return '已过期'
      // 已下单未支付：券被锁定
      return c.locked ? '已锁定（待支付）' : '未使用'
    },
    statusTagType(c) {
      if (Number(c.status) === 1) return c.locked ? 'warning' : 'danger'
      return 'info'
    }
  }
}
</script>
