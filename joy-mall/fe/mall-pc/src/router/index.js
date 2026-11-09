import { createRouter, createWebHistory } from 'vue-router'
import store from '@/store'

import Home from '@/views/Home.vue'
import GoodsList from '@/views/GoodsList.vue'
import GoodsDetail from '@/views/GoodsDetail.vue'
import MerchantShop from '@/views/MerchantShop.vue'
import Login from '@/views/Login.vue'
import Cart from '@/views/Cart.vue'
import OrderConfirm from '@/views/OrderConfirm.vue'
import Pay from '@/views/Pay.vue'
import OrderList from '@/views/OrderList.vue'
import OrderDetail from '@/views/OrderDetail.vue'
import CouponCenter from '@/views/CouponCenter.vue'
import CouponMine from '@/views/CouponMine.vue'
import Address from '@/views/Address.vue'
import User from '@/views/User.vue'
import RefundApply from '@/views/RefundApply.vue'
import RefundList from '@/views/RefundList.vue'
import RefundDetail from '@/views/RefundDetail.vue'
import Footprint from '@/views/Footprint.vue'
import Favorite from '@/views/Favorite.vue'
import Point from '@/views/Point.vue'
import Notice from '@/views/Notice.vue'
import SeckillList from '@/views/SeckillList.vue'
import SeckillDetail from '@/views/SeckillDetail.vue'
import GroupList from '@/views/GroupList.vue'
import GroupDetail from '@/views/GroupDetail.vue'
import GroupMine from '@/views/GroupMine.vue'
import LotteryList from '@/views/LotteryList.vue'
import LotteryPlay from '@/views/LotteryPlay.vue'
import Distribution from '@/views/Distribution.vue'

const routes = [
  { path: '/', name: 'home', component: Home },
  { path: '/list', name: 'list', component: GoodsList },
  { path: '/goods/:id', name: 'goodsDetail', component: GoodsDetail },
  // V10：店铺主页（店内商品由 /shop/goods?merchantId= 提供）
  { path: '/merchant/:id', name: 'merchantShop', component: MerchantShop },
  { path: '/login', name: 'login', component: Login },
  { path: '/cart', name: 'cart', component: Cart, meta: { requiresAuth: true } },
  { path: '/order/confirm', name: 'orderConfirm', component: OrderConfirm, meta: { requiresAuth: true } },
  { path: '/pay/:orderNo', name: 'pay', component: Pay, meta: { requiresAuth: true } },
  { path: '/order/list', name: 'orderList', component: OrderList, meta: { requiresAuth: true } },
  { path: '/order/detail/:id', name: 'orderDetail', component: OrderDetail, meta: { requiresAuth: true } },
  { path: '/coupon/center', name: 'couponCenter', component: CouponCenter },
  { path: '/coupon/my', name: 'couponMine', component: CouponMine, meta: { requiresAuth: true } },
  { path: '/address', name: 'address', component: Address, meta: { requiresAuth: true } },
  { path: '/user', name: 'user', component: User, meta: { requiresAuth: true } },
  { path: '/refund/apply', name: 'refundApply', component: RefundApply, meta: { requiresAuth: true } },
  { path: '/refund/list', name: 'refundList', component: RefundList, meta: { requiresAuth: true } },
  { path: '/refund/detail/:id', name: 'refundDetail', component: RefundDetail, meta: { requiresAuth: true } },
  { path: '/footprint', name: 'footprint', component: Footprint, meta: { requiresAuth: true } },
  // V7：收藏/积分/消息
  { path: '/favorite', name: 'favorite', component: Favorite, meta: { requiresAuth: true } },
  { path: '/point', name: 'point', component: Point, meta: { requiresAuth: true } },
  { path: '/notice', name: 'notice', component: Notice, meta: { requiresAuth: true } },
  // V11：营销中心（秒杀/拼团/抽奖）+ 分销中心
  { path: '/seckill', name: 'seckillList', component: SeckillList },
  { path: '/seckill/:id', name: 'seckillDetail', component: SeckillDetail, meta: { requiresAuth: true } },
  { path: '/group', name: 'groupList', component: GroupList },
  { path: '/group/my', name: 'groupMine', component: GroupMine, meta: { requiresAuth: true } },
  { path: '/group/:id', name: 'groupDetail', component: GroupDetail, meta: { requiresAuth: true } },
  { path: '/lottery', name: 'lotteryList', component: LotteryList },
  { path: '/lottery/:id', name: 'lotteryPlay', component: LotteryPlay, meta: { requiresAuth: true } },
  { path: '/distribution', name: 'distribution', component: Distribution, meta: { requiresAuth: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ left: 0, top: 0 })
})

// 未登录访问需要会员身份的页面 → 先去登录，登录后回跳
router.beforeEach((to, from, next) => {
  if (to.meta && to.meta.requiresAuth && !store.isLogin) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

export default router
