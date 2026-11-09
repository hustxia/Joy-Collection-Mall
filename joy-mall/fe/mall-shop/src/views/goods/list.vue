<template>
  <div class="page">
    <van-nav-bar :title="title" left-arrow @click-left="router.back()" />
    <van-search v-model="keyword" shape="round" placeholder="搜索商品" @search="onSearch" />

    <!-- 排序：综合 / 销量 / 价格（点击切换升降） -->
    <div class="sort-bar">
      <span :class="{ on: sort === '' }" @click="changeSort('')">综合</span>
      <span :class="{ on: sort === 'sales' }" @click="changeSort('sales')">销量</span>
      <span :class="{ on: isPriceSort }" @click="changeSort('price')">
        价格
        <van-icon v-if="sort === 'priceAsc'" name="arrow-up" />
        <van-icon v-else-if="sort === 'priceDesc'" name="arrow-down" />
        <van-icon v-else name="exchange" />
      </span>
    </div>

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
      <div class="goods-grid">
        <GoodsCard v-for="g in goods" :key="g.id" :goods="g" />
      </div>
      <van-empty v-if="finished && !goods.length" description="没有找到相关商品" />
    </van-list>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getGoodsPage } from '@/api/shop'
import GoodsCard from '@/components/GoodsCard.vue'

const route = useRoute()
const router = useRouter()

const title = computed(() => route.query.title || '商品列表')
const keyword = ref(route.query.keyword || '')
const categoryId = route.query.categoryId ? Number(route.query.categoryId) : undefined

// sort: '' 综合 | sales | priceAsc | priceDesc
const sort = ref('')
const isPriceSort = computed(() => sort.value === 'priceAsc' || sort.value === 'priceDesc')

const goods = ref([])
const pageNum = ref(1)
const pageSize = 12
const loading = ref(false)
const finished = ref(false)

const onLoad = async () => {
  try {
    const page = await getGoodsPage({
      pageNum: pageNum.value,
      pageSize,
      categoryId,
      keyword: keyword.value || undefined,
      sort: sort.value || undefined,
    })
    goods.value.push(...(page.records || []))
    pageNum.value += 1
    if (!page.records || goods.value.length >= page.total) {
      finished.value = true
    }
  } finally {
    loading.value = false
  }
}

const reload = () => {
  goods.value = []
  pageNum.value = 1
  finished.value = false
  loading.value = true
  onLoad()
}

const onSearch = () => reload()

const changeSort = s => {
  if (s === 'price') {
    // 价格：asc → desc → asc 循环
    sort.value = sort.value === 'priceAsc' ? 'priceDesc' : 'priceAsc'
  } else {
    sort.value = s
  }
  reload()
}
</script>

<style scoped>
.sort-bar {
  display: flex;
  background: #fff;
  border-bottom: 1px solid #ebedf0;
  padding: 0 16px;
}

.sort-bar span {
  flex: 1;
  text-align: center;
  font-size: 13px;
  color: #646566;
  padding: 10px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.sort-bar span.on {
  color: #4a6cf7;
  font-weight: 600;
}
</style>
