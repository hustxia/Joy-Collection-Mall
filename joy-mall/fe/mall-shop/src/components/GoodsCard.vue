<template>
  <div class="goods-card" @click="goDetail">
    <van-image class="goods-card-img" fit="cover" :src="thumb(goods.mainImage, 300)" lazy-load>
      <template #error><div class="img-ph">暂无图片</div></template>
      <template #loading><div class="img-ph">加载中</div></template>
      <div v-if="promo" class="promo-badge">{{ promo.ruleText }}</div>
    </van-image>
    <div v-if="goods.merchantName" class="goods-card-shop" @click.stop="goShop">
      <van-icon name="shop-o" />
      <span class="van-ellipsis">{{ goods.merchantName }}</span>
    </div>
    <div class="goods-card-name van-multi-ellipsis--l2">{{ goods.name }}</div>
    <div class="goods-card-bottom">
      <!-- 限时折扣：活动价 + 划线原价；其余活动只显示角标不改价 -->
      <span v-if="promoPriceFen !== null" class="price">
        <span class="symbol">¥</span>{{ fenToYuan(promoPriceFen) }}
        <span class="origin">¥{{ fenToYuan(goods.priceMin) }}</span>
      </span>
      <span v-else class="price"><span class="symbol">¥</span>{{ fenToYuan(goods.priceMin) }}</span>
      <span class="sales">已售{{ goods.sales || 0 }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fenToYuan, thumb } from '@/utils/format'
import { getGoodsPromotions, pickBadge, promoPrice } from '@/api/promotion'

// 商品卡片（双列流用）
const props = defineProps({
  goods: { type: Object, required: true },
})

// 促销角标（V15）：available 匿名可读，失败/无活动时角标不展示
const promo = ref(null)
onMounted(async () => {
  promo.value = pickBadge(await getGoodsPromotions(props.goods.id))
})
const promoPriceFen = computed(() =>
  promo.value && Number(promo.value.type) === 3
    ? promoPrice(props.goods.priceMin, promo.value.discountRate)
    : null,
)

const router = useRouter()
const goDetail = () => router.push(`/goods/${props.goods.id}`)
// 店铺标：阻止冒泡，避免点店铺时进了商品详情
const goShop = () => {
  if (!props.goods.merchantId) return
  router.push(`/merchant/${props.goods.merchantId}`)
}
</script>

<style scoped>
.goods-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  padding-bottom: 8px;
}

.goods-card-img {
  width: 100%;
  height: 150px;
  display: block;
}

/* 活动角标（V15）：贴图左上角 */
.promo-badge {
  position: absolute;
  top: 6px;
  left: 6px;
  max-width: calc(100% - 12px);
  padding: 1px 6px;
  border-radius: 4px 8px 8px 2px;
  background: linear-gradient(135deg, #ff6034, #ee0a24);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.origin {
  margin-left: 3px;
  color: #969799;
  font-size: 11px;
  text-decoration: line-through;
  font-weight: 400;
}

.img-ph {
  width: 100%;
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f2f3f5;
  color: #c8c9cc;
  font-size: 12px;
}

.goods-card-name {
  margin: 6px 8px 4px;
  font-size: 13px;
  line-height: 18px;
  height: 36px;
}

.goods-card-shop {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  max-width: calc(100% - 16px);
  margin: 6px 8px 0;
  padding: 1px 6px;
  border-radius: 8px;
  background: #fff8e6;
  color: #8a6d3b;
  font-size: 10px;
}

.goods-card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin: 0 8px;
}

.sales {
  font-size: 11px;
  color: #969799;
}
</style>
