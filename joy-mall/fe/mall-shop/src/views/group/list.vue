<template>
  <div class="page">
    <van-nav-bar title="拼团" left-arrow fixed placeholder @click-left="router.back()" />

    <div class="gp-actions">
      <van-button size="small" plain round color="#4a6cf7" @click="router.push('/group/my')">我的拼团</van-button>
    </div>

    <div v-for="a in activities" :key="a.id" class="gp-card" @click="router.push(`/group/${a.id}`)">
      <van-image :src="thumb(a.mainImage, 300)" width="96" height="96" radius="6" fit="cover">
        <template #error><div class="gp-img-ph">图</div></template>
      </van-image>
      <div class="gp-info">
        <div class="gp-name van-multi-ellipsis--l2">{{ a.goodsName || '商品名加载中' }}</div>
        <div class="gp-tags">
          <van-tag plain type="primary" color="#4a6cf7">{{ a.groupSize }}人团</van-tag>
          <van-tag plain type="warning">成团时限 {{ a.validHours || 24 }} 小时</van-tag>
        </div>
        <div class="gp-price-row">
          <span class="gp-price">￥{{ fenToYuan(a.groupPrice) }}</span>
          <span class="gp-origin">￥{{ fenToYuan(a.originPrice) }}</span>
        </div>
      </div>
      <van-button round type="primary" color="#e1251b" size="small" class="gp-btn">去拼团</van-button>
    </div>

    <van-empty v-if="!loading && !activities.length" description="暂无进行中的拼团活动" />
    <van-loading v-if="loading" class="gp-loading" vertical>加载中...</van-loading>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getGroupActivities } from '@/api/group'
import { fenToYuan, thumb } from '@/utils/format'

const router = useRouter()
const activities = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    activities.value = (await getGroupActivities()) || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}
.gp-actions {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px 0;
}
.gp-card {
  position: relative;
  display: flex;
  gap: 10px;
  background: #fff;
  margin: 10px;
  border-radius: 8px;
  padding: 12px;
}
.gp-img-ph {
  width: 96px;
  height: 96px;
  line-height: 96px;
  text-align: center;
  background: #f2f3f5;
  color: #c8c9cc;
}
.gp-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-right: 64px;
}
.gp-name {
  font-size: 14px;
  color: #323233;
}
.gp-tags {
  display: flex;
  gap: 6px;
}
.gp-price-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: auto;
}
.gp-price {
  color: #e1251b;
  font-size: 18px;
  font-weight: 700;
}
.gp-origin {
  color: #969799;
  font-size: 12px;
  text-decoration: line-through;
}
.gp-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  padding: 0 14px;
}
.gp-loading {
  margin-top: 40px;
}
</style>
