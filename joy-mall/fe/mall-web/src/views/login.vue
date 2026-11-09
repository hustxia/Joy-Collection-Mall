<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>悦集商城后台</h2>
      <el-form @submit.prevent="doLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-form-item>
          <div class="captcha-row">
            <el-input v-model="form.captchaAnswer" placeholder="计算结果" @keyup.enter="doLogin" />
            <img v-if="captchaImage" :src="captchaImage" class="captcha-img" title="点击刷新" @click="loadCaptcha" />
          </div>
        </el-form-item>
        <el-button type="primary" :loading="loading" native-type="submit" style="width:100%">登 录</el-button>
      </el-form>
      <p class="tip">默认账号 admin / admin123</p>
    </el-card>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'Login',
  data() {
    return {
      form: { username: 'admin', password: '', captchaId: '', captchaAnswer: '' },
      captchaImage: '',
      loading: false
    }
  },
  created() {
    this.loadCaptcha()
  },
  methods: {
    async loadCaptcha() {
      const res = await request.get('/auth/captcha')
      this.captchaImage = res.data.image
      this.form.captchaId = res.data.captchaId
      this.form.captchaAnswer = ''
    },
    async doLogin() {
      if (!this.form.username || !this.form.password) return
      if (!this.form.captchaAnswer) {
        this.$message.warning('请输入验证码')
        return
      }
      this.loading = true
      try {
        const res = await request.post('/auth/login', this.form)
        localStorage.setItem('token', res.data.token)
        localStorage.setItem('username', res.data.username)
        // V6.4：perms 落地供 v-hasPermi 指令与后端权限校验对齐
        localStorage.setItem('perms', JSON.stringify(res.data.perms || []))
        this.$router.push('/')
      } catch (e) {
        // 验证码一次性：失败（含密码错）后必须换新码，否则重放必错
        this.loadCaptcha()
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.login-wrap { display: flex; align-items: center; justify-content: center; height: 100vh; background: #f0f2f5; }
.login-card { width: 360px; text-align: center; }
.captcha-row { display: flex; gap: 8px; width: 100%; }
.captcha-img { height: 40px; cursor: pointer; border-radius: 4px; }
.tip { color: #999; font-size: 12px; margin-bottom: 0; }
</style>
