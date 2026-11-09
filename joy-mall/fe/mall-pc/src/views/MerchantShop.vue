<template>
  <div v-loading="loading">
    <!-- 店铺头 -->
    <div v-if="shop.id" class="shop-hero">
      <div class="shop-hero-logo">
        <pc-image
          v-if="shop.logo"
          :src="shop.logo"
          :img-style="{ width: '76px', height: '76px', objectFit: 'cover', borderRadius: '12px', display: 'block' }"
        />
        <span v-else class="shop-hero-logo-fallback">{{ (shop.name || '?').slice(0, 1) }}</span>
      </div>
      <div class="shop-hero-main">
        <div class="shop-hero-name">
          {{ shop.name }}
          <el-tag size="mini" effect="plain" style="margin-left:10px">商家编号 {{ shop.merchantNo }}</el-tag>
        </div>
        <div class="shop-hero-intro">{{ shop.intro || '这家店铺还没有填写简介' }}</div>
        <div class="shop-hero-meta">
          <span>经营类目：{{ shop.category || '—' }}</span>
          <span>在售商品：{{ total }} 件</span>
        </div>
      </div>
      <el-button size="small" @click="$router.push('/')">回到首页</el-button>
    </div>

    <!-- 停业 / 不存在 -->
    <div v-else-if="loaded" class="page-card">
      <el-empty :description="errorText || '店铺不存在或已停业'">
        <el-button type="danger" size="small" @click="$router.push('/')">去首页逛逛</el-button>
      </el-empty>
    </div>

    <!-- 店内商品 -->
    <template v-if="shop.id">
      <div class="section">
        <div class="section-head">
          <span class="section-title">店内商品</span>
          <span class="muted">共 {{ total }} 件</span>
        </div>
        <div v-if="goods.length" class="goods-grid">
          <div v-for="g in goods" :key="g.id" class="goods-card" @click="$router.push(`/goods/${g.id}`)">
            <pc-image :src="thumb(g.mainImage, 300)" :img-style="{ width: '100%', height: '190px', objectFit: 'cover', display: 'block' }" />
            <div class="goods-card-body">
              <div class="goods-card-name">{{ g.name }}</div>
              <div class="goods-card-sub">{{ g.subTitle || g.brandName || '' }}</div>
              <div class="goods-card-price">
                <span v-if="g.priceMin !== g.priceMax" class="price-range">¥{{ $price(g.priceMin) }} - {{ $price(g.priceMax) }}</span>
                <span v-else class="price"><span class="yen">¥</span><span class="num">{{ $price(g.priceMin) }}</span></span>
                <span class="goods-card-sales">已售 {{ g.sales || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else-if="loaded" description="这家店铺还没有上架商品" />
      </div>

      <div v-if="total > pageSize" style="text-align:center;margin-top:18px">
        <el-pagination
          background
          layout="prev, pager, next, total"
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          @current-change="loadGoods"
        />
      </div>
    </template>
  </div>
</template>

<script>
import { getMerchantDetail, getGoodsPage } from '@/api/shop'
import { thumb } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'

export default {
  name: 'MerchantShop',
  components: { PcImage },
  data() {
    return {
      shop: {},
      goods: [],
      total: 0,
      pageNum: 1,
      pageSize: 12,
      loading: false,
      loaded: false,
      errorText: ''
    }
  },
  watch: {
    // 店铺间跳转复用同一组件，靠 id 变化重载
    '$route.params.id'() {
      this.reset()
    }
  },
  created() {
    this.reset()
  },
  methods: {
    thumb,
    reset() {
      this.shop = {}
      this.goods = []
      this.total = 0
      this.pageNum = 1
      this.loaded = false
      this.errorText = ''
      this.loadShop()
    },
    async loadShop() {
      const id = this.$route.params.id
      if (!id) {
        this.loaded = true
        this.errorText = '缺少店铺参数'
        return
      }
      this.loading = true
      try {
        this.shop = (await getMerchantDetail(id)) || {}
        await this.loadGoods()
      } catch (e) {
        // 停用/不存在时后端 400，错误文案由 request 拦截器提示，这里只负责空态
        this.shop = {}
        this.errorText = (e && e.message) || '店铺不存在或已停业'
      } finally {
        this.loading = false
        this.loaded = true
      }
    },
    async loadGoods() {
      const res = await getGoodsPage({
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        merchantId: this.$route.params.id
      })
      this.goods = (res && res.records) || []
      this.total = (res && Number(res.total)) || 0
    }
  }
}
</script>

<style scoped>
.shop-hero {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 24px;
  margin-bottom: 18px;
  border-radius: 12px;
  background: linear-gradient(135deg, #fff5f4 0%, #ffffff 60%);
  border: 1px solid #ffe4e0;
}

.shop-hero-logo {
  flex: none;
}

.shop-hero-logo-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 76px;
  height: 76px;
  border-radius: 12px;
  background: #fdeceb;
  color: #e1251b;
  font-size: 30px;
  font-weight: 700;
}

.shop-hero-main {
  flex: 1;
  min-width: 0;
}

.shop-hero-name {
  font-size: 22px;
  font-weight: 700;
  color: #1f2430;
}

.shop-hero-intro {
  margin-top: 6px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
}

.shop-hero-meta {
  margin-top: 8px;
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: #98a1b3;
}
</style>
