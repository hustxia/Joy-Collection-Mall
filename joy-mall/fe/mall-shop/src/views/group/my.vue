<template>
  <div class="page">
    <van-nav-bar title="我的拼团" left-arrow fixed placeholder @click-left="router.back()" />

    <van-cell-group inset class="gpm-list">
      <div v-for="t in teams" :key="t.teamId + t.isLeader" class="gpm-item">
        <van-image :src="thumb(t.mainImage, 200)" width="64" height="64" radius="6" fit="cover">
          <template #error><div class="gpm-img-ph">图</div></template>
        </van-image>
        <div class="gpm-info">
          <div class="gpm-name van-ellipsis">{{ t.goodsName || '拼团商品' }}</div>
          <div class="gpm-meta">
            <van-tag :type="tagType(t.teamStatus)">{{ statusText(t.teamStatus) }}</van-tag>
            <span>{{ t.currentSize }}/{{ t.groupSize }}人</span>
            <van-tag v-if="t.isLeader === 1" plain type="primary" color="#4a6cf7">团长</van-tag>
          </div>
          <div class="gpm-price">￥{{ fenToYuan(t.groupPrice) }}</div>
        </div>
        <van-button
          round
          size="small"
          plain
          type="primary"
          color="#4a6cf7"
          @click="router.push(`/group/${t.activityId}`)"
        >
          活动详情
        </van-button>
      </div>
      <van-empty v-if="!loading && !teams.length" description="还没有参与过拼团" />
    </van-cell-group>

    <van-loading v-if="loading" class="gpm-loading" vertical>加载中...</van-loading>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyGroups } from '@/api/group'
import { fenToYuan, thumb } from '@/utils/format'

const router = useRouter()
const teams = ref([])
const loading = ref(true)

const TEAM_STATUS = { 0: '拼团中', 1: '已成团', 2: '已失败' }
const statusText = s => TEAM_STATUS[s] || '未知'
const tagType = s => (s === 1 ? 'success' : s === 2 ? 'danger' : 'warning')

onMounted(async () => {
  try {
    teams.value = (await getMyGroups()) || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}
.gpm-list {
  margin-top: 12px;
}
.gpm-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #f2f3f5;
}
.gpm-img-ph {
  width: 64px;
  height: 64px;
  line-height: 64px;
  text-align: center;
  background: #f2f3f5;
  color: #c8c9cc;
}
.gpm-info {
  flex: 1;
  min-width: 0;
}
.gpm-name {
  font-size: 14px;
  color: #323233;
}
.gpm-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #969799;
  margin: 4px 0;
}
.gpm-price {
  color: #e1251b;
  font-size: 14px;
  font-weight: 600;
}
.gpm-loading {
  margin-top: 40px;
}
</style>
