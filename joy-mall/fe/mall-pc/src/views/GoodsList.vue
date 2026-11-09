<template>
  <div class="list-layout">
    <!-- 左侧分类 -->
    <aside class="list-side">
      <div class="list-side-card">
        <div class="side-group-title">全部分类</div>
        <a class="side-item" :class="{ active: !activeCategory }" @click="pickCategory(null)">全部商品</a>
        <template v-for="c in categories" :key="'t' + c.id">
          <a class="side-item" :class="{ active: isActive(c) }" @click="pickCategory(c)">{{ c.name }}</a>
          <a
            v-for="child in c.children || []"
            :key="'c' + child.id"
            class="side-item side-child"
            :class="{ active: isActive(child) }"
            @click="pickCategory(child)"
          >{{ child.name }}</a>
        </template>
      </div>
    </aside>

    <!-- 右侧商品区 -->
    <section class="list-body">
      <div class="sort-bar">
        <a class="sort-item" :class="{ active: !sort }" @click="pickSort('')">综合</a>
        <a class="sort-item" :class="{ active: sort === 'sales' }" @click="pickSort('sales')">销量</a>
        <a class="sort-item" :class="{ active: sort === 'priceAsc' }" @click="pickSort('priceAsc')">价格 ↑</a>
        <a class="sort-item" :class="{ active: sort === 'priceDesc' }" @click="pickSort('priceDesc')">价格 ↓</a>
        <span v-if="keyword" style="font-size:13px;color:#909399;margin-left:auto">
          关键词「{{ keyword }}」共 {{ total }} 件
        </span>
        <span v-else style="font-size:13px;color:#909399;margin-left:auto">共 {{ total }} 件商品</span>
      </div>

      <div v-if="goods.length" class="list-grid">
        <div v-for="g in goods" :key="g.id" class="goods-card" @click="goDetail(g)">
          <div class="goods-card-imgwrap">
            <pc-image :src="thumb(g.mainImage, 300)" :img-style="{ width: '100%', height: '190px', objectFit: 'cover', display: 'block' }" />
            <span v-if="promoOf(g)" class="goods-card-promo">{{ promoOf(g).ruleText }}</span>
          </div>
          <div class="goods-card-body">
            <a v-if="g.merchantName" class="goods-card-shop" title="进入店铺" @click.stop="goShop(g)">
              <i class="el-icon-s-shop" />{{ g.merchantName }}
            </a>
            <div class="goods-card-name">{{ g.name }}</div>
            <div class="goods-card-sub">{{ g.subTitle || g.brandName || '' }}</div>
            <div class="goods-card-price">
              <!-- 限时折扣：活动价 + 划线原价 -->
              <template v-if="promoPriceOf(g) !== null">
                <span class="price"><span class="yen">¥</span><span class="num">{{ $price(promoPriceOf(g)) }}</span></span>
                <span class="goods-card-origin">¥{{ $price(g.priceMin) }}</span>
              </template>
              <span v-else-if="g.priceMin !== g.priceMax" class="price-range">¥{{ $price(g.priceMin) }} - {{ $price(g.priceMax) }}</span>
              <span v-else class="price"><span class="yen">¥</span><span class="num">{{ $price(g.priceMin) }}</span></span>
              <span class="goods-card-sales">已售 {{ g.sales || 0 }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="loaded" class="list-empty">
        <el-empty description="没有找到相关商品，换个关键词试试" />
      </div>

      <div class="pager-wrap">
        <el-pagination
          background
          layout="prev, pager, next, jumper, total"
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          @current-change="load"
        />
      </div>
    </section>
  </div>
</template>

<script>
import { getCategories, getGoodsPage } from '@/api/shop'
import { getGoodsPromotions, pickBadge, promoPrice } from '@/api/promotion'
import { thumb } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'

export default {
  name: 'GoodsList',
  components: { PcImage },
  data() {
    return {
      categories: [],
      goods: [],
      total: 0,
      pageNum: 1,
      pageSize: 12,
      sort: '',
      loaded: false,
      // V15 促销角标：goodsId → 可用活动（available 匿名可读，失败为空）
      promoMap: {}
    }
  },
  computed: {
    categoryId() {
      return this.$route.query.categoryId || null
    },
    keyword() {
      return this.$route.query.keyword || ''
    },
    activeCategory() {
      if (!this.categoryId) return null
      for (const c of this.categories) {
        if (String(c.id) === String(this.categoryId)) return c
        for (const child of c.children || []) {
          if (String(child.id) === String(this.categoryId)) return child
        }
      }
      return { id: this.categoryId }
    }
  },
  created() {
    getCategories().then(res => { this.categories = res || [] })
    this.load()
  },
  methods: {
    isActive(c) {
      return this.categoryId && String(c.id) === String(this.categoryId)
    },
    pickCategory(c) {
      this.pageNum = 1
      this.$router.push({
        path: '/list',
        query: { categoryId: c ? c.id : undefined, keyword: this.keyword || undefined }
      })
    },
    pickSort(s) {
      this.sort = s
      this.pageNum = 1
      this.load()
    },
    load() {
      this.loaded = false
      getGoodsPage({
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        categoryId: this.categoryId,
        keyword: this.keyword || undefined,
        sort: this.sort || undefined
      }).then(res => {
        this.goods = (res && res.records) || []
        this.total = (res && Number(res.total)) || 0
        this.loaded = true
        this.loadPromos()
      })
    },
    /** V15：逐商品拉可用活动（api 层有模块级缓存，翻页回看不重复请求） */
    loadPromos() {
      for (const g of this.goods) {
        const id = g.id
        getGoodsPromotions(id).then(list => {
          const badge = pickBadge(list)
          if (badge) this.promoMap[String(id)] = badge
        })
      }
    },
    promoOf(g) {
      return this.promoMap[String(g.id)] || null
    },
    promoPriceOf(g) {
      const p = this.promoOf(g)
      return p && Number(p.type) === 3 ? promoPrice(g.priceMin, p.discountRate) : null
    },
    goDetail(g) {
      this.$router.push(`/goods/${g.id}`)
    },
    goShop(g) {
      if (!g.merchantId) return
      this.$router.push(`/merchant/${g.merchantId}`)
    }
  }
}
</script>

<style scoped>
.goods-card-imgwrap {
  position: relative;
}
.goods-card-promo {
  position: absolute;
  top: 8px;
  left: 8px;
  max-width: calc(100% - 16px);
  padding: 1px 7px;
  border-radius: 4px 10px 10px 2px;
  background: linear-gradient(135deg, #ff6034, #e1251b);
  color: #fff;
  font-size: 12px;
  line-height: 20px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.goods-card-origin {
  color: #c0c4cc;
  font-size: 12px;
  text-decoration: line-through;
}
</style>
