<template>
  <div class="mch-layout">
    <aside class="mch-aside">
      <div class="logo">
        <span class="name">{{ merchant.name || '我的店铺' }}</span>
        <span class="no">{{ merchant.merchantNo }}</span>
      </div>
      <el-menu :default-active="$route.path" router class="mch-menu" background-color="#1f2d3d" text-color="#c0c4cc" active-text-color="#ffd04b">
        <el-menu-item index="/merchant/overview"><i class="el-icon-data-line" /><span>经营概览</span></el-menu-item>
        <el-menu-item index="/merchant/goods"><i class="el-icon-goods" /><span>商品管理</span></el-menu-item>
        <el-menu-item index="/merchant/order"><i class="el-icon-s-order" /><span>订单与发货</span></el-menu-item>
        <el-menu-item index="/merchant/refund"><i class="el-icon-refresh-left" /><span>售后审核</span></el-menu-item>
        <el-menu-item index="/merchant/fund"><i class="el-icon-wallet" /><span>结算与提现</span></el-menu-item>
      </el-menu>
    </aside>
    <section class="mch-main">
      <header class="mch-header">
        <div class="left">
          <el-tag size="small" type="warning" effect="plain">商家后台</el-tag>
          <span class="balance">账户余额 ¥{{ fenToYuan(merchant.accountBalance) }}</span>
        </div>
        <div class="right">
          <span class="user">{{ user.realName || user.username }}</span>
          <el-button type="text" @click="logout">退出</el-button>
        </div>
      </header>
      <div class="mch-body">
        <router-view />
      </div>
    </section>
  </div>
</template>

<script>
import { fenToYuan } from '@/utils/format'
import { merchantLogout } from '@/api/merchant'

export default {
  name: 'MerchantLayout',
  data() {
    return {
      merchant: {},
      user: {}
    }
  },
  created() {
    this.refresh()
  },
  methods: {
    fenToYuan,
    refresh() {
      this.merchant = JSON.parse(localStorage.getItem('merchant') || '{}')
      this.user = JSON.parse(localStorage.getItem('merchantUser') || '{}')
    },
    async logout() {
      try {
        await merchantLogout()
      } catch (e) {
        // 退出接口失败不阻断，本地清干净即可
      }
      localStorage.removeItem('merchantToken')
      localStorage.removeItem('merchant')
      localStorage.removeItem('merchantUser')
      this.$router.push('/merchant/login')
    }
  }
}
</script>

<style scoped>
.mch-layout { display: flex; min-height: 100vh; background: #f0f2f5; }
.mch-aside { width: 200px; background: #1f2d3d; }
.logo { padding: 16px 16px 10px; color: #fff; border-bottom: 1px solid rgba(255, 255, 255, 0.08); }
.logo .name { display: block; font-size: 15px; font-weight: 600; }
.logo .no { display: block; font-size: 12px; color: #8a97a8; margin-top: 2px; }
.mch-menu { border-right: none; }
.mch-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.mch-header { height: 52px; background: #fff; display: flex; align-items: center; justify-content: space-between; padding: 0 18px; box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08); }
.mch-header .left { display: flex; align-items: center; gap: 12px; }
.balance { color: #606266; font-size: 13px; }
.mch-header .right { display: flex; align-items: center; gap: 10px; }
.user { color: #606266; font-size: 13px; }
.mch-body { padding: 16px; }
</style>
