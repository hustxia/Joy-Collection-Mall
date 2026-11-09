<template>
  <div class="page">
    <van-nav-bar title="商品详情" left-arrow @click-left="router.back()" />
    <template v-if="goods">
      <!-- 轮播图 -->
      <van-swipe class="hero" :autoplay="3500" lazy-render indicator-color="#4a6cf7">
        <van-swipe-item v-for="(img, i) in heroImages" :key="i">
          <van-image :src="img" fit="cover" class="hero-img" />
        </van-swipe-item>
      </van-swipe>

      <!-- 价格与标题 -->
      <div class="card">
        <div class="price-row">
          <!-- 限时折扣：活动价 + 划线原价 -->
          <span v-if="promoPriceFen !== null" class="price price-main">
            <span class="symbol">¥</span>{{ fenToYuan(promoPriceFen) }}
            <span class="origin">¥{{ priceText }}</span>
          </span>
          <span v-else class="price price-main">
            <span class="symbol">¥</span>{{ priceText }}
          </span>
          <span class="text-gray sales">已售 {{ goods.sales || 0 }}</span>
        </div>
        <div class="g-name">{{ goods.name }}</div>
        <div v-if="goods.subTitle" class="g-sub text-gray">{{ goods.subTitle }}</div>
      </div>

      <!-- 促销活动（V15）：活动标签 + 限时折扣倒计时（用服务端相对秒数，避免本地时间格式兼容问题） -->
      <div v-if="promos.length" class="card promo-card">
        <div v-for="p in promos" :key="p.id" class="promo-row">
          <van-tag type="danger" class="promo-tag">{{ p.typeText }}</van-tag>
          <span class="promo-name van-ellipsis">{{ p.name }}</span>
          <span class="promo-rule">{{ p.ruleText }}</span>
          <span v-if="Number(p.type) === 3 && p.countdownSeconds > 0" class="promo-cd">
            距结束 <b>{{ countdownText(p.countdownSeconds) }}</b>
          </span>
        </div>
      </div>

      <!-- 规格入口 -->
      <van-cell class="card cell-card" is-link @click="openSku('cart')">
        <template #title>
          <span class="text-gray">选择</span>
          <span class="cell-val">{{ selectedSku ? specText(selectedSku.specJson) : '规格' }}</span>
        </template>
      </van-cell>

      <!-- 所属店铺：多商家平台下买家关心谁发货、谁售后 -->
      <van-cell
        v-if="goods.merchantName || goods.merchantId"
        class="card cell-card"
        is-link
        @click="router.push(`/merchant/${goods.merchantId}`)"
      >
        <template #title>
          <van-icon name="shop-o" class="shop-ico" />
          <span class="cell-val">{{ goods.merchantName || ('店铺 #' + goods.merchantId) }}</span>
        </template>
        <template #value><span class="text-gray" style="font-size:12px">进店逛逛</span></template>
      </van-cell>

      <!-- 商品评价（V7/V15）：均分条 + 标签云筛选 + 列表（含追评/有用/晒单，匿名可读） -->
      <div v-if="reviewTotal > 0" class="card">
        <div class="rv-head">
          <span class="detail-title">商品评价（{{ reviewTotal }}）</span>
          <span class="rv-avg">
            <van-rate :model-value="Number(reviewAvg)" readonly allow-half size="12" color="#ffb400" />
            {{ reviewAvg }} 分
          </span>
        </div>
        <!-- V15 标签云筛选 -->
        <div v-if="reviewTags.length" class="rv-tags">
          <span
            v-for="t in reviewTags"
            :key="t"
            class="rv-tag"
            :class="{ on: activeTag === t }"
            @click="onTagClick(t)"
          >{{ t }}</span>
        </div>
        <div v-for="rv in reviews" :key="rv.id" class="rv-item">
          <div class="rv-top">
            <span class="rv-nick">{{ rv.memberNickname || '匿名用户' }}</span>
            <van-rate :model-value="rv.rating" readonly size="10" color="#ffb400" />
          </div>
          <div class="rv-content">{{ rv.content || '此用户没有填写评价' }}</div>
          <!-- V15 追评：原评之后展示，标注「追评」 -->
          <div v-if="rv.appendContent" class="rv-append">
            <span class="rv-append-badge">追评</span>
            <span class="rv-append-text">{{ rv.appendContent }}</span>
            <span v-if="rv.appendTime" class="rv-append-time">{{ rv.appendTime }}</span>
          </div>
          <div v-if="rvImgList(rv.images).length" class="rv-imgs">
            <van-image
              v-for="(img, i) in rvImgList(rv.images)"
              :key="i"
              :src="thumb(img, 120)"
              fit="cover"
              class="rv-img"
              radius="4"
              @click="previewImg(rvImgList(rv.images), i)"
            />
          </div>
          <div v-if="rvTagList(rv.tags).length" class="rv-tags rv-tags--row">
            <span v-for="t in rvTagList(rv.tags)" :key="t" class="rv-tag mini">{{ t }}</span>
          </div>
          <div class="rv-foot">
            <span v-if="rv.reply" class="rv-reply">商家回复：{{ rv.reply }}</span>
            <!-- V15 点有用：登录后可点，同一会员幂等 -->
            <span class="rv-useful" :class="{ on: likedMap[rv.id] }" @click="onUseful(rv)">
              <van-icon :name="likedMap[rv.id] ? 'good-job' : 'good-job-o'" />
              有用 {{ rv.usefulCount || 0 }}
            </span>
          </div>
        </div>
      </div>

      <!-- 富文本详情 -->
      <div class="card">
        <div class="detail-title">商品详情</div>
        <!-- eslint-disable-next-line vue/no-v-html -->
        <div class="goods-detail-rich" v-html="goods.detail || '<p class=\'text-gray\'>暂无详情</p>'"></div>
      </div>

      <!-- 底部操作栏 -->
      <van-action-bar safe-area-inset-bottom>
        <van-action-bar-icon
          :icon="favorited ? 'like' : 'like-o'"
          :color="favorited ? '#e1251b' : undefined"
          text="收藏"
          @click="onToggleFavorite"
        />
        <van-action-bar-icon icon="cart-o" text="购物车" @click="router.push('/cart')" />
        <van-action-bar-button color="#ff976a" text="加入购物车" @click="openSku('cart')" />
        <van-action-bar-button color="#4a6cf7" text="立即购买" @click="openSku('buy')" />
      </van-action-bar>

      <!-- SKU 选择弹层：每个 SKU 一行（规格文本+价格+库存），单选 -->
      <van-popup v-model:show="showSku" position="bottom" round>
        <div class="sku-sheet">
          <div class="sku-head">
            <van-image :src="(selectedSku && selectedSku.image) || goods.mainImage" fit="cover" class="sku-img" />
            <div class="sku-head-mid">
              <div class="price">
                <span class="symbol">¥</span>{{ fenToYuan(selectedSku ? selectedSku.price : goods.priceMin) }}
              </div>
              <div class="text-gray sku-meta">库存 {{ selectedSku ? selectedSku.stock : '-' }} 件</div>
              <div class="text-gray sku-meta">{{ selectedSku ? '已选：' + specText(selectedSku.specJson) : '请选择规格' }}</div>
            </div>
          </div>

          <div class="sku-list">
            <div
              v-for="s in goods.skus || []"
              :key="s.id"
              class="sku-row"
              :class="{ on: selectedSkuId === s.id, off: !s.stock }"
              @click="selectSku(s)"
            >
              <span class="sku-spec">{{ specText(s.specJson) }}</span>
              <span class="price sku-price"><span class="symbol">¥</span>{{ fenToYuan(s.price) }}</span>
              <span class="sku-stock text-gray">{{ s.stock ? '库存' + s.stock : '无货' }}</span>
            </div>
          </div>

          <div class="num-row">
            <span>购买数量</span>
            <van-stepper v-model="num" min="1" :max="selectedSku ? Math.max(selectedSku.stock, 1) : 1" :disabled="!selectedSku" />
          </div>

          <van-button block round color="#4a6cf7" :loading="acting" :disabled="!selectedSku || !selectedSku.stock" @click="onSkuConfirm">
            确 定
          </van-button>
        </div>
      </van-popup>
    </template>
    <van-empty v-else-if="loaded" description="商品不存在或已下架" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showImagePreview, showToast } from 'vant'
import { getGoodsDetail } from '@/api/shop'
import { addCart } from '@/api/cart'
import { recordFootprint } from '@/api/footprint'
import { getGoodsReviews, toggleReviewUseful } from '@/api/review'
import { checkFavorite, toggleFavorite } from '@/api/favorite'
import { getGoodsPromotions, promoPrice } from '@/api/promotion'
import { useUserStore } from '@/stores/user'
import { fenToYuan, specText, thumb } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const goodsId = route.params.id

const goods = ref(null)
const loaded = ref(false)

// V7：评价区与收藏态
const reviews = ref([])
const reviewTotal = ref(0)
const reviewAvg = ref(0)
const favorited = ref(false)

const rvImgList = images => (images ? String(images).split(',').filter(Boolean) : [])
const rvTagList = tags => (tags ? String(tags).split(',').filter(Boolean) : [])
const previewImg = (images, i) => showImagePreview({ images, startPosition: i })

// V15 评价增强：标签云筛选 / 点有用
const reviewTags = ref([])
const activeTag = ref('')
const likedMap = ref({})

const loadReviews = tag => {
  getGoodsReviews(goodsId, { pageNum: 1, pageSize: 10, ...(tag ? { tag } : {}) })
    .then(d => {
      reviews.value = d.records || []
      reviewTotal.value = d.total || 0
      reviewAvg.value = d.avg || 0
      // 标签云：从当前评价里聚合（去重），仅无筛选态刷新
      if (!tag) {
        const set = new Set()
        reviews.value.forEach(rv => rvTagList(rv.tags).forEach(t => set.add(t)))
        reviewTags.value = Array.from(set)
      }
    })
    .catch(() => {})
}

const onTagClick = t => {
  activeTag.value = activeTag.value === t ? '' : t
  loadReviews(activeTag.value)
}

const onUseful = rv => {
  if (!user.isLogin) {
    showToast('请先登录')
    return
  }
  const liked = !likedMap.value[rv.id]
  toggleReviewUseful({ reviewId: rv.id, liked })
    .then(d => {
      rv.usefulCount = d.usefulCount
      likedMap.value = { ...likedMap.value, [rv.id]: d.liked }
    })
    .catch(() => {}) // 拦截器已提示（如未登录/评价不存在）
}

const onToggleFavorite = async () => {
  if (!user.isLogin) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const data = await toggleFavorite(goodsId)
  favorited.value = !!data.favorited
  showToast(favorited.value ? '已收藏' : '已取消收藏')
}

// SKU 弹层状态：mode = cart 加购 / buy 立即购买
const showSku = ref(false)
const mode = ref('cart')
const selectedSkuId = ref(null)
const num = ref(1)
const acting = ref(false)

const selectedSku = computed(() => (goods.value?.skus || []).find(s => s.id === selectedSkuId.value) || null)

const heroImages = computed(() => {
  if (!goods.value) return []
  return goods.value.sliderImages && goods.value.sliderImages.length
    ? goods.value.sliderImages
    : [goods.value.mainImage].filter(Boolean)
})

// 促销（V15）：可用活动 + 限时折扣活动价 + 倒计时
// 倒计时用服务端返回的相对秒数（countdownSeconds），本地只做递减，
// 不解析 "yyyy-MM-dd HH:mm:ss"（Safari/旧 WebView new Date 会 Invalid Date）
const promos = ref([])
const nowTick = ref(0)
let cdTimer = null
const promoPriceFen = computed(() => {
  const p = promos.value.find(x => Number(x.type) === 3 && Number(x.discountRate) < 100)
  if (!p) return null
  return promoPrice(goods.value?.priceMin, p.discountRate)
})
const countdownText = seconds => {
  nowTick.value // 依赖 tick 驱动刷新
  const left = Math.max(0, Math.floor(Number(seconds) - nowTick.value))
  const h = String(Math.floor(left / 3600)).padStart(2, '0')
  const m = String(Math.floor((left % 3600) / 60)).padStart(2, '0')
  const s = String(left % 60).padStart(2, '0')
  return `${h}:${m}:${s}`
}

const priceText = computed(() => {
  if (!goods.value) return ''
  const min = fenToYuan(goods.value.priceMin)
  const max = fenToYuan(goods.value.priceMax)
  return min === max ? min : `${min} ~ ${max}`
})

const openSku = m => {
  mode.value = m
  showSku.value = true
}

const selectSku = s => {
  if (!s.stock) return
  selectedSkuId.value = s.id
  if (num.value > s.stock) num.value = s.stock
}

const onSkuConfirm = async () => {
  if (!selectedSku.value) return showToast('请选择规格')
  acting.value = true
  try {
    if (mode.value === 'cart') {
      await addCart({ skuId: selectedSku.value.id, num: num.value })
      showToast('已加入购物车')
    } else {
      router.push({ path: '/order/confirm', query: { skuId: selectedSku.value.id, num: num.value } })
    }
    showSku.value = false
  } finally {
    acting.value = false
  }
}

onMounted(async () => {
  goods.value = await getGoodsDetail(goodsId)
  loaded.value = true
  // 默认选中第一个有货 SKU
  const first = (goods.value?.skus || []).find(s => s.stock > 0)
  if (first) selectedSkuId.value = first.id
  // V15：促销活动（匿名可读，失败静默不展示）
  getGoodsPromotions(goodsId).then(list => {
    promos.value = list || []
    if (promos.value.some(p => Number(p.type) === 3 && Number(p.countdownSeconds) > 0)) {
      cdTimer = setInterval(() => { nowTick.value += 1 }, 1000)
    }
  })
  // V4 P2-2：登录态下记录一次浏览（未登录/失败静默，不影响浏览）
  if (user.isLogin) {
    recordFootprint(goodsId).catch(() => {})
    // V7：收藏态（未登录不查，失败静默）
    checkFavorite(goodsId).then(d => { favorited.value = !!d.favorited }).catch(() => {})
  }
  // V7：评价区（匿名可读，失败静默）；V15 支持按标签筛选
  loadReviews('')
})
onUnmounted(() => { if (cdTimer) clearInterval(cdTimer) })
</script>

<style scoped>
.hero {
  background: #fff;
}

.hero-img {
  width: 100%;
  height: 320px;
}

.card {
  background: #fff;
  padding: 12px;
  margin-bottom: 8px;
}

.price-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.price-main {
  font-size: 20px;
}

.sales {
  font-size: 12px;
}

.g-name {
  margin-top: 8px;
  font-size: 15px;
  font-weight: 600;
  line-height: 21px;
}

.g-sub {
  margin-top: 4px;
  font-size: 12px;
}

.origin {
  margin-left: 4px;
  color: #969799;
  font-size: 13px;
  text-decoration: line-through;
  font-weight: 400;
}

.promo-card {
  padding: 10px 12px;
}

.promo-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 12px;
}

.promo-tag {
  flex: none;
}

.promo-name {
  max-width: 40%;
  color: #323233;
}

.promo-rule {
  color: #ee0a24;
}

.promo-cd {
  margin-left: auto;
  color: #969799;
}

.promo-cd b {
  color: #ee0a24;
  font-family: monospace;
}

.cell-card {
  padding: 12px;
}

.cell-val {
  margin-left: 12px;
}

.shop-ico {
  color: #ee9a2b;
  margin-right: 6px;
}

.detail-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.rv-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.rv-head .detail-title {
  margin-bottom: 0;
}

.rv-avg {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #ffb400;
}

.rv-item {
  padding: 10px 0;
  border-top: 1px solid #f2f3f5;
}

.rv-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rv-nick {
  font-size: 12px;
  color: #646566;
}

.rv-content {
  margin-top: 6px;
  font-size: 13px;
  color: #323233;
  line-height: 1.5;
}

.rv-imgs {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.rv-img {
  width: 72px;
  height: 72px;
}

.rv-reply {
  margin-top: 8px;
  background: #f7f8fa;
  border-radius: 4px;
  padding: 8px 10px;
  font-size: 12px;
  color: #646566;
}

/* V15：标签云 / 追评 / 有用 */
.rv-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0;
}

.rv-tag {
  padding: 3px 10px;
  border-radius: 20px;
  background: #f2f3f5;
  color: #646566;
  font-size: 11px;
  cursor: pointer;
}

.rv-tag.on {
  background: #eef3ff;
  color: #4a6cf7;
}

.rv-tag.mini {
  padding: 1px 8px;
  cursor: default;
}

.rv-append {
  margin-top: 8px;
  padding: 8px 10px;
  background: #fff7ee;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
}

.rv-append-badge {
  display: inline-block;
  margin-right: 6px;
  padding: 0 6px;
  border-radius: 3px;
  background: #ff976a;
  color: #fff;
  font-size: 10px;
  line-height: 18px;
}

.rv-append-text {
  color: #b88230;
}

.rv-append-time {
  margin-left: 8px;
  color: #c8c9cc;
  font-size: 11px;
}

.rv-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.rv-useful {
  flex-shrink: 0;
  margin-top: 8px;
  font-size: 12px;
  color: #969799;
  cursor: pointer;
}

.rv-useful.on {
  color: #4a6cf7;
}

.sku-sheet {
  padding: 16px 16px 24px;
}

.sku-head {
  display: flex;
  gap: 10px;
}

.sku-img {
  width: 72px;
  height: 72px;
  border-radius: 6px;
  overflow: hidden;
  flex: none;
}

.sku-head-mid {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 3px;
  font-size: 12px;
}

.sku-list {
  margin-top: 16px;
  max-height: 260px;
  overflow-y: auto;
}

.sku-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #f2f3f5;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 13px;
}

.sku-row.on {
  background: #e8edff;
  border-color: #4a6cf7;
  color: #4a6cf7;
}

.sku-row.off {
  opacity: 0.45;
}

.sku-spec {
  flex: 1;
}

.sku-price {
  font-size: 13px;
}

.sku-stock {
  font-size: 11px;
}

.num-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0;
  font-size: 13px;
  color: #646566;
}
</style>
