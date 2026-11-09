<template>
  <div>
    <div class="breadcrumb">
      <a @click="$router.push('/')">首页</a> /
      <a v-if="goods.categoryId" @click="$router.push({ path: '/list', query: { categoryId: goods.categoryId } })">
        {{ goods.categoryName || '商品' }}
      </a>
      <span v-if="goods.name"> / {{ goods.name }}</span>
    </div>

    <div v-if="goods.id" class="detail-top">
      <div class="detail-gallery">
        <pc-image :src="currentImage" :img-style="{ width: '420px', height: '420px', objectFit: 'cover', borderRadius: '8px', display: 'block' }" />
        <div class="detail-thumbs">
          <pc-image
            v-for="(img, i) in galleryImages"
            :key="i"
            :src="img"
            :img-style="{ width: '74px', height: '74px', objectFit: 'cover', borderRadius: '6px', cursor: 'pointer', border: i === currentIdx ? '2px solid #e1251b' : '2px solid transparent' }"
            @click="currentIdx = i"
          />
        </div>
      </div>

      <div class="detail-info">
        <h1 class="detail-name">{{ goods.name }}</h1>
        <p class="detail-sub">{{ goods.subTitle || goods.brandName || '' }}</p>
        <div class="detail-price-box">
          <span class="detail-price-label">{{ goods.priceMin !== goods.priceMax ? '价格区间' : '价格' }}</span>
          <!-- 限时折扣：活动价 + 划线原价 -->
          <template v-if="promoPriceFen !== null">
            <span class="price detail-price"><span class="yen">¥</span>{{ $price(promoPriceFen) }}</span>
            <span class="detail-price-origin">¥{{ goods.priceMin !== goods.priceMax ? $price(goods.priceMin) + ' 起' : $price(goods.priceMin) }}</span>
          </template>
          <span v-else-if="goods.priceMin !== goods.priceMax" class="price-range detail-price">
            ¥{{ $price(goods.priceMin) }} - {{ $price(goods.priceMax) }}
          </span>
          <span v-else class="price detail-price"><span class="yen">¥</span>{{ $price(goods.priceMin) }}</span>
        </div>

        <!-- 促销活动（V15）：活动标签 + 限时折扣倒计时（服务端相对秒数递减，避免时间格式解析兼容问题） -->
        <div v-if="promos.length" class="detail-promo">
          <div v-for="p in promos" :key="p.id" class="detail-promo-row">
            <el-tag type="danger" size="mini">{{ p.typeText }}</el-tag>
            <span class="detail-promo-name">{{ p.name }}</span>
            <span class="detail-promo-rule">{{ p.ruleText }}</span>
            <span v-if="Number(p.type) === 3 && Number(p.countdownSeconds) > 0" class="detail-promo-cd">
              距结束 <b>{{ countdownText(p.countdownSeconds - tick) }}</b>
            </span>
          </div>
        </div>
        <div class="detail-meta">
          <span>销量 {{ goods.sales || 0 }}</span>
          <span>品牌 {{ goods.brandName || '—' }}</span>
          <span>分类 {{ goods.categoryName || '—' }}</span>
          <span>商品编号 {{ goods.goodsNo || '—' }}</span>
        </div>

        <!-- V10 所属店铺：多商家平台下买家关心「谁发货、谁售后」 -->
        <div v-if="goods.merchantName || goods.merchantId" class="detail-shop">
          <i class="el-icon-s-shop" style="color:#e6a23c;font-size:18px" />
          <span class="detail-shop-label">店铺</span>
          <span class="detail-shop-name" @click="goShop">{{ goods.merchantName || ('店铺 #' + goods.merchantId) }}</span>
          <span class="detail-shop-enter" @click="goShop">进店逛逛 ›</span>
        </div>

        <!-- 规格选择：点选一个 SKU 后加购/立即购买（id 是雪花字符串，不做数值转换） -->
        <div v-if="skus.length" class="detail-sku-box">
          <div class="detail-sku-title">选择规格 <span class="sku-tip">{{ selectedSku ? specText(selectedSku) : '请选择一个规格' }}</span></div>
          <div class="sku-options">
            <div
              v-for="s in skus"
              :key="s.id"
              class="sku-option"
              :class="{ on: selectedSkuId === s.id, off: !s.stock }"
              @click="selectSku(s)"
            >
              <div class="sku-option-spec">{{ specText(s) }}</div>
              <div class="sku-option-price">¥{{ $price(s.price) }}</div>
              <div class="sku-option-stock">{{ s.stock > 0 ? '库存 ' + s.stock : '缺货' }}</div>
            </div>
          </div>

          <div class="sku-buy-row">
            <span class="sku-buy-label">数量</span>
            <el-input-number
              v-model="num"
              size="small"
              :min="1"
              :max="selectedSku ? Math.max(selectedSku.stock, 1) : 1"
              :disabled="!selectedSku"
            />
            <span class="sku-buy-total">
              合计 <em>¥{{ $price(selectedSku ? selectedSku.price * num : 0) }}</em>
            </span>
          </div>

          <div class="sku-buy-actions">
            <el-button
              type="warning"
              icon="el-icon-shopping-cart-2"
              :loading="adding"
              :disabled="!selectedSku || !selectedSku.stock"
              @click="onAddCart"
            >加入购物车</el-button>
            <el-button
              type="danger"
              :loading="buying"
              :disabled="!selectedSku || !selectedSku.stock"
              @click="onBuyNow"
            >立即购买</el-button>
            <!-- V7：收藏切换（未登录先登录） -->
            <el-button
              :icon="favorited ? 'el-icon-star-on' : 'el-icon-star-off'"
              :type="favorited ? 'danger' : 'default'"
              plain
              @click="onToggleFavorite"
            >{{ favorited ? '已收藏' : '收藏' }}</el-button>
            <span v-if="!selectedSku" class="sku-warn">请先选择规格</span>
          </div>
        </div>
        <div v-else-if="loaded" class="detail-sku-box">
          <div class="detail-sku-title">该商品暂无规格，暂不支持下单</div>
        </div>
      </div>
    </div>
    <div v-else-if="loaded && !goods.id" class="list-empty">
      <el-empty description="商品不存在或已下架" />
    </div>

    <div v-if="goods.id" class="detail-rich">
      <div class="detail-rich-title">商品详情</div>
      <div v-if="goods.detail" class="rich-content" v-html="goods.detail" />
      <div v-else class="rich-content" style="color:#909399">
        该商品暂无图文详情
        <template v-if="(goods.sliderImages || []).length">
          ，以下是商品图集：
          <div style="margin-top:14px">
            <pc-image
              v-for="(img, i) in goods.sliderImages"
              :key="'d' + i"
              :src="img"
              :img-style="{ maxWidth: '100%', display: 'block', margin: '10px 0', borderRadius: '8px' }"
            />
          </div>
        </template>
      </div>
    </div>

    <!-- 商品评价（V7）：均分 + 列表（匿名可读），失败时不展示，不影响下单 -->
    <div v-if="goods.id" class="detail-rich">
      <div class="detail-rich-title">
        商品评价
        <span style="margin-left:10px;font-size:13px;color:#e1251b">
          {{ reviewTotal ? reviewAvg + ' 分 · 共 ' + reviewTotal + ' 条' : '暂无评价' }}
        </span>
      </div>
      <div v-if="reviewTags.length" class="rv-tag-cloud">
        <span class="rv-tag-cloud-label">按标签筛选：</span>
        <el-tag
          v-for="t in reviewTags"
          :key="t"
          size="small"
          :effect="activeTag === t ? 'dark' : 'plain'"
          class="rv-tag-cloud-item"
          @click="onTagClick(t)"
        >{{ t }}</el-tag>
      </div>
      <div v-for="rv in reviews" :key="rv.id" class="rv-item">
        <div class="rv-top">
          <span class="rv-nick">{{ rv.memberNickname || '匿名用户' }}</span>
          <el-rate :value="rv.rating" disabled />
          <span class="rv-time">{{ shortTime(rv.createTime) }}</span>
        </div>
        <div class="rv-content">{{ rv.content || '此用户没有填写评价' }}</div>
        <!-- V15 追评：标注「追评」 -->
        <div v-if="rv.appendContent" class="rv-append">
          <span class="rv-append-badge">追评</span>
          <span class="rv-append-text">{{ rv.appendContent }}</span>
          <span v-if="rv.appendTime" class="rv-append-time">{{ rv.appendTime }}</span>
        </div>
        <div v-if="rvImages(rv.images).length" class="rv-imgs">
          <el-image
            v-for="(img, i) in rvImages(rv.images)"
            :key="i"
            :src="thumbUrl(img)"
            :preview-src-list="rvImages(rv.images).map(thumbUrl)"
            fit="cover"
            class="rv-img"
          />
        </div>
        <div v-if="rvTags(rv.tags).length" class="rv-tag-row">
          <el-tag v-for="t in rvTags(rv.tags)" :key="t" size="mini" type="success" effect="plain">{{ t }}</el-tag>
        </div>
        <div class="rv-foot">
          <span v-if="rv.reply" class="rv-reply">商家回复：{{ rv.reply }}</span>
          <!-- V15 点有用：登录后可点，同一会员幂等 -->
          <span class="rv-useful" :class="{ on: likedMap[rv.id] }" @click="onUseful(rv)">
            <i class="el-icon-thumb" />
            有用（{{ rv.usefulCount || 0 }}）
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getGoodsDetail } from '@/api/shop'
import { addCart } from '@/api/cart'
import { recordFootprint } from '@/api/footprint'
import { getGoodsReviews, toggleReviewUseful } from '@/api/review'
import { checkFavorite, toggleFavorite } from '@/api/favorite'
import { getGoodsPromotions, promoPrice, countdownText as formatCountdown } from '@/api/promotion'
// 与 methods.specText 同名会误读，导入时改名
import { specText as formatSpec, thumb, shortTime as formatTime } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'
import store from '@/store'

export default {
  name: 'GoodsDetail',
  components: { PcImage },
  data() {
    return {
      goods: {},
      currentIdx: 0,
      loaded: false,
      selectedSkuId: null,
      num: 1,
      adding: false,
      buying: false,
      // V7：评价区 + 收藏态
      reviews: [],
      reviewTotal: 0,
      reviewAvg: 0,
      // V15：标签云筛选 + 有用态
      reviewTags: [],
      activeTag: '',
      likedMap: {},
      favorited: false,
      // V15 促销：可用活动 + 倒计时 tick（每秒 +1）
      promos: [],
      tick: 0
    }
  },
  computed: {
    promoPriceFen() {
      const p = this.promos.find(x => Number(x.type) === 3 && Number(x.discountRate) < 100)
      if (!p || !this.goods.id) return null
      return promoPrice(this.goods.priceMin, p.discountRate)
    },
    skus() {
      return this.goods.skus || []
    },
    selectedSku() {
      return this.skus.find(s => s.id === this.selectedSkuId) || null
    },
    galleryImages() {
      const list = []
      if (this.goods.mainImage) list.push(this.goods.mainImage)
      for (const img of this.goods.sliderImages || []) {
        if (img && !list.includes(img)) list.push(img)
      }
      return list
    },
    currentImage() {
      return this.galleryImages[this.currentIdx] || this.goods.mainImage
    }
  },
  created() {
    this.load()
  },
  beforeDestroy() {
    if (this.cdTimer) clearInterval(this.cdTimer)
  },
  methods: {
    countdownText(seconds) {
      return formatCountdown(seconds)
    },
    load() {
      this.loaded = false
      this.currentIdx = 0
      getGoodsDetail(this.$route.params.id).then(res => {
        this.goods = res || {}
        this.loaded = true
        // V15：促销活动（匿名可读，失败静默不展示）
        getGoodsPromotions(res.id).then(list => {
          this.promos = list || []
          if (this.promos.some(p => Number(p.type) === 3 && Number(p.countdownSeconds) > 0)) {
            this.cdTimer = setInterval(() => { this.tick += 1 }, 1000)
          }
        }).catch(() => {})
        // V4 P2-2：登录态下记录浏览（未登录/失败静默）
        if (store.isLogin) recordFootprint(res.id).catch(() => {})
        this.loadReviewCut(res.id)
      })
    },
    /** V7 评价区（匿名可读，失败静默不影响下单）；V15 支持按标签筛选 */
    loadReviewCut(goodsId, tag) {
      getGoodsReviews(goodsId, { pageNum: 1, pageSize: 10, ...(tag ? { tag } : {}) })
        .then(data => {
          this.reviews = (data && data.records) || []
          this.reviewTotal = (data && data.total) || 0
          this.reviewAvg = (data && data.avg) || 0
          // 标签云：从当前评价里聚合（去重），仅无筛选态刷新
          if (!tag) {
            const set = new Set()
            this.reviews.forEach(rv => this.rvTags(rv.tags).forEach(t => set.add(t)))
            this.reviewTags = Array.from(set)
          }
        })
        .catch(() => {})
      // 收藏态仅登录态查询
      if (store.isLogin) {
        checkFavorite(goodsId).then(d => { this.favorited = !!d.favorited }).catch(() => {})
      }
    },
    rvTags(tags) {
      return tags ? String(tags).split(',').filter(Boolean) : []
    },
    onTagClick(t) {
      this.activeTag = this.activeTag === t ? '' : t
      this.loadReviewCut(this.$route.params.id, this.activeTag)
    },
    onUseful(rv) {
      if (!store.isLogin) {
        this.$message.info('请先登录')
        return
      }
      const liked = !this.likedMap[rv.id]
      toggleReviewUseful({ reviewId: rv.id, liked })
        .then(d => {
          rv.usefulCount = d.usefulCount
          this.likedMap = { ...this.likedMap, [rv.id]: d.liked }
        })
        .catch(() => {}) // 拦截器已提示
    },
    rvImages(images) {
      return images ? String(images).split(',').filter(Boolean) : []
    },
    thumbUrl(src) {
      return thumb(src, 200)
    },
    shortTime(v) {
      return formatTime(v)
    },
    async onToggleFavorite() {
      if (!this.ensureLogin()) return
      const data = await toggleFavorite(this.goods.id)
      this.favorited = !!(data && data.favorited)
      this.$message.success(this.favorited ? '已收藏' : '已取消收藏')
    },
    specText(sku) {
      return formatSpec(sku.specJson) || sku.skuCode
    },
    goShop() {
      const id = this.goods.merchantId
      if (!id) return
      this.$router.push(`/merchant/${id}`)
    },
    selectSku(s) {
      this.selectedSkuId = s.id
      // 换规格时数量收敛到新库存内，避免带着超库存的数字去加购
      if (this.num > s.stock) this.num = Math.max(s.stock, 1)
    },
    // 加购/下单都要求会员身份：未登录先去登录，登录后回到当前商品页
    ensureLogin() {
      if (store.isLogin) return true
      this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } })
      return false
    },
    async onAddCart() {
      if (!this.ensureLogin() || !this.selectedSku) return
      this.adding = true
      try {
        await addCart({ skuId: this.selectedSku.id, num: this.num })
        this.$message.success('已加入购物车')
        store.refreshCartCount()
      } finally {
        this.adding = false
      }
    },
    onBuyNow() {
      if (!this.ensureLogin() || !this.selectedSku) return
      // 雪花 id 原样透传（Number() 会丢精度，后端查不到规格）
      this.$router.push({ path: '/order/confirm', query: { skuId: this.selectedSku.id, num: this.num } })
    }
  }
}
</script>

<style scoped>
.detail-price-origin {
  margin-left: 10px;
  color: #c0c4cc;
  font-size: 14px;
  text-decoration: line-through;
}
.detail-promo {
  margin-top: 10px;
  padding: 8px 12px;
  background: #fff5f2;
  border: 1px solid #ffe0d6;
  border-radius: 6px;
}
.detail-promo-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
  font-size: 13px;
}
.detail-promo-name {
  color: #303133;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-promo-rule {
  color: #e1251b;
}
.detail-promo-cd {
  margin-left: auto;
  color: #909399;
}
.detail-promo-cd b {
  color: #e1251b;
  font-family: monospace;
}
.rv-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}
.rv-item:last-child {
  border-bottom: none;
}
.rv-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.rv-nick {
  font-size: 13px;
  color: #606266;
}
.rv-time {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}
.rv-content {
  margin-top: 8px;
  font-size: 14px;
  color: #303133;
  line-height: 1.6;
}
.rv-imgs {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.rv-img {
  width: 96px;
  height: 96px;
  border-radius: 6px;
}
.rv-reply {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f7f8fa;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
}
/* V15：追评 / 标签云 / 有用 */
.rv-append {
  margin-top: 10px;
  padding: 8px 12px;
  background: #fdf6ec;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
}
.rv-append-badge {
  display: inline-block;
  margin-right: 8px;
  padding: 0 8px;
  border-radius: 3px;
  background: #ff976a;
  color: #fff;
  font-size: 12px;
  line-height: 20px;
}
.rv-append-text {
  color: #b88230;
}
.rv-append-time {
  margin-left: 10px;
  color: #c0c4cc;
  font-size: 12px;
}
.rv-tag-row {
  margin-top: 8px;
}
.rv-tag-row .el-tag {
  margin-right: 6px;
}
.rv-tag-cloud {
  margin: 4px 0 12px;
}
.rv-tag-cloud-label {
  font-size: 13px;
  color: #909399;
  margin-right: 6px;
}
.rv-tag-cloud-item {
  margin-right: 8px;
  cursor: pointer;
}
.rv-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.rv-useful {
  flex-shrink: 0;
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
  cursor: pointer;
}
.rv-useful.on {
  color: #4a6cf7;
}
</style>
