import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '@/layout/index.vue'
import genRoutes from './gen-routes'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/index.vue'),
        meta: { title: '首页' }
      }
    ]
  },
  {
    path: '/merchant/login',
    name: 'MerchantLogin',
    component: () => import('@/views/merchant/login.vue'),
    meta: { title: '商家登录' }
  },
  {
    // V11：商家后台为独立布局与独立令牌，不挂平台 Layout（否则会渲染平台菜单与权限）
    path: '/merchant',
    component: () => import('@/views/merchant/layout.vue'),
    redirect: '/merchant/overview',
    children: [
      { path: 'overview', name: 'MerchantOverview', component: () => import('@/views/merchant/overview.vue'), meta: { title: '经营概览' } },
      { path: 'goods', name: 'MerchantGoods', component: () => import('@/views/merchant/goods.vue'), meta: { title: '商品管理' } },
      { path: 'order', name: 'MerchantOrder', component: () => import('@/views/merchant/order.vue'), meta: { title: '订单与发货' } },
      { path: 'refund', name: 'MerchantRefund', component: () => import('@/views/merchant/refund.vue'), meta: { title: '售后审核' } },
      { path: 'fund', name: 'MerchantFund', component: () => import('@/views/merchant/fund.vue'), meta: { title: '结算与提现' } }
    ]
  },
  ...genRoutes
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 未登录一律踢到 /login；401 时 request.js 会清 token 并跳这里
// V11：商家端（/merchant/**）只认 merchantToken，两套会话互不顶替
router.beforeEach((to, from, next) => {
  if (to.path.indexOf('/merchant') === 0) {
    const merchantToken = localStorage.getItem('merchantToken')
    if (to.path !== '/merchant/login' && !merchantToken) {
      next('/merchant/login')
    } else {
      next()
    }
    return
  }
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
