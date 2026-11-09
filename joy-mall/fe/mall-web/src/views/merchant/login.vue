<template>
  <div class="mch-login">
    <el-card class="mch-card" shadow="always">
      <div class="brand">
        <h2>悦集商城 · 商家中心</h2>
        <p>店铺经营、发品发货、售后与结算</p>
      </div>
      <el-form ref="form" :model="form" @submit.prevent="doLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="商家账号" prefix-icon="el-icon-user" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" prefix-icon="el-icon-lock" />
        </el-form-item>
        <el-button type="primary" :loading="loading" native-type="submit" style="width:100%">登 录</el-button>
      </el-form>
      <p class="tip">演示账号 merchant / 123456（与平台后台账号不通用）</p>
      <p class="tip">平台管理员请走 <a href="/#/login">平台登录</a></p>
    </el-card>
  </div>
</template>

<script>
import { merchantLogin } from '@/api/merchant'

export default {
  name: 'MerchantLogin',
  data() {
    return {
      form: { username: 'merchant', password: '' },
      loading: false
    }
  },
  methods: {
    async doLogin() {
      if (!this.form.username || !this.form.password) {
        this.$message.warning('请输入账号和密码')
        return
      }
      this.loading = true
      try {
        const res = await merchantLogin(this.form)
        const data = res.data || {}
        localStorage.setItem('merchantToken', data.token)
        localStorage.setItem('merchant', JSON.stringify(data.merchant || {}))
        localStorage.setItem('merchantUser', JSON.stringify(data.user || {}))
        // 商家与平台两套会话并存时容易串号：登录商家后台就清掉平台 token
        localStorage.removeItem('token')
        this.$message.success('登录成功')
        this.$router.push('/merchant/overview')
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.mch-login { display: flex; align-items: center; justify-content: center; min-height: 100vh; background: #eef2f7; }
.mch-card { width: 380px; padding: 8px 4px 0; }
.brand { text-align: center; margin-bottom: 18px; }
.brand h2 { margin: 6px 0; font-size: 20px; color: #1f2d3d; }
.brand p { margin: 0; color: #909399; font-size: 13px; }
.tip { color: #a8abb2; font-size: 12px; text-align: center; margin: 8px 0 0; }
</style>
