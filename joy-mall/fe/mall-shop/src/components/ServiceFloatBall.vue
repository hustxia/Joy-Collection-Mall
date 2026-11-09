<template>
  <!-- V16 在线客服悬浮球：聊天页与登录页不显示 -->
  <div v-if="show" class="svc-ball" @click="router.push('/service-chat?source=float')">
    <van-icon name="chat-o" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const show = computed(() => route.path !== '/service-chat' && route.path !== '/login')
</script>

<style scoped>
.svc-ball {
  position: fixed;
  right: 16px;
  /* 80px：避开首页/购物车等页 50px 高的 van-tabbar */
  bottom: 80px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #e1251b;
  color: #fff;
  font-size: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(225, 37, 27, 0.4);
  z-index: 100;
}
</style>
