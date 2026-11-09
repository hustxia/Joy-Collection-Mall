<template>
  <router-view />
  <!-- V16 在线客服悬浮球：App 级挂载，路由切换不丢状态 -->
  <ServiceFloatBall />
</template>

<script setup>
import ServiceFloatBall from '@/components/ServiceFloatBall.vue'
</script>
