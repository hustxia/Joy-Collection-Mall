<template>
  <div class="page">
    <van-nav-bar :title="shop.name || '店铺'" left-arrow @click-left="router.back()" />

    <!-- 店铺头 -->
    <div v-if="shop.id" class="shop-hero">
      <van-image v-if="shop.logo" :src="shop.logo" fit="cover" round width="52" height="52" />
      <div v-else class="shop-hero-ph">{{ (shop.name || '?').slice(0, 1) }}</div>
      <div class="shop-hero-main">
        <div class="shop-hero-name van-ellipsis">{{ shop.name }}</div>
        <div class="shop-hero-sub text-gray van-ellipsis">
          {{ shop.category || '综合店铺' }} · 在售 {{ total }} 件
        </div>
      </div>
    </div>
    <div v-if="shop.id && shop.intro" class="shop-intro">{{ shop.intro }}</div>

    <!-- 停业 / 不存在 -->
    <van-empty v-if="loaded && !shop.id" :description="errorText || '店铺不存在或已停业'">
      <van-button round type="primary" color="#4a6cf7" size="small" @click="router.push('/home')">去首页逛逛</van-button>
    </van-empty>

    <!-- 店内商品 -->
    <template v-if="shop.id">
      <div class="section-title">店内商品</div>
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
        <div class="goods-grid">
          <GoodsCard v-for="g in goods" :key="g.id" :goods="g" />
        </div>
        <van-empty v-if="finished && !goods.length" description="这家店铺还没有上架商品" />
      </van-list>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMerchantDetail, getGoodsPage } from '@/api/shop'
import GoodsCard from '@/components/GoodsCard.vue'

const route = useRoute()
const router = useRouter()

const shop = ref({})
const errorText = ref('')
const loaded = ref(false)

const goods = ref([])
const pageNum = ref(1)
const pageSize = 12
const loading = ref(false)
const finished = ref(false)
const total = ref(0)

const onLoad = async () => {
  try {
    const page = await getGoodsPage({
      pageNum: pageNum.value,
      pageSize,
      merchantId: route.params.id,
    })
    goods.value.push(...(page.records || []))
    total.value = Number(page.total || 0)
    pageNum.value += 1
    if (!page.records || goods.value.length >= total.value) {
      finished.value = true
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    shop.value = (await getMerchantDetail(route.params.id)) || {}
  } catch (e) {
    shop.value = {}
    errorText.value = (e && e.message) || '店铺不存在或已停业'
  } finally {
    loaded.value = true
  }
  if (!shop.value.id) finished.value = true
})
</script>

<style scoped>
.shop-hero {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 10px;
  padding: 14px;
  background: #fff;
  border-radius: 10px;
}

.shop-hero-ph {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: #e8edff;
  color: #4a6cf7;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
  flex: none;
}

.shop-hero-main {
  min-width: 0;
  flex: 1;
}

.shop-hero-name {
  font-size: 16px;
  font-weight: 600;
}

.shop-hero-sub {
  font-size: 12px;
  margin-top: 4px;
}

.shop-intro {
  margin: 0 10px 8px;
  padding: 10px 12px;
  background: #fff;
  border-radius: 10px;
  font-size: 12px;
  color: #646566;
  line-height: 1.6;
}

.section-title {
  margin: 12px 10px 8px;
  font-size: 15px;
  font-weight: 600;
}

.goods-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 0 10px 12px;
}
</style>
