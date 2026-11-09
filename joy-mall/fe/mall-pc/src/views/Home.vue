<template>
  <div class="home">
    <!-- 首屏：分类导航 + Banner + 活动位 -->
    <section class="home-hero">
      <aside class="hero-cats">
        <div class="hero-cats-head">
          <span>全部分类</span>
          <span class="hero-cats-en">CATEGORY</span>
        </div>
        <a
          v-for="c in categories"
          :key="c.id"
          class="hero-cat"
          @click="goCategory(c)"
        >
          <span class="hero-cat-ico">{{ categoryEmoji(c.name) }}</span>
          <span class="hero-cat-main">
            <b>{{ c.name }}</b>
            <em>{{ subNames(c) }}</em>
          </span>
          <i class="el-icon-arrow-right"></i>
        </a>
        <div class="hero-hot">
          <div class="hero-hot-head">热门榜单</div>
          <div
            v-for="(g, i) in hotGoods.slice(0, 3)"
            :key="g.id"
            class="hero-hot-item"
            @click="goDetail(g)"
          >
            <span class="hero-hot-rank" :class="{ top: i === 0 }">{{ i + 1 }}</span>
            <span class="hero-hot-name">{{ g.name }}</span>
            <span class="hero-hot-price">¥{{ $price(g.priceMin) }}</span>
          </div>
        </div>
        <div class="hero-cats-foot">
          <span><i class="el-icon-circle-check"></i> 正品保障</span>
          <span><i class="el-icon-truck"></i> 极速配送</span>
          <span><i class="el-icon-refresh-left"></i> 无忧退换</span>
        </div>
      </aside>
      <div class="hero-center">
        <home-banner :banners="banners" @select="onBanner" />

        <div class="hero-quick">
          <div v-for="q in quickEntries" :key="q.path" class="quick-cell" @click="goTo(q.path)">
            <span class="quick-ico" :style="{ background: q.bg }">
              <i :class="q.icon"></i>
            </span>
            <span class="quick-label">{{ q.name }}</span>
          </div>
        </div>
      </div>

      <aside class="hero-side">
        <div class="side-promo side-promo--group" @click="goTo('/group')">
          <div class="side-promo-head">
            <span class="side-tag side-tag--group">超值拼团</span>
            <span class="side-more">更多 ›</span>
          </div>
          <div class="side-promo-title">邀好友 · 享团价</div>
          <div class="side-thumbs">
            <div v-for="g in groups.slice(0, 3)" :key="g.id" class="side-thumb">
              <pc-image
                :src="thumb(g.mainImage, 200)"
                :img-style="{ width: '100%', height: '62px', objectFit: 'cover', display: 'block' }"
              />
            </div>
          </div>
          <div class="side-promo-foot">2 人成团 · 低至 5 折</div>
        </div>

        <div class="side-promo side-promo--lottery" @click="goLottery">
          <div class="side-promo-head">
            <span class="side-tag side-tag--lottery">幸运抽奖</span>
            <span class="side-more side-more--light">去抽奖 ›</span>
          </div>
          <div class="side-promo-title side-promo-title--light">{{ lottery ? lottery.name : '每日幸运抽奖' }}</div>
          <div v-if="lottery" class="side-prizes">
            <span v-for="p in (lottery.prizes || []).slice(0, 4)" :key="p.id" class="side-prize">{{ p.name }}</span>
          </div>
          <div v-else class="side-prizes">
            <span class="side-prize">积分</span>
            <span class="side-prize">优惠券</span>
            <span class="side-prize">实物好礼</span>
          </div>
          <div class="side-promo-foot side-promo-foot--light">免费参与 · 100% 中奖</div>
        </div>
      </aside>
    </section>

    <!-- 限时秒杀 -->
    <section v-if="seckill" class="home-sec sec-seckill">
      <div class="sec-head2">
        <div class="sec-head-left">
          <span class="sec-title2 sec-title2--fire">限时秒杀</span>
          <span class="sec-sub2">限时低价 · 抢完即止</span>
          <span class="sec-countdown">
            <i class="el-icon-time"></i> 距结束 <b>{{ countdownText }}</b>
          </span>
        </div>
        <a class="sec-more2" @click="goTo('/seckill')">查看全部 ›</a>
      </div>

      <div class="seckill-layout">
        <div class="sk-featured" @click="goSeckill(seckill)">
          <div class="sk-pic">
            <pc-image
              :src="thumb(seckill.mainImage, 400)"
              :img-style="{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }"
            />
            <span class="sk-flag">限时秒杀</span>
          </div>
          <div class="sk-info">
            <div class="sk-name">{{ seckill.goodsName }}</div>
            <div class="sk-price">
              <span class="sk-yen">¥</span>{{ $price(seckill.seckillPrice) }}
              <s>¥{{ $price(seckill.originPrice) }}</s>
            </div>
            <div class="sk-bar"><span :style="{ width: progress(seckill) + '%' }"></span></div>
            <div class="sk-stock">已抢 {{ sold(seckill) }} 件 · 仅剩 {{ seckill.availableStock }} 件 · 限购 {{ seckill.perLimit }} 件</div>
            <div class="sk-foot">
              <span class="sk-tip"><i class="el-icon-alarm-clock"></i> 秒杀价不与其他优惠叠加</span>
              <el-button type="danger" round size="small">立即抢购</el-button>
            </div>
          </div>
        </div>

        <div v-if="upcoming" class="sk-upcoming" @click="goSeckill(upcoming)">
          <span class="sk-up-tag">即将开始</span>
          <div class="sk-up-pic">
            <pc-image
              :src="thumb(upcoming.mainImage, 300)"
              :img-style="{ width: '100%', height: '128px', objectFit: 'cover', display: 'block' }"
            />
          </div>
          <div class="sk-up-name">{{ upcoming.goodsName }}</div>
          <div class="sk-up-price">
            <span>¥{{ $price(upcoming.seckillPrice) }}</span>
            <s>¥{{ $price(upcoming.originPrice) }}</s>
          </div>
          <div class="sk-up-time">{{ (upcoming.startTime || '').slice(5, 16) }} 开抢</div>
          <span class="sk-up-cta">提前关注</span>
        </div>
      </div>
    </section>

    <!-- 拼团 + 抽奖 -->
    <section class="home-sec promo-twin">
      <div class="twin-panel">
        <div class="sec-head2">
          <div class="sec-head-left">
            <span class="sec-title2 sec-title2--group">超值拼团</span>
            <span class="sec-sub2">邀请好友一起拼</span>
          </div>
          <a class="sec-more2" @click="goTo('/group')">查看全部 ›</a>
        </div>
        <div v-if="groups.length" class="twin-group-list">
          <div v-for="g in groups.slice(0, 3)" :key="g.id" class="twin-group" @click="goGroup(g)">
            <div class="twin-group-pic">
              <pc-image
                :src="thumb(g.mainImage, 300)"
                :img-style="{ width: '86px', height: '86px', objectFit: 'cover', display: 'block', borderRadius: '8px' }"
              />
            </div>
            <div class="twin-group-body">
              <div class="twin-group-name">{{ g.goodsName }}</div>
              <div class="twin-group-tags">
                <span class="tag-group">{{ g.groupSize }} 人团</span>
                <span class="tag-hour">{{ g.validHours || 24 }} 小时成团</span>
              </div>
              <div class="twin-group-price">
                <b>¥{{ $price(g.groupPrice) }}</b>
                <s>¥{{ $price(g.originPrice) }}</s>
              </div>
            </div>
            <span class="twin-group-cta">去拼团</span>
          </div>
        </div>
        <el-empty v-else description="暂无拼团活动" :image-size="70" />
      </div>

      <div class="twin-panel twin-panel--lottery">
        <div class="lottery-deco lottery-deco--1"></div>
        <div class="lottery-deco lottery-deco--2"></div>
        <div class="lottery-content">
          <span class="lottery-tag">LUCKY DRAW</span>
          <div class="lottery-title">{{ lottery ? lottery.name : '每日幸运抽奖' }}</div>
          <div class="lottery-desc">
            每日 {{ lottery ? lottery.dailyLimit : 3 }} 次机会 ·
            {{ lottery && lottery.consumePoints > 0 ? lottery.consumePoints + ' 积分/次' : '免费参与' }}
          </div>
          <div class="lottery-prize-list">
            <span v-for="p in ((lottery && lottery.prizes) || []).slice(0, 4)" :key="p.id" class="lottery-prize-chip">
              {{ p.name }}
            </span>
          </div>
          <el-button class="lottery-btn" round @click="goLottery">立即抽奖</el-button>
        </div>
        <div class="lottery-orb">
          <i class="el-icon-present"></i>
          <span>100% 中奖</span>
        </div>
      </div>
    </section>

    <!-- 品牌店铺 -->
    <section v-if="merchants.length" class="section shop-section">
      <div class="section-head">
        <span class="section-title">品牌店铺</span>
        <span class="section-sub">官方好店 · 品质保障</span>
      </div>
      <div class="shop-grid">
        <div v-for="m in merchants.slice(0, 8)" :key="m.id" class="shop-cell" @click="$router.push(`/merchant/${m.id}`)">
          <pc-image
            v-if="m.logo"
            :src="m.logo"
            :img-style="{ width: '44px', height: '44px', objectFit: 'cover', borderRadius: '10px', display: 'block' }"
          />
          <span v-else class="shop-cell-fallback">{{ (m.name || '?').slice(0, 1) }}</span>
          <div class="shop-cell-body">
            <div class="shop-cell-name">{{ m.name }}</div>
            <div class="shop-cell-sub">{{ m.category || '综合店铺' }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- 热销商品 -->
    <section class="section">
      <div class="section-head">
        <span class="section-title">热销推荐</span>
        <a class="section-more" @click="goList">更多 ›</a>
      </div>
      <div class="goods-grid">
        <div v-for="g in hotGoods" :key="g.id" class="goods-card" @click="goDetail(g)">
          <div class="goods-card-imgwrap">
            <pc-image :src="thumb(g.mainImage, 300)" :img-style="{ width: '100%', height: '190px', objectFit: 'cover', display: 'block' }" />
            <span v-if="promoOf(g)" class="goods-card-promo">{{ promoOf(g).ruleText }}</span>
          </div>
          <div class="goods-card-body">
            <div class="goods-card-name">{{ g.name }}</div>
            <div class="goods-card-sub">{{ g.subTitle || g.brandName || '' }}</div>
            <div class="goods-card-price">
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
    </section>
  </div>
</template>

<script>
import { getBanners, getCategories, getGoodsPage, getMerchantList } from '@/api/shop'
import { getGoodsPromotions, pickBadge, promoPrice } from '@/api/promotion'
import { getSeckillActivities, getGroupActivities, getLotteryActivities } from '@/api/marketing'
import { thumb } from '@/utils/format'
import countdown from '@/utils/countdown'
import PcImage from '@/components/PcImage.vue'
import HomeBanner from '@/components/HomeBanner.vue'

const EMOJI_MAP = {
  手机: '📱', 数码: '💻', 家用: '🏠', 电视: '📺', 服饰: '👕', 鞋: '👟',
  包: '👜', 厨房: '🍳', 智能: '⌚', 笔记本: '💻', 冰: '🧊', 洗: '🫧'
}

const QUICK_ENTRIES = [
  { name: '限时秒杀', icon: 'el-icon-time', path: '/seckill', bg: 'linear-gradient(135deg,#ff7a45,#ff3b30)' },
  { name: '超值拼团', icon: 'el-icon-user-solid', path: '/group', bg: 'linear-gradient(135deg,#ff9f43,#ff5a7d)' },
  { name: '幸运抽奖', icon: 'el-icon-present', path: '/lottery', bg: 'linear-gradient(135deg,#a06bff,#6b5cff)' },
  { name: '领券中心', icon: 'el-icon-ticket', path: '/coupon/center', bg: 'linear-gradient(135deg,#ffb33f,#ff8a00)' },
  { name: '每日签到', icon: 'el-icon-calendar', path: '/signin', bg: 'linear-gradient(135deg,#4facfe,#2f80ed)' },
  { name: '分销中心', icon: 'el-icon-share', path: '/distribution', bg: 'linear-gradient(135deg,#38d6a8,#12b76a)' }
]

export default {
  name: 'ShopHome',
  components: { PcImage, HomeBanner },
  mixins: [countdown],
  data() {
    return {
      banners: [],
      categories: [],
      merchants: [],
      hotGoods: [],
      promoMap: {},
      seckills: [],
      upcoming: null,
      groups: [],
      lottery: null,
      quickEntries: QUICK_ENTRIES
    }
  },
  computed: {
    // 倒计时取第一场进行中的秒杀
    endTime() {
      return (this.seckills[0] && this.seckills[0].endTime) || null
    },
    seckill() {
      return this.seckills.find(i => i.onSale) || this.seckills[0] || null
    }
  },
  created() {
    getBanners().then(res => { this.banners = res || [] }).catch(() => {})
    getCategories().then(res => { this.categories = (res || []).slice(0, 8) }).catch(() => {})
    getMerchantList().then(res => { this.merchants = (res || []).slice(0, 8) }).catch(() => {})
    getGoodsPage({ pageNum: 1, pageSize: 10, sort: 'sales' }).then(res => {
      this.hotGoods = (res && res.records) || []
      // V15 促销角标（available 匿名可读，失败静默）
      for (const g of this.hotGoods) {
        getGoodsPromotions(g.id).then(list => {
          const badge = pickBadge(list)
          if (badge) this.promoMap[String(g.id)] = badge
        }).catch(() => {})
      }
    }).catch(() => {})

    // 营销模块独立降级
    Promise.all([
      getSeckillActivities().catch(() => []),
      getSeckillActivities('upcoming').catch(() => [])
    ]).then(([ongoing, future]) => {
      this.seckills = (ongoing || []).slice(0, 2)
      this.upcoming = (future || []).find(i => !i.onSale) || null
    })
    getGroupActivities().then(res => { this.groups = res || [] }).catch(() => {})
    getLotteryActivities().then(res => { this.lottery = (res || [])[0] || null }).catch(() => {})
  },
  methods: {
    promoOf(g) {
      return this.promoMap[String(g.id)] || null
    },
    promoPriceOf(g) {
      const p = this.promoOf(g)
      return p && Number(p.type) === 3 ? promoPrice(g.priceMin, p.discountRate) : null
    },
    categoryEmoji(name) {
      if (!name) return '🛍️'
      for (const key of Object.keys(EMOJI_MAP)) {
        if (name.includes(key)) return EMOJI_MAP[key]
      }
      return '🛍️'
    },
    subNames(c) {
      return (c.children || []).slice(0, 3).map(i => i.name).join(' / ')
    },
    sold(a) {
      return Math.max(0, (a.totalStock || 0) - (a.availableStock || 0))
    },
    progress(a) {
      if (!a.totalStock) return 0
      return Math.round((this.sold(a) / a.totalStock) * 100)
    },
    goTo(path) {
      this.$router.push(path)
    },
    onBanner(b) {
      const url = b.linkUrl || ''
      if (!url) return
      // 券模板直链（/coupon/5001）没有对应路由，统一进领券中心
      if (url.startsWith('/coupon')) {
        this.$router.push('/coupon/center')
      } else if (url.startsWith('/')) {
        this.$router.push(url)
      }
    },
    goCategory(c) {
      this.$router.push({ path: '/list', query: { categoryId: c.id } })
    },
    goList() {
      this.$router.push('/list')
    },
    goDetail(g) {
      this.$router.push(`/goods/${g.id}`)
    },
    goSeckill(a) {
      this.$router.push(`/seckill/${a.id}`)
    },
    goGroup(g) {
      this.$router.push(`/group/${g.id}`)
    },
    goLottery() {
      this.$router.push(this.lottery ? `/lottery/${this.lottery.id}` : '/lottery')
    }
  }
}
</script>

<style scoped>
.home { padding-bottom: 8px; }

/* ---------- 首屏 ---------- */
.home-hero {
  display: grid;
  grid-template-columns: 208px 1fr 268px;
  gap: 14px;
  align-items: stretch;
}

.hero-cats {
  background: #fff;
  border-radius: 12px;
  padding: 14px 0 10px;
  box-shadow: 0 6px 18px rgba(24, 32, 66, 0.06);
  display: flex;
  flex-direction: column;
}
.hero-cats-head {
  display: flex; align-items: baseline; justify-content: space-between;
  padding: 0 18px 10px; border-bottom: 1px solid #f3f5f9;
}
.hero-cats-head span:first-child { font-size: 15px; font-weight: 800; color: #1f2430; }
.hero-cats-en { font-size: 8px; letter-spacing: 1.6px; color: #b9bdc9; }
.hero-cat {
  display: flex; align-items: center; gap: 9px;
  padding: 10px 16px; margin: 1px 6px; border-radius: 8px;
  transition: background .15s;
}
.hero-cat:hover { background: #fff4f1; }
.hero-cat-ico {
  width: 28px; height: 28px; flex: none; border-radius: 8px; background: #f6f8ff;
  display: flex; align-items: center; justify-content: center; font-size: 16px;
}
.hero-cat-main { flex: 1; min-width: 0; display: flex; flex-direction: column; line-height: 1.25; }
.hero-cat-main b { font-size: 13px; color: #2b3145; font-weight: 600; }
.hero-cat-main em { font-style: normal; font-size: 11px; color: #a0a6b8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hero-cat .el-icon-arrow-right { font-size: 12px; color: #c3c8d4; }
.hero-cats-foot {
  margin-top: auto; padding: 12px 14px 0; border-top: 1px solid #f5f6fa;
  display: flex; flex-wrap: wrap; gap: 6px 10px;
}
.hero-cats-foot span { font-size: 10px; color: #97a0b4; display: inline-flex; align-items: center; gap: 3px; }
.hero-cats-foot i { color: #e1251b; font-size: 11px; }

.hero-hot { margin: 10px 6px 0; padding-top: 10px; border-top: 1px solid #f3f5f9; }
.hero-hot-head { padding: 0 10px 5px; font-size: 10px; letter-spacing: 1px; color: #b0b6c6; }
.hero-hot-item { display: flex; align-items: center; gap: 7px; padding: 5px 10px; border-radius: 6px; cursor: pointer; }
.hero-hot-item:hover { background: #fff8f6; }
.hero-hot-rank {
  width: 16px; height: 16px; flex: none; border-radius: 4px; background: #eef1f8; color: #8a90a2;
  font-size: 10px; font-weight: 700; display: flex; align-items: center; justify-content: center;
}
.hero-hot-rank.top { background: linear-gradient(135deg, #ff7a45, #ff3b30); color: #fff; }
.hero-hot-name { flex: 1; min-width: 0; font-size: 11.5px; color: #43495c; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.hero-hot-price { flex: none; font-size: 11px; color: #e1251b; font-weight: 600; }

.hero-center { min-width: 0; display: flex; flex-direction: column; gap: 12px; }
.hero-quick {
  display: grid; grid-template-columns: repeat(6, 1fr); gap: 8px;
  background: #fff; border-radius: 12px; padding: 12px 10px;
  box-shadow: 0 6px 18px rgba(24, 32, 66, 0.06);
}
.quick-cell {
  display: flex; flex-direction: column; align-items: center; gap: 7px;
  padding: 3px 0; border-radius: 8px; transition: background .15s;
}
.quick-cell:hover { background: #f7f9ff; }
.quick-ico {
  width: 40px; height: 40px; border-radius: 12px; display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 20px; box-shadow: 0 5px 12px rgba(40, 55, 150, 0.16);
}
.quick-label { font-size: 12px; color: #43495c; font-weight: 500; }

.hero-side { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.side-promo {
  flex: 1; border-radius: 12px; padding: 13px 14px;
  background: #fff; box-shadow: 0 6px 18px rgba(24, 32, 66, 0.06);
  cursor: pointer; display: flex; flex-direction: column; transition: transform .15s, box-shadow .15s;
}
.side-promo:hover { transform: translateY(-2px); box-shadow: 0 10px 24px rgba(24, 32, 66, 0.1); }
.side-promo-head { display: flex; align-items: center; justify-content: space-between; }
.side-tag {
  font-size: 12px; font-weight: 700; color: #ff4d2e; background: #fff0ec;
  border-radius: 7px; padding: 1px 8px;
}
.side-tag--group { color: #ff3d6e; background: #fff0f5; }
.side-tag--lottery { color: #ffe6a8; background: rgba(255, 255, 255, .16); }
.side-more { font-size: 11px; color: #a0a6b8; }
.side-more--light { color: rgba(255, 255, 255, .72); }
.side-promo-title { margin-top: 7px; font-size: 15px; font-weight: 700; color: #222838; }
.side-promo-title--light { color: #fff; }
.side-thumbs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-top: 9px; }
.side-thumb { border-radius: 7px; overflow: hidden; background: #f4f6fb; }
.side-promo-foot { margin-top: auto; padding-top: 8px; font-size: 11px; color: #98a0b6; }
.side-promo-foot--light { color: rgba(255, 255, 255, .7); }

.side-promo--lottery {
  position: relative; overflow: hidden; border: none;
  background: linear-gradient(130deg, #2b2167 0%, #6a3fd6 58%, #b04ee6 100%);
  box-shadow: 0 10px 24px rgba(90, 58, 190, .28);
}
.side-prizes { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 9px; }
.side-prize {
  font-size: 10px; color: #ffe2a8; background: rgba(255, 255, 255, .14);
  border: 1px solid rgba(255, 226, 168, .35); border-radius: 6px; padding: 1px 6px;
  max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* ---------- 通用区块 ---------- */
.home-sec {
  margin-top: 18px; background: #fff; border-radius: 12px; padding: 16px 18px 18px;
  box-shadow: 0 6px 18px rgba(24, 32, 66, 0.06);
}
.sec-head2 { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.sec-head-left { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.sec-title2 { font-size: 19px; font-weight: 800; color: #1f2430; letter-spacing: .5px; }
.sec-title2--fire {
  background: linear-gradient(90deg, #ff4d2e, #ff8a3d); -webkit-background-clip: text; background-clip: text; color: transparent;
}
.sec-title2--group {
  background: linear-gradient(90deg, #ff7a18, #ff3d6e); -webkit-background-clip: text; background-clip: text; color: transparent;
}
.sec-sub2 { font-size: 12px; color: #a0a6b8; }
.sec-countdown {
  display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: #7c8399;
  background: #fff5f2; border-radius: 11px; padding: 2px 10px;
}
.sec-countdown i { color: #ff4d2e; }
.sec-countdown b { color: #ff3b2f; font-size: 13px; letter-spacing: .5px; }
.sec-more2 { font-size: 12px; color: #8f96a8; }
.sec-more2:hover { color: #e1251b; }

/* ---------- 秒杀 ---------- */
.seckill-layout { display: grid; grid-template-columns: 1fr 268px; gap: 14px; }
.sk-featured {
  display: flex; gap: 18px; padding: 14px; border-radius: 10px; cursor: pointer;
  background: linear-gradient(120deg, #fff7f4 0%, #fff 60%);
  border: 1px solid #ffe6df;
}
.sk-pic { position: relative; width: 250px; height: 214px; flex: none; border-radius: 9px; overflow: hidden; }
.sk-flag {
  position: absolute; top: 9px; left: 9px; font-size: 11px; color: #fff;
  background: linear-gradient(135deg, #ff6a2f, #ff2d2d); padding: 2px 8px; border-radius: 8px;
}
.sk-info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.sk-name { font-size: 17px; font-weight: 700; color: #222838; line-height: 1.4; }
.sk-price { margin-top: 10px; font-size: 30px; font-weight: 800; color: #ff3b2f; line-height: 1; }
.sk-yen { font-size: 16px; }
.sk-price s { margin-left: 10px; font-size: 13px; color: #b3b8c6; font-weight: 400; }
.sk-bar { margin-top: 14px; height: 9px; border-radius: 5px; background: #ffe6e1; overflow: hidden; }
.sk-bar span { display: block; height: 100%; border-radius: 5px; background: linear-gradient(90deg, #ff7a45, #ff2d2d); }
.sk-stock { margin-top: 7px; font-size: 12px; color: #98a0b6; }
.sk-foot { margin-top: auto; padding-top: 12px; display: flex; align-items: center; justify-content: space-between; border-top: 1px dashed #f0e6e2; }
.sk-tip { font-size: 11px; color: #a0a6b8; }
.sk-upcoming {
  position: relative; display: flex; flex-direction: column; padding: 12px;
  border-radius: 10px; background: #f7f9ff; border: 1px solid #e9edfa; cursor: pointer;
}
.sk-up-tag {
  position: absolute; top: 12px; left: 12px; z-index: 2; font-size: 10px; color: #fff;
  background: linear-gradient(135deg, #5b6cff, #7b5cf5); padding: 1px 7px; border-radius: 7px;
}
.sk-up-pic { border-radius: 8px; overflow: hidden; }
.sk-up-name { margin-top: 9px; font-size: 13px; color: #2b3145; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sk-up-price { margin-top: 5px; font-size: 18px; font-weight: 800; color: #ff3b2f; }
.sk-up-price s { margin-left: 6px; font-size: 11px; color: #b3b8c6; font-weight: 400; }
.sk-up-time { margin-top: 4px; font-size: 11px; color: #7b8299; }
.sk-up-cta {
  margin-top: auto; padding-top: 10px; font-size: 12px; color: #5b6cff; font-weight: 600;
}

/* ---------- 拼团 + 抽奖 ---------- */
.promo-twin { display: grid; grid-template-columns: 1fr 340px; gap: 16px; }
.twin-panel { min-width: 0; }
.twin-group-list { display: flex; flex-direction: column; gap: 10px; }
.twin-group {
  display: flex; align-items: center; gap: 13px; padding: 10px; border-radius: 9px;
  background: #fafbff; border: 1px solid #eef1f9; cursor: pointer; transition: all .15s;
}
.twin-group:hover { border-color: #ffd9e2; background: #fff8fa; }
.twin-group-pic { flex: none; border-radius: 8px; overflow: hidden; }
.twin-group-body { flex: 1; min-width: 0; }
.twin-group-name { font-size: 13px; font-weight: 600; color: #2b3145; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.twin-group-tags { display: flex; gap: 6px; margin-top: 5px; }
.tag-group { font-size: 10px; color: #ff3d6e; background: #fff0f5; border-radius: 6px; padding: 1px 6px; }
.tag-hour { font-size: 10px; color: #8a90a2; background: #f2f4f9; border-radius: 6px; padding: 1px 6px; }
.twin-group-price { margin-top: 5px; font-size: 16px; font-weight: 800; color: #ff3d6e; }
.twin-group-price s { margin-left: 6px; font-size: 11px; color: #b3b8c6; font-weight: 400; }
.twin-group-cta {
  flex: none; font-size: 11px; color: #fff; background: linear-gradient(135deg, #ff7a18, #ff3d6e);
  border-radius: 12px; padding: 5px 12px; font-weight: 600;
}

.twin-panel--lottery {
  position: relative; overflow: hidden; border-radius: 10px; padding: 18px;
  background: linear-gradient(125deg, #2b2167 0%, #6a3fd6 58%, #b04ee6 100%);
  display: flex; align-items: center; justify-content: space-between;
}
.lottery-deco { position: absolute; border-radius: 50%; background: rgba(255, 255, 255, .1); }
.lottery-deco--1 { width: 150px; height: 150px; right: -40px; top: -56px; }
.lottery-deco--2 { width: 76px; height: 76px; right: 76px; bottom: -34px; }
.lottery-content { position: relative; z-index: 2; }
.lottery-tag { font-size: 9px; letter-spacing: 2px; color: rgba(255, 255, 255, .6); }
.lottery-title { margin-top: 4px; font-size: 21px; font-weight: 800; color: #fff; }
.lottery-desc { margin-top: 5px; font-size: 11px; color: rgba(255, 255, 255, .72); }
.lottery-prize-list { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 9px; }
.lottery-prize-chip {
  font-size: 10px; color: #ffe2a8; background: rgba(255, 255, 255, .14);
  border: 1px solid rgba(255, 226, 168, .35); border-radius: 6px; padding: 1px 6px;
  max-width: 122px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.lottery-btn {
  margin-top: 12px; border: none; color: #4a2379;
  background: linear-gradient(135deg, #ffe6a8, #ffc74d);
  font-weight: 700;
}
.lottery-orb {
  position: relative; z-index: 2; width: 88px; flex: none; display: flex; flex-direction: column;
  align-items: center; gap: 5px;
}
.lottery-orb i {
  width: 58px; height: 58px; border-radius: 50%; font-size: 30px; color: #ffc74d;
  background: rgba(255, 255, 255, .15); border: 1px solid rgba(255, 230, 168, .42);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 8px 18px rgba(0, 0, 0, .2);
}
.lottery-orb span {
  font-size: 10px; color: #ffe6a8; border: 1px solid rgba(255, 230, 168, .45);
  border-radius: 7px; padding: 0 6px;
}

/* ---------- 品牌店铺 ---------- */
.shop-section { margin-top: 28px; }

/* ---------- 商品卡促销角标 ---------- */
.goods-card-imgwrap { position: relative; }
.goods-card-promo {
  position: absolute; top: 8px; left: 8px; max-width: calc(100% - 16px); padding: 1px 7px;
  border-radius: 4px 10px 10px 2px; background: linear-gradient(135deg, #ff6034, #e1251b);
  color: #fff; font-size: 12px; line-height: 20px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis;
}
.goods-card-origin { color: #c0c4cc; font-size: 12px; text-decoration: line-through; }
</style>
