<template>
  <div class="page page--tab">
    <van-nav-bar title="分类" />
    <div v-if="categories.length" class="cat-layout">
      <!-- 左侧一级分类 -->
      <van-sidebar v-model="activeIndex" class="cat-left">
        <van-sidebar-item v-for="c in categories" :key="c.id" :title="c.name" />
      </van-sidebar>

      <!-- 右侧：二级分类 + 当前一级分类商品 -->
      <div class="cat-right">
        <template v-if="children.length">
          <div class="cr-title">{{ current.name }}</div>
          <div class="cr-grid">
            <div v-for="sub in children" :key="sub.id" class="cr-item" @click="goList(sub)">
              <img v-if="sub.icon" :src="sub.icon" class="cr-img" alt="" />
              <div v-else class="cr-img cr-img--ph">{{ (sub.name || '')[0] }}</div>
              <div class="cr-name van-ellipsis">{{ sub.name }}</div>
            </div>
          </div>
        </template>
        <div class="cr-title">热门商品</div>
        <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
          <div class="goods-grid goods-grid--inner">
            <GoodsCard v-for="g in goods" :key="g.id" :goods="g" />
          </div>
          <van-empty v-if="finished && !goods.length" description="该分类暂无商品" />
        </van-list>
      </div>
    </div>
    <van-empty v-else description="暂无分类" />
    <TabBar />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCategories, getGoodsPage } from '@/api/shop'
import GoodsCard from '@/components/GoodsCard.vue'
import TabBar from '@/components/TabBar.vue'

const route = useRoute()
const router = useRouter()

const categories = ref([])
const activeIndex = ref(0)

const current = computed(() => categories.value[activeIndex.value] || {})
const children = computed(() => current.value.children || [])

// 右侧商品流：按当前一级分类筛选（后端含子类）
const goods = ref([])
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)

const onLoad = async () => {
  if (!current.value.id) {
    loading.value = false
    return
  }
  try {
    const page = await getGoodsPage({ pageNum: pageNum.value, pageSize, categoryId: current.value.id })
    goods.value.push(...(page.records || []))
    pageNum.value += 1
    if (!page.records || goods.value.length >= page.total) {
      finished.value = true
    }
  } finally {
    loading.value = false
  }
}

// 切换一级分类：重置商品流
watch(activeIndex, () => {
  goods.value = []
  pageNum.value = 1
  finished.value = false
  loading.value = true
  onLoad()
})

const goList = sub => router.push({ path: '/goods/list', query: { categoryId: sub.id, title: sub.name } })

onMounted(async () => {
  categories.value = await getCategories()
  // 首页宫格带 active=<分类id> 进来时定位
  const qid = Number(route.query.active)
  if (qid) {
    const idx = categories.value.findIndex(c => c.id === qid)
    if (idx >= 0) activeIndex.value = idx
  }
})
</script>

<style scoped>
.cat-layout {
  display: flex;
  height: calc(100vh - 46px - 50px);
  overflow: hidden;
}

.cat-left {
  height: 100%;
  overflow-y: auto;
}

.cat-right {
  flex: 1;
  height: 100%;
  overflow-y: auto;
  padding: 8px 10px;
  background: #fff;
}

.cr-title {
  font-size: 14px;
  font-weight: 600;
  margin: 6px 0 10px;
}

.cr-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.cr-item {
  width: calc(33.3% - 7px);
  text-align: center;
}

.cr-img {
  width: 100%;
  height: 64px;
  border-radius: 6px;
  object-fit: cover;
  margin-bottom: 4px;
}

.cr-img--ph {
  background: #f2f3f5;
  color: #4a6cf7;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.cr-name {
  font-size: 12px;
  color: #646566;
}

.goods-grid--inner {
  padding: 0;
}
</style>
