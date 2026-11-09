<template>
  <div>
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">领券中心</span>
        <span class="muted">
          共 {{ coupons.length }} 张可领
          <router-link to="/coupon/my" style="color:#e1251b;margin-left:10px">我的优惠券 ›</router-link>
        </span>
      </div>

      <div v-loading="loading" class="coupon-grid">
        <div v-for="c in coupons" :key="c.id" class="coupon-card">
          <div class="coupon-left">
            <div class="coupon-amount"><small>¥</small>{{ $price(c.amount) }}</div>
            <div class="coupon-threshold">
              {{ Number(c.thresholdAmount) > 0 ? '满 ' + $price(c.thresholdAmount) + ' 可用' : '无门槛' }}
            </div>
          </div>
          <div class="coupon-right">
            <div>
              <div class="coupon-name">{{ c.name }}</div>
              <div class="coupon-time">{{ (c.startTime || '').slice(0, 10) }} 至 {{ (c.endTime || '').slice(0, 10) }}</div>
              <div class="coupon-time">每人限领 {{ c.perLimit }} 张 · 已领 {{ c.received }} 张</div>
            </div>
            <div style="display:flex;align-items:center;justify-content:space-between">
              <span class="muted">剩余 {{ c.left }} 张</span>
              <el-button
                size="mini"
                type="danger"
                plain
                :disabled="reachedLimit(c)"
                @click="onReceive(c)"
              >{{ reachedLimit(c) ? '已领过' : '立即领取' }}</el-button>
            </div>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && !coupons.length" description="当前没有可领取的优惠券" />
    </div>
  </div>
</template>

<script>
import { getCouponCenter, receiveCoupon } from '@/api/coupon'
import store from '@/store'

export default {
  name: 'CouponCenter',
  data() {
    return { coupons: [], loading: false }
  },
  created() {
    // 领券接口需要会员身份；未登录也允许浏览券面
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        this.coupons = (await getCouponCenter()) || []
      } finally {
        this.loading = false
      }
    },
    reachedLimit(c) {
      return Number(c.received) >= Number(c.perLimit)
    },
    async onReceive(c) {
      if (!store.isLogin) {
        this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } })
        return
      }
      if (this.reachedLimit(c)) {
        this.$message.info('该券已达每人限领数量')
        return
      }
      await receiveCoupon(c.id)
      this.$message.success('领取成功，下单时可用')
      this.load()
    }
  }
}
</script>
