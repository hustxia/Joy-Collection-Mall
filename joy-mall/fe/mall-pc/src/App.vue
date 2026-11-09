<template>
  <div id="app">
    <div class="shop-top">
      <!-- 顶部工具条 -->
      <div class="top-strip">
        <div class="top-strip-inner">
          <div class="strip-left">
            <span class="strip-logo">悦集商城</span>
            <span class="strip-slogan">正品保障 · 极速配送 · 7 天无理由退换</span>
          </div>
          <div class="strip-right">
            <a class="strip-link strip-link--hot" @click="go('/seckill')">限时秒杀</a>
            <a class="strip-link strip-link--hot" @click="go('/group')">超值拼团</a>
            <a class="strip-link strip-link--hot" @click="go('/lottery')">幸运抽奖</a>
            <span class="strip-sep"></span>
            <a class="strip-link" @click="go('/order/list')">我的订单</a>
            <a class="strip-link" @click="go('/coupon/center')">领券中心</a>
            <a class="strip-link" @click="go('/distribution')">分销中心</a>
            <span class="strip-sep"></span>
            <el-dropdown v-if="isLogin" trigger="hover" @command="onCommand">
              <span class="strip-user">{{ nickname }}<i class="el-icon-arrow-down"></i></span>
              <template #dropdown><el-dropdown-menu>
                <el-dropdown-item command="user">个人中心</el-dropdown-item>
                <el-dropdown-item command="coupon">我的优惠券</el-dropdown-item>
                <el-dropdown-item command="address">收货地址</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu></template>
            </el-dropdown>
            <a v-else class="strip-login" @click="toLogin">登录 / 注册</a>
          </div>
        </div>
      </div>

      <!-- 品牌与搜索 -->
      <header class="shop-header">
        <div class="header-inner">
          <div class="logo" @click="$router.push('/')">
            <img class="logo-img" src="/logo.png" alt="悦集商城" />
            <div class="logo-copy">
              <span class="logo-text">悦集商城</span>
              <span class="logo-en">JOY COLLECTION</span>
            </div>
          </div>
          <div class="search-box">
            <input
              v-model="keyword"
              class="search-input"
              placeholder="搜索商品，例如：手机 / 冰箱 / 双肩包"
              @keyup.enter="goSearch"
            />
            <button class="search-btn" @click="goSearch">搜索</button>
          </div>
          <div class="header-actions">
            <a class="h-action h-cart" @click="$router.push('/cart')">
              <i class="el-icon-shopping-cart-2"></i>
              <span>购物车</span>
              <span v-if="cartCount" class="cart-badge">{{ cartCount }}</span>
            </a>
            <a class="h-action" @click="$router.push('/order/list')">
              <i class="el-icon-tickets"></i>
              <span>我的订单</span>
            </a>
            <a class="h-action" @click="$router.push('/coupon/center')">
              <i class="el-icon-ticket"></i>
              <span>领券中心</span>
            </a>
          </div>
        </div>
      </header>

      <!-- 主导航 -->
      <nav class="cat-nav">
        <div class="cat-nav-inner">
          <a class="cat-link cat-link--home" :class="{ active: $route.path === '/' }" @click="goHome">
            <i class="el-icon-s-home"></i>首页
          </a>
          <a class="cat-link cat-link--hot" @click="go('/seckill')">限时秒杀</a>
          <a class="cat-link cat-link--hot" @click="go('/group')">超值拼团</a>
          <a class="cat-link cat-link--hot" @click="go('/lottery')">幸运抽奖</a>
          <span class="cat-nav-divider"></span>
          <a
            v-for="c in topCategories"
            :key="c.id"
            class="cat-link"
            :class="{ active: $route.query.categoryId == c.id }"
            @click="goCategory(c)"
          >{{ c.name }}</a>
          <a class="cat-nav-all" @click="go('/list')">全部商品 ›</a>
        </div>
      </nav>
    </div>

    <main class="shop-main">
      <router-view :key="$route.fullPath" />
    </main>

    <footer class="shop-footer">
      <p>悦集商城 · 演示环境（yueji_mall）</p>
      <p>PC 端 · 数据来自网关 8080 /shop 接口（mall-cloud 微服务）</p>
    </footer>

    <!-- V16 在线 AI 客服：悬浮球 + 滑出面板（挂在 App 级，路由切换不丢会话） -->
    <AiChatPanel />
  </div>
</template>

<script>
import { getCategories } from '@/api/shop'
import store from '@/store'
import AiChatPanel from '@/components/AiChatPanel.vue'

export default {
  name: 'App',
  components: { AiChatPanel },
  data() {
    return { keyword: '', topCategories: [] }
  },
  computed: {
    isLogin() {
      return !!store.state.token
    },
    nickname() {
      const m = store.state.member
      return (m && (m.nickname || m.phone)) || '我的账户'
    },
    cartCount() {
      return store.state.cartCount
    }
  },
  created() {
    getCategories().then(res => {
      this.topCategories = (res || []).slice(0, 6)
    })
    if (store.isLogin) {
      store.refreshCartCount()
    }
  },
  methods: {
    goSearch() {
      this.$router.push({ path: '/list', query: { keyword: this.keyword || undefined } })
    },
    go(path) {
      this.$router.push(path)
    },
    goHome() {
      this.$router.push('/')
    },
    goCategory(c) {
      this.$router.push({ path: '/list', query: { categoryId: c.id } })
    },
    toLogin() {
      // 已经在登录页就不要再 push 一次，否则 redirect 会套成 /login?redirect=/login?redirect=...
      if (this.$route.path === '/login') return
      this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } })
    },
    async onCommand(cmd) {
      if (cmd === 'logout') {
        await store.logout()
        this.$message.success('已退出登录')
        this.$router.push('/')
        return
      }
      const map = { user: '/user', coupon: '/coupon/my', address: '/address' }
      this.$router.push(map[cmd])
    }
  }
}
</script>
