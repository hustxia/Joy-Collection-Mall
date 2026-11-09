<template>
  <div class="page login-page">
    <div class="login-logo">mall</div>

    <!-- 登录 / 注册 切换 -->
    <div class="login-tabs">
      <span :class="{ on: mode === 'login' }" @click="mode = 'login'">登录</span>
      <span :class="{ on: mode === 'register' }" @click="mode = 'register'">注册</span>
    </div>

    <div class="login-form">
      <van-field v-model="form.phone" type="tel" maxlength="11" placeholder="手机号" class="lf" />
      <van-field v-model="form.password" type="password" maxlength="20" placeholder="密码（6~20位）" class="lf" />
      <van-field v-if="mode === 'register'" v-model="form.nickname" maxlength="32" placeholder="昵称（选填）" class="lf" />
      <van-button block round color="#4a6cf7" :loading="submitting" @click="onSubmit">
        {{ mode === 'login' ? '登 录' : '注册并登录' }}
      </van-button>
      <div class="tip">{{ mode === 'login' ? '未注册手机号请先注册' : '注册即视为同意平台服务协议' }}</div>
      <!-- 演示环境账号提示：种子会员历史密码为 NULL，用户易误以为是自己密码记错 -->
      <div v-if="mode === 'login'" class="demo-tip">
        演示账号 13800000001 ~ 13800000006 / 密码 <b>123456</b>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const mode = ref('login')
const submitting = ref(false)
const form = reactive({ phone: '', password: '', nickname: '' })

const onSubmit = async () => {
  if (!/^1\d{10}$/.test(form.phone)) return showToast('请输入正确的手机号')
  if (form.password.length < 6 || form.password.length > 20) return showToast('密码需为 6~20 位')
  submitting.value = true
  try {
    if (mode.value === 'login') {
      await user.login(form.phone, form.password)
    } else {
      await user.register(form.phone, form.password, form.nickname || undefined)
    }
    showToast(mode.value === 'login' ? '登录成功' : '注册成功')
    router.replace(route.query.redirect || '/home')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  background: #fff;
}

.login-logo {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  margin: 60px auto 12px;
  background: linear-gradient(135deg, #4a6cf7, #7a9bff);
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-tabs {
  display: flex;
  justify-content: center;
  gap: 32px;
  font-size: 15px;
  color: #969799;
  margin: 16px 0 28px;
}

.login-tabs .on {
  color: #4a6cf7;
  font-weight: 600;
}

.login-form {
  padding: 0 32px;
}

.lf {
  border: 1px solid #e5e6eb;
  border-radius: 22px;
  margin-bottom: 14px;
}

.tip {
  text-align: center;
  font-size: 11px;
  color: #c8c9cc;
  margin-top: 16px;
}

.demo-tip {
  text-align: center;
  font-size: 11px;
  color: #969799;
  margin-top: 8px;
  line-height: 16px;
}

.demo-tip b {
  color: #4a6cf7;
  font-weight: 600;
}
</style>
