<template>
  <div class="user-grid">
    <!-- 左：身份卡 -->
    <div class="page-card">
      <div class="user-avatar">{{ initial }}</div>
      <div class="user-name">{{ member.nickname || member.phone || '会员' }}</div>
      <div class="user-phone">{{ member.phone || '' }}</div>

      <div class="user-stats">
        <div v-for="s in stats" :key="s.label" class="user-stat" @click="$router.push(s.to)">
          <div class="user-stat-num">{{ s.count }}</div>
          <div class="user-stat-label">{{ s.label }}</div>
        </div>
      </div>

      <div style="margin-top:20px;display:flex;flex-direction:column;gap:10px">
        <el-button size="small" @click="$router.push('/address')">收货地址管理</el-button>
        <el-button size="small" @click="$router.push('/coupon/my')">我的优惠券</el-button>
        <el-button size="small" @click="$router.push('/refund/list')">我的售后单</el-button>
        <!-- V4 P2-2：足迹页；V7 补收藏/积分/消息入口，评价入口沿用占位 -->
        <el-button size="small" @click="$router.push('/footprint')">我的足迹</el-button>
        <el-button size="small" @click="$router.push('/favorite')">我的收藏</el-button>
        <el-button size="small" @click="$router.push('/point')">我的积分</el-button>
        <el-button size="small" @click="$router.push('/notice')">
          消息中心
          <el-badge v-if="unreadCount > 0" :value="unreadCount" style="margin-left:6px" />
        </el-button>
        <el-button size="small" @click="$message.info('评价功能敬请期待')">我的评价</el-button>
        <el-button size="small" type="danger" plain @click="onLogout">退出登录</el-button>
      </div>
    </div>

    <!-- 右：资料与概览 -->
    <div>
      <div class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">基本资料</span></div>
        <el-form :model="profile" label-width="88px" size="small" style="max-width:460px">
          <el-form-item label="手机号">
            <el-input :value="member.phone" disabled />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model.trim="profile.nickname" maxlength="32" placeholder="设置一个昵称" />
          </el-form-item>
          <el-form-item label="性别">
            <el-radio-group v-model="profile.gender">
              <el-radio :label="0">保密</el-radio>
              <el-radio :label="1">男</el-radio>
              <el-radio :label="2">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item>
            <el-button type="danger" size="small" :loading="saving" @click="onSave">保存修改</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="page-card">
        <div class="page-head">
          <span class="page-title">最近订单</span>
          <router-link to="/order/list" style="color:#e1251b;font-size:13px">全部订单 ›</router-link>
        </div>
        <div v-for="o in recent" :key="o.id" class="co-row">
          <div class="co-row-mid">
            <div class="co-row-name">{{ firstGoodsName(o) }}</div>
            <div class="co-row-spec">订单号 {{ o.orderNo }} · {{ shortTime(o.createTime) }}</div>
          </div>
          <div class="co-row-right">
            <div class="money">¥{{ $price(o.payAmount) }}</div>
            <div style="margin-top:6px">{{ orderStatusText(o.status) }}</div>
          </div>
        </div>
        <el-empty v-if="!recent.length" description="还没有下过单" :image-size="80" />
      </div>
    </div>
  </div>
</template>

<script>
import { getProfile, updateProfile } from '@/api/auth'
import { getOrderPage } from '@/api/order'
import { getUnreadCount } from '@/api/notice'
import { orderStatusText, shortTime } from '@/utils/format'
import store from '@/store'

export default {
  name: 'User',
  data() {
    return {
      member: {},
      profile: { nickname: '', gender: 0 },
      counts: { 10: 0, 20: 0, 30: 0, 40: 0 },
      recent: [],
      saving: false,
      // V7：消息未读角标
      unreadCount: 0
    }
  },
  computed: {
    initial() {
      const name = this.member.nickname || this.member.phone || '会'
      return name.slice(0, 1).toUpperCase()
    },
    stats() {
      return [
        { label: '待付款', count: this.counts[10], to: '/order/list?status=10' },
        { label: '待发货', count: this.counts[20], to: '/order/list?status=20' },
        { label: '已发货', count: this.counts[30], to: '/order/list?status=30' },
        { label: '已完成', count: this.counts[40], to: '/order/list?status=40' }
      ]
    }
  },
  created() {
    this.load()
  },
  methods: {
    orderStatusText,
    shortTime,
    firstGoodsName(o) {
      const items = o.items || []
      if (!items.length) return '订单 ' + o.orderNo
      return items.length > 1 ? items[0].goodsName + ' 等 ' + items.length + ' 件' : items[0].goodsName
    },
    async load() {
      this.member = await getProfile()
      this.profile.nickname = this.member.nickname || ''
      this.profile.gender = Number(this.member.gender) || 0
      // 各状态订单数：pageSize=1 只取 total，四次并发
      const statuses = [10, 20, 30, 40]
      const pages = await Promise.all(
        statuses.map(s => getOrderPage({ status: s, pageNum: 1, pageSize: 1 }))
      )
      statuses.forEach((s, i) => {
        this.counts[s] = Number((pages[i] && pages[i].total) || 0)
      })
      const latest = await getOrderPage({ pageNum: 1, pageSize: 3 })
      this.recent = (latest && latest.records) || []
      // V7：未读消息数（失败静默，不影响页面）
      getUnreadCount().then(d => { this.unreadCount = (d && d.count) || 0 }).catch(() => {})
    },
    async onSave() {
      this.saving = true
      try {
        await updateProfile({ nickname: this.profile.nickname, gender: this.profile.gender })
        await this.load()
        // 头部昵称读的是 store 里的 member，同步刷新（用新值覆盖，别拿旧 profile）
        store.setSession(store.state.token, {
          ...this.member,
          nickname: this.profile.nickname,
          gender: this.profile.gender
        })
        this.$message.success('资料已更新')
      } finally {
        this.saving = false
      }
    },
    async onLogout() {
      await store.logout()
      this.$message.success('已退出登录')
      this.$router.push('/')
    }
  }
}
</script>
