<template>
  <div class="page">
    <van-nav-bar title="我的足迹" left-arrow fixed placeholder @click-left="router.back()">
      <template #right>
        <span v-if="list.length" class="nav-clear" @click="onClear">清空</span>
      </template>
    </van-nav-bar>

    <van-empty v-if="!loading && !list.length" description="还没有浏览记录，去逛逛吧" />

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
      <div v-for="it in list" :key="it.goodsId" class="fp-item" @click="goGoods(it.goodsId)">
        <van-image :src="thumb(it.mainImage)" fit="cover" class="fp-img" radius="6" />
        <div class="fp-main">
          <div class="fp-name">{{ it.goodsName || '商品已下架' }}</div>
          <div class="fp-price">{{ fenToYuan(it.priceMin) }}</div>
          <div class="fp-time">{{ it.viewTime }}</div>
        </div>
      </div>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getFootprintList, clearFootprint } from '@/api/footprint'
import { fenToYuan, thumb } from '@/utils/format'

const router = useRouter()
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const finished = ref(false)

const onLoad = async () => {
  try {
    const data = await getFootprintList({ pageNum: page.value, pageSize: size })
    const rows = data.list || []
    total.value = data.total || 0
    list.value = page.value === 1 ? rows : list.value.concat(rows)
    page.value += 1
    finished.value = list.value.length >= total.value
  } catch (e) {
    finished.value = true
  } finally {
    loading.value = false
  }
}

const onClear = async () => {
  try {
    await showConfirmDialog({ title: '清空足迹', message: '确定清空全部浏览记录吗？', confirmButtonColor: '#4a6cf7' })
  } catch (e) {
    return
  }
  const data = await clearFootprint()
  showToast(`已清空 ${data.cleared || 0} 条`)
  list.value = []
  total.value = 0
  page.value = 1
  finished.value = true
}

const goGoods = id => router.push(`/goods/${id}`)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}
.nav-clear {
  font-size: 14px;
  color: #4a6cf7;
}
.fp-item {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  background: #fff;
  border-bottom: 1px solid #f2f3f5;
}
.fp-img {
  width: 84px;
  height: 84px;
  flex: none;
}
.fp-main {
  flex: 1;
  min-width: 0;
}
.fp-name {
  font-size: 14px;
  color: #323233;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.fp-price {
  margin-top: 6px;
  color: #e1251b;
  font-size: 15px;
  font-weight: 600;
}
.fp-time {
  margin-top: 4px;
  font-size: 11px;
  color: #969799;
}
</style>
