import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/home', name: 'home', component: () => import('@/views/home/index.vue'), meta: { title: '首页' } },
  { path: '/category', name: 'category', component: () => import('@/views/category/index.vue'), meta: { title: '分类' } },
  { path: '/goods/list', name: 'goodsList', component: () => import('@/views/goods/list.vue'), meta: { title: '商品列表' } },
  { path: '/goods/:id', name: 'goodsDetail', component: () => import('@/views/goods/detail.vue'), meta: { title: '商品详情' } },
  // V10：店铺主页（店内商品走 /shop/goods?merchantId= 过滤）
  { path: '/merchant/:id', name: 'merchantShop', component: () => import('@/views/merchant/index.vue'), meta: { title: '店铺' } },
  { path: '/login', name: 'login', component: () => import('@/views/login/index.vue'), meta: { title: '登录' } },
  // 以下需登录
  { path: '/cart', name: 'cart', component: () => import('@/views/cart/index.vue'), meta: { title: '购物车', requiresAuth: true } },
  { path: '/user', name: 'user', component: () => import('@/views/user/index.vue'), meta: { title: '我的', requiresAuth: true } },
  { path: '/user/profile', name: 'userProfile', component: () => import('@/views/user/profile.vue'), meta: { title: '编辑资料', requiresAuth: true } },
  { path: '/order/confirm', name: 'orderConfirm', component: () => import('@/views/order/confirm.vue'), meta: { title: '确认订单', requiresAuth: true } },
  { path: '/order/list', name: 'orderList', component: () => import('@/views/order/list.vue'), meta: { title: '我的订单', requiresAuth: true } },
  { path: '/order/:id', name: 'orderDetail', component: () => import('@/views/order/detail.vue'), meta: { title: '订单详情', requiresAuth: true } },
  { path: '/pay/:orderNo', name: 'pay', component: () => import('@/views/pay/index.vue'), meta: { title: '收银台', requiresAuth: true } },
  { path: '/address', name: 'address', component: () => import('@/views/address/list.vue'), meta: { title: '收货地址', requiresAuth: true } },
  { path: '/address/edit', name: 'addressEdit', component: () => import('@/views/address/edit.vue'), meta: { title: '编辑地址', requiresAuth: true } },
  { path: '/coupon/center', name: 'couponCenter', component: () => import('@/views/coupon/center.vue'), meta: { title: '领券中心', requiresAuth: true } },
  { path: '/coupon/my', name: 'couponMy', component: () => import('@/views/coupon/my.vue'), meta: { title: '我的优惠券', requiresAuth: true } },
  { path: '/refund/apply', name: 'refundApply', component: () => import('@/views/refund/apply.vue'), meta: { title: '申请售后', requiresAuth: true } },
  { path: '/refund/list', name: 'refundList', component: () => import('@/views/refund/list.vue'), meta: { title: '我的售后', requiresAuth: true } },
  { path: '/refund/:id', name: 'refundDetail', component: () => import('@/views/refund/detail.vue'), meta: { title: '售后详情', requiresAuth: true } },
  { path: '/footprint', name: 'footprint', component: () => import('@/views/footprint/index.vue'), meta: { title: '我的足迹', requiresAuth: true } },
  // V7：收藏/积分/签到/消息/评价
  { path: '/favorite', name: 'favorite', component: () => import('@/views/favorite/index.vue'), meta: { title: '我的收藏', requiresAuth: true } },
  { path: '/point', name: 'point', component: () => import('@/views/point/index.vue'), meta: { title: '我的积分', requiresAuth: true } },
  { path: '/signin', name: 'signin', component: () => import('@/views/signin/index.vue'), meta: { title: '每日签到', requiresAuth: true } },
  { path: '/notice', name: 'notice', component: () => import('@/views/notice/index.vue'), meta: { title: '消息中心', requiresAuth: true } },
  { path: '/review/my', name: 'reviewMy', component: () => import('@/views/review/my.vue'), meta: { title: '我的评价', requiresAuth: true } },
  // V15：营销与分销（秒杀/详情、抽奖/详情、拼团/详情匿名可看；动作接口未登录时后端 401 拦截器跳登录）
  { path: '/seckill', name: 'seckillList', component: () => import('@/views/seckill/list.vue'), meta: { title: '限时秒杀' } },
  { path: '/seckill/:id', name: 'seckillDetail', component: () => import('@/views/seckill/detail.vue'), meta: { title: '秒杀详情' } },
  { path: '/group', name: 'groupList', component: () => import('@/views/group/list.vue'), meta: { title: '拼团' } },
  { path: '/group/:id', name: 'groupDetail', component: () => import('@/views/group/detail.vue'), meta: { title: '拼团详情' } },
  { path: '/group/my', name: 'groupMy', component: () => import('@/views/group/my.vue'), meta: { title: '我的拼团', requiresAuth: true } },
  { path: '/lottery', name: 'lotteryList', component: () => import('@/views/lottery/list.vue'), meta: { title: '幸运抽奖' } },
  { path: '/lottery/:id', name: 'lotteryDetail', component: () => import('@/views/lottery/detail.vue'), meta: { title: '抽奖' } },
  { path: '/lottery/my', name: 'lotteryMy', component: () => import('@/views/lottery/my.vue'), meta: { title: '我的抽奖记录', requiresAuth: true } },
  { path: '/distribution', name: 'distribution', component: () => import('@/views/distribution/index.vue'), meta: { title: '分销中心', requiresAuth: true } },
  // V16：在线 AI 客服（全屏聊天页；未登录时页面内提示，不加 requiresAuth）
  { path: '/service-chat', name: 'serviceChat', component: () => import('@/views/service/chat.vue'), meta: { title: '在线客服' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// 登录守卫：需登录页面无 token 时跳 /login 并携带来源
router.beforeEach(to => {
  const token = localStorage.getItem('mall-shop-token')
  if (to.meta.requiresAuth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.title) {
    document.title = `${to.meta.title} - mall 商城`
  }
  return true
})

export default router
