<template>
  <div class="page page--tab">
    <!-- 头部：登录后头像昵称，未登录显示登录按钮 -->
    <div class="me-head">
      <template v-if="user.isLogin && user.member">
        <van-image round :src="user.member.avatar" class="avatar">
          <template #error><van-icon name="user-circle-o" size="56" color="#fff" /></template>
        </van-image>
        <div class="me-info">
          <div class="me-name">{{ user.member.nickname || '未设置昵称' }}</div>
          <div class="me-phone">{{ user.member.phone }}</div>
        </div>
      </template>
      <template v-else>
        <van-icon name="user-circle-o" size="56" color="#fff" />
        <van-button round size="small" plain color="#fff" class="login-btn" @click="router.push('/login')">点击登录</van-button>
      </template>
    </div>

    <!-- 我的订单入口 -->
    <div class="me-card">
      <van-cell title="我的订单" is-link value="全部" @click="goOrders()" />
      <div class="me-order-row">
        <div v-for="m in orderMenus" :key="m.label" class="mo" @click="goOrders(m.status)">
          <van-icon :name="m.icon" size="22" color="#4a6cf7" />
          <div class="mo-label">{{ m.label }}</div>
        </div>
      </div>
    </div>

    <!-- 功能菜单 -->
    <van-cell-group inset class="me-menu">
      <van-cell title="消息中心" is-link icon="chat-o" @click="router.push('/notice')">
        <template #right-icon>
          <van-badge v-if="unreadCount > 0" :content="unreadCount" max="99" class="me-badge" />
          <van-icon name="arrow" class="van-cell__right-icon" />
        </template>
      </van-cell>
      <van-cell title="每日签到" is-link icon="calendar-o" @click="router.push('/signin')" />
      <van-cell title="我的积分" is-link icon="points" @click="router.push('/point')" />
      <van-cell title="我的收藏" is-link icon="star-o" @click="router.push('/favorite')" />
      <van-cell title="收货地址" is-link icon="location-o" @click="router.push('/address')" />
      <van-cell title="我的售后" is-link icon="replay" @click="router.push('/refund/list')" />
      <van-cell title="我的足迹" is-link icon="underway-o" @click="router.push('/footprint')" />
      <van-cell title="我的评价" is-link icon="comment-o" @click="router.push('/review/my')" />
      <van-cell title="我的优惠券" is-link icon="coupon-o" @click="router.push('/coupon/my')" />
      <van-cell title="领券中心" is-link icon="gift-o" @click="router.push('/coupon/center')" />
      <van-cell title="限时秒杀" is-link icon="clock-o" @click="router.push('/seckill')" />
      <van-cell title="拼团" is-link icon="friends-o" @click="router.push('/group')" />
      <van-cell title="幸运抽奖" is-link icon="gift-card-o" @click="router.push('/lottery')" />
      <van-cell title="分销中心" is-link icon="medal-o" @click="router.push('/distribution')" />
      <van-cell title="编辑资料" is-link icon="edit" @click="router.push('/user/profile')" />
      <van-cell v-if="user.isLogin" title="退出登录" is-link icon="logout" @click="onLogout" />
    </van-cell-group>

    <TabBar />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { useUserStore } from '@/stores/user'
import { getUnreadCount } from '@/api/notice'
import TabBar from '@/components/TabBar.vue'

const router = useRouter()
const user = useUserStore()
const unreadCount = ref(0)

const orderMenus = [
  { label: '待付款', icon: 'balance-o', status: 10 },
  { label: '待发货', icon: 'send-gift-o', status: 20 },
  { label: '待收货', icon: 'logistics', status: 30 },
  { label: '已完成', icon: 'checked', status: 40 },
  { label: '全部', icon: 'orders-o', status: undefined },
]

const goOrders = status =>
  router.push({ path: '/order/list', query: status === undefined ? {} : { status } })

const onLogout = async () => {
  await showConfirmDialog({ title: '退出登录', message: '确定退出当前账号吗？', confirmButtonColor: '#4a6cf7' })
  await user.logout()
  router.replace('/login')
}

onMounted(() => {
  // 刷新最新资料（失败时静默，保留本地缓存）
  if (user.isLogin) {
    user.fetchProfile().catch(() => {})
    // V7 消息未读角标（失败静默，不影响页面）
    getUnreadCount().then(d => { unreadCount.value = d.count || 0 }).catch(() => {})
  }
})
</script>

<style scoped>
.me-head {
  background: linear-gradient(135deg, #4a6cf7, #7a9bff);
  padding: 24px 16px 34px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar {
  width: 56px;
  height: 56px;
}

.me-info {
  color: #fff;
}

.me-name {
  font-size: 17px;
  font-weight: 600;
}

.me-phone {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 4px;
}

.login-btn {
  border: 1px solid #fff;
  background: transparent;
}

.me-card {
  background: #fff;
  margin: -20px 10px 0;
  border-radius: 8px;
  position: relative;
  overflow: hidden;
}

.me-order-row {
  display: flex;
  padding: 4px 0 14px;
}

.mo {
  flex: 1;
  text-align: center;
}

.mo-label {
  font-size: 11px;
  color: #646566;
  margin-top: 4px;
}

.me-menu {
  margin-top: 8px;
}

.me-badge {
  margin-right: 4px;
}
</style>
