<template>
  <div class="page">
    <van-nav-bar title="编辑资料" left-arrow @click-left="router.back()" />

    <van-cell-group inset class="block">
      <van-field v-model="form.nickname" label="昵称" placeholder="请输入昵称" maxlength="32" />
      <van-field v-model="form.avatar" label="头像 URL" placeholder="请输入头像图片地址" />
      <van-cell title="性别">
        <template #value>
          <van-radio-group v-model="form.gender" direction="horizontal">
            <van-radio :name="0" checked-color="#4a6cf7">未知</van-radio>
            <van-radio :name="1" checked-color="#4a6cf7">男</van-radio>
            <van-radio :name="2" checked-color="#4a6cf7">女</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
    </van-cell-group>

    <div class="btn-wrap">
      <van-button block round color="#4a6cf7" :loading="saving" @click="onSave">保存</van-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { updateProfile } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const user = useUserStore()
const saving = ref(false)

const form = reactive({ nickname: '', avatar: '', gender: 0 })

const onSave = async () => {
  saving.value = true
  try {
    await updateProfile({
      nickname: form.nickname || undefined,
      avatar: form.avatar || undefined,
      gender: form.gender,
    })
    await user.fetchProfile()
    showToast('已保存')
    router.back()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (user.member) {
    form.nickname = user.member.nickname || ''
    form.avatar = user.member.avatar || ''
    form.gender = user.member.gender ?? 0
  }
})
</script>

<style scoped>
.block {
  margin-top: 8px;
}

.btn-wrap {
  padding: 20px 16px;
}
</style>
