<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-brand">悦集商城</div>
      <div class="login-sub">会员账号可用于下单、查订单、领券</div>

      <div class="login-tabs">
        <span class="login-tab" :class="{ on: mode === 'login' }" @click="switchMode('login')">登录</span>
        <span class="login-tab" :class="{ on: mode === 'register' }" @click="switchMode('register')">注册</span>
      </div>

      <el-form ref="form" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model.trim="form.phone" maxlength="11" placeholder="请输入 11 位手机号" prefix-icon="el-icon-mobile-phone" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" maxlength="20" show-password placeholder="6~20 位密码" prefix-icon="el-icon-lock" @keyup.enter="onSubmit" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="昵称（选填）" prop="nickname">
          <el-input v-model.trim="form.nickname" maxlength="32" placeholder="不填就用手机号后四位" prefix-icon="el-icon-user" @keyup.enter="onSubmit" />
        </el-form-item>
        <el-button type="danger" :loading="submitting" @click="onSubmit">
          {{ mode === 'login' ? '登 录' : '注册并登录' }}
        </el-button>
      </el-form>

      <div class="login-foot">{{ mode === 'login' ? '未注册手机号请先切换到注册' : '注册即视为同意平台服务协议' }}</div>
      <!-- 演示环境账号提示：种子会员历史密码为 NULL，不给提示用户会误以为密码记错反复重试 -->
      <div v-if="mode === 'login'" class="login-demo">
        演示账号 13800000001 ~ 13800000006 / 密码 <b>123456</b>
      </div>
    </div>
  </div>
</template>

<script>
import store from '@/store'

export default {
  name: 'Login',
  data() {
    return {
      mode: 'login',
      submitting: false,
      form: { phone: '', password: '', nickname: '' },
      rules: {
        phone: [
          { required: true, message: '请输入手机号', trigger: 'blur' },
          { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 6, max: 20, message: '密码需为 6~20 位', trigger: 'blur' }
        ]
      }
    }
  },
  methods: {
    switchMode(mode) {
      this.mode = mode
      this.$refs.form && this.$refs.form.clearValidate()
    },
    onSubmit() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.submitting = true
        try {
          if (this.mode === 'login') {
            await store.login(this.form.phone, this.form.password)
            this.$message.success('登录成功')
          } else {
            await store.register({
              phone: this.form.phone,
              password: this.form.password,
              nickname: this.form.nickname || undefined
            })
            this.$message.success('注册成功')
          }
          // 回跳来源页；没有则回首页
          const redirect = this.$route.query.redirect
          this.$router.replace(redirect || '/')
        } finally {
          this.submitting = false
        }
      })
    }
  }
}
</script>
