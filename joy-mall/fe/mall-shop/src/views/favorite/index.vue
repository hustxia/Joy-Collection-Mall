<template>
  <div class="page">
    <van-nav-bar title="我的收藏" left-arrow fixed placeholder @click-left="router.back()" />

    <van-empty v-if="!loading && !list.length" description="还没有收藏商品，去逛逛吧" />

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
      <div v-for="it in list" :key="it.id" class="fv-item" @click="goGoods(it)">
        <van-image :src="thumb(it.mainImage)" fit="cover" class="fv-img" radius="6" />
        <div class="fv-main">
          <div class="fv-name">{{ it.goodsName || '商品已下架' }}</div>
          <div class="fv-price">{{ it.priceMin == null ? '-' : fenToYuan(it.priceMin) }}</div>
        </div>
        <van-button
          size="small"
          round
          plain
          class="fv-cancel"
          @click.stop="onCancel(it)"
        >取消收藏</van-button>
      </div>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getFavoriteList, toggleFavorite } from '@/api/favorite'
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
    const data = await getFavoriteList({ pageNum: page.value, pageSize: size })
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

const onCancel = async it => {
  await toggleFavorite(it.goodsId)
  showToast('已取消收藏')
  list.value = list.value.filter(x => x.id !== it.id)
  total.value -= 1
}

const goGoods = it => {
  if (!it.goodsName) return showToast('商品已下架')
  router.push(`/goods/${it.goodsId}`)
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}
.fv-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: #fff;
  border-bottom: 1px solid #f2f3f5;
}
.fv-img {
  width: 84px;
  height: 84px;
  flex: none;
}
.fv-main {
  flex: 1;
  min-width: 0;
}
.fv-name {
  font-size: 14px;
  color: #323233;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.fv-price {
  margin-top: 6px;
  color: #e1251b;
  font-size: 15px;
  font-weight: 600;
}
.fv-price::before {
  content: '¥';
  font-size: 12px;
}
.fv-cancel {
  flex: none;
}
</style>
