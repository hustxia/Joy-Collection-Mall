<template>
  <div class="page page--tab home-page">
    <!-- 顶部品牌栏 + 导航（新增） -->
    <div class="home-top">
      <div class="home-top-row">
        <div class="brand" @click="router.push('/home')">
          <span class="brand-logo">悦</span>
          <span class="brand-text">
            <b>悦集商城</b>
            <em>JOY COLLECTION</em>
          </span>
        </div>
        <div class="top-search" @click="goList()">
          <van-icon name="search" />
          <span>搜索商品 / 品牌</span>
        </div>
        <van-icon name="chat-o" class="top-msg" @click="router.push('/notice')" />
      </div>
      <nav class="top-nav">
        <a
          v-for="n in navs"
          :key="n.path"
          class="top-nav-item"
          :class="{ on: n.path === '/home' }"
          @click="router.push(n.path)"
        >{{ n.name }}</a>
      </nav>
    </div>

    <!-- 主 Banner 轮播 -->
    <HomeBanner :banners="banners" @select="onBanner" />

    <!-- 金刚区 -->
    <div class="quick-grid">
      <div v-for="q in quickEntries" :key="q.path" class="quick-item" @click="router.push(q.path)">
        <div class="quick-icon" :style="{ background: q.bg }">
          <van-icon :name="q.icon" />
        </div>
        <span class="quick-name">{{ q.name }}</span>
      </div>
    </div>

    <!-- 限时秒杀 -->
    <section v-if="seckill" class="card sec seckill-sec">
      <div class="sec-head">
        <div class="sec-title-wrap">
          <span class="sec-title sec-title--fire">限时秒杀</span>
          <span class="sec-sub">限时低价 · 抢完即止</span>
        </div>
        <a class="sec-more" @click="router.push('/seckill')">全部 <van-icon name="arrow" /></a>
      </div>

      <div class="sk-feature" @click="router.push(`/seckill/${seckill.id}`)">
        <div class="sk-imgwrap">
          <van-image :src="thumb(seckill.mainImage, 300)" fit="cover" class="sk-img">
            <template #error><div class="sk-img-ph">图</div></template>
          </van-image>
          <span class="sk-flag">秒杀</span>
        </div>
        <div class="sk-body">
          <div class="sk-name van-multi-ellipsis--l2">{{ seckill.goodsName }}</div>
          <div class="sk-price">
            <span class="sk-symbol">¥</span>{{ fenToYuan(seckill.seckillPrice) }}
            <s>¥{{ fenToYuan(seckill.originPrice) }}</s>
          </div>
          <div class="sk-cd">
            <van-icon name="clock-o" />
            <span>距结束</span>
            <van-count-down :time="seckillRemain" format="HH:mm:ss" class="sk-cd-num" />
          </div>
          <div class="sk-bar">
            <i :style="{ width: skProgress + '%' }"></i>
          </div>
          <div class="sk-stock">已抢 {{ skSold }} 件 · 仅剩 {{ seckill.availableStock }} 件</div>
        </div>
        <van-button round size="small" color="#ff4d2e" class="sk-btn">马上抢</van-button>
      </div>

      <div v-if="upcoming" class="sk-next" @click="router.push(`/seckill/${upcoming.id}`)">
        <span class="sk-next-tag">下一场</span>
        <span class="sk-next-name van-ellipsis">{{ upcoming.goodsName }}</span>
        <span class="sk-next-time">{{ (upcoming.startTime || '').slice(5, 16) }} 开抢</span>
      </div>
    </section>

    <!-- 超值拼团 -->
    <section v-if="groups.length" class="card sec">
      <div class="sec-head">
        <div class="sec-title-wrap">
          <span class="sec-title sec-title--group">超值拼团</span>
          <span class="sec-sub">邀好友 · 享团价</span>
        </div>
        <a class="sec-more" @click="router.push('/group')">全部 <van-icon name="arrow" /></a>
      </div>
      <div class="group-row">
        <div v-for="g in groups.slice(0, 4)" :key="g.id" class="group-item" @click="router.push(`/group/${g.id}`)">
          <div class="group-imgwrap">
            <van-image :src="thumb(g.mainImage, 300)" fit="cover" class="group-img">
              <template #error><div class="group-img-ph">图</div></template>
            </van-image>
            <span class="group-badge">{{ g.groupSize }} 人团</span>
          </div>
          <div class="group-name van-ellipsis">{{ g.goodsName }}</div>
          <div class="group-price">
            <b>¥{{ fenToYuan(g.groupPrice) }}</b>
            <s>¥{{ fenToYuan(g.originPrice) }}</s>
          </div>
          <span class="group-cta">去拼团</span>
        </div>
      </div>
    </section>

    <!-- 幸运抽奖 -->
    <section v-if="lottery" class="lottery-card" @click="router.push(`/lottery/${lottery.id}`)">
      <div class="lottery-deco lottery-deco--1"></div>
      <div class="lottery-deco lottery-deco--2"></div>
      <div class="lottery-main">
        <div class="lottery-eyebrow">LUCKY DRAW</div>
        <div class="lottery-title">{{ lottery.name }}</div>
        <div class="lottery-desc">
          每日 {{ lottery.dailyLimit }} 次机会 · {{ lottery.consumePoints > 0 ? `每次 ${lottery.consumePoints} 积分` : '免费参与' }}
        </div>
        <div class="lottery-prizes">
          <span v-for="p in (lottery.prizes || []).slice(0, 4)" :key="p.id" class="lottery-prize van-ellipsis">
            {{ p.name }}
          </span>
        </div>
        <span class="lottery-btn">立即抽奖 <van-icon name="arrow" /></span>
      </div>
      <div class="lottery-gift">
        <div class="lottery-gift-orb">
          <van-icon name="gift" />
        </div>
        <span class="lottery-gift-label">100% 中奖</span>
      </div>
    </section>

    <!-- 商品分类 -->
    <section v-if="categories.length" class="card sec">
      <div class="sec-head">
        <div class="sec-title-wrap">
          <span class="sec-title">商品分类</span>
          <span class="sec-sub">好物分类逛</span>
        </div>
        <a class="sec-more" @click="router.push('/category')">全部 <van-icon name="arrow" /></a>
      </div>
      <div class="cat-row">
        <div v-for="c in categories" :key="c.id" class="cat-item" @click="goCategory(c)">
          <div v-if="c.icon" class="cat-icon">
            <img :src="c.icon" alt="" />
          </div>
          <div v-else class="cat-icon cat-icon--ph">{{ (c.name || '')[0] }}</div>
          <span class="cat-name van-ellipsis">{{ c.name }}</span>
          <span class="cat-sub van-ellipsis">{{ (c.children || []).slice(0, 2).map(i => i.name).join(' · ') }}</span>
        </div>
      </div>
    </section>

    <!-- 品牌店铺 -->
    <section v-if="merchants.length" class="card sec">
      <div class="sec-head">
        <div class="sec-title-wrap">
          <span class="sec-title">品牌店铺</span>
          <span class="sec-sub">官方好店</span>
        </div>
      </div>
      <div class="shop-row">
        <div v-for="m in merchants.slice(0, 8)" :key="m.id" class="shop-item" @click="router.push(`/merchant/${m.id}`)">
          <div class="shop-logo">
            <img v-if="m.logo" :src="m.logo" alt="" />
            <span v-else>{{ (m.name || '?').slice(0, 1) }}</span>
          </div>
          <span class="shop-name van-ellipsis">{{ m.name }}</span>
          <span class="shop-cate">{{ m.category || '综合' }}</span>
        </div>
      </div>
    </section>

    <!-- 为你推荐 -->
    <section class="sec">
      <div class="sec-head recommend-head">
        <div class="sec-title-wrap">
          <span class="sec-title">为你推荐</span>
          <span class="sec-sub">猜你喜欢</span>
        </div>
      </div>
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
        <div class="goods-grid">
          <GoodsCard v-for="g in goods" :key="g.id" :goods="g" />
        </div>
      </van-list>
    </section>

    <TabBar />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getBanners, getCategories, getGoodsPage, getMerchantList } from '@/api/shop'
import { getSeckillActivities } from '@/api/seckill'
import { getGroupActivities } from '@/api/group'
import { getLotteryActivities } from '@/api/lottery'
import { fenToYuan, thumb, parseTime } from '@/utils/format'
import GoodsCard from '@/components/GoodsCard.vue'
import TabBar from '@/components/TabBar.vue'
import HomeBanner from '@/components/HomeBanner.vue'

const router = useRouter()

const navs = [
  { name: '首页', path: '/home' },
  { name: '秒杀', path: '/seckill' },
  { name: '拼团', path: '/group' },
  { name: '抽奖', path: '/lottery' },
  { name: '领券', path: '/coupon/center' },
  { name: '分销', path: '/distribution' },
]

const quickEntries = [
  { name: '限时秒杀', icon: 'clock-o', path: '/seckill', bg: 'linear-gradient(135deg,#ff7a45,#ff3b30)' },
  { name: '超值拼团', icon: 'friends-o', path: '/group', bg: 'linear-gradient(135deg,#ff9f43,#ff6b6b)' },
  { name: '幸运抽奖', icon: 'gift-o', path: '/lottery', bg: 'linear-gradient(135deg,#a06bff,#6b5cff)' },
  { name: '领券中心', icon: 'coupon-o', path: '/coupon/center', bg: 'linear-gradient(135deg,#ffb33f,#ff8a00)' },
  { name: '每日签到', icon: 'calendar-o', path: '/signin', bg: 'linear-gradient(135deg,#4facfe,#2f80ed)' },
  { name: '分销中心', icon: 'share-o', path: '/distribution', bg: 'linear-gradient(135deg,#38d6a8,#12b76a)' },
]

const banners = ref([])
const categories = ref([])
const merchants = ref([])
const seckill = ref(null)
const upcoming = ref(null)
const groups = ref([])
const lottery = ref(null)

const skSold = computed(() => {
  if (!seckill.value) return 0
  return Math.max(0, (seckill.value.totalStock || 0) - (seckill.value.availableStock || 0))
})
const skProgress = computed(() => {
  if (!seckill.value || !seckill.value.totalStock) return 0
  return Math.round((skSold.value / seckill.value.totalStock) * 100)
})
const seckillRemain = computed(() => {
  if (!seckill.value) return 0
  const d = parseTime(seckill.value.endTime)
  const t = (d ? d.getTime() : 0) - Date.now()
  return t > 0 ? t : 0
})

// 商品流（上拉加载）
const goods = ref([])
const pageNum = ref(1)
const pageSize = 12
const loading = ref(false)
const finished = ref(false)

const onLoad = async () => {
  try {
    const page = await getGoodsPage({ pageNum: pageNum.value, pageSize })
    goods.value.push(...(page.records || []))
    pageNum.value += 1
    if (!page.records || goods.value.length >= page.total) {
      finished.value = true
    }
  } finally {
    loading.value = false
  }
}

const goList = keyword => router.push({ path: '/goods/list', query: keyword ? { keyword } : {} })
const goCategory = c => router.push({ path: '/category', query: { active: c.id } })
const onBanner = b => {
  if (!b.linkUrl) return
  if (b.linkUrl.startsWith('/')) router.push(b.linkUrl)
  else window.open(b.linkUrl)
}

onMounted(async () => {
  banners.value = (await getBanners().catch(() => [])) || []
  categories.value = (await getCategories().catch(() => [])) || []

  // 营销模块独立降级：任一接口异常不影响首页其他内容
  Promise.all([
    getSeckillActivities().catch(() => []),
    getSeckillActivities('upcoming').catch(() => []),
  ]).then(([ongoing, future]) => {
    const list = ongoing || []
    seckill.value = list.find(i => i.onSale) || list[0] || null
    upcoming.value = (future || []).find(i => !i.onSale) || null
  })
  getGroupActivities().then(res => { groups.value = res || [] }).catch(() => {})
  getLotteryActivities().then(res => { lottery.value = (res || [])[0] || null }).catch(() => {})

  try {
    merchants.value = (await getMerchantList()) || []
  } catch (e) {
    merchants.value = []
  }
})
</script>

<style scoped>
.home-page {
  background: #f4f6fb;
}

/* ---------- 顶部导航 ---------- */
.home-top {
  position: sticky;
  top: 0;
  z-index: 20;
  background: linear-gradient(120deg, #2f3d9e 0%, #4a55e2 45%, #7b5cf5 100%);
  padding: 12px 12px 10px;
  border-radius: 0 0 16px 16px;
  box-shadow: 0 6px 18px rgba(58, 74, 196, 0.24);
}

.home-top-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

.brand-logo {
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: linear-gradient(135deg, #ff8f5c, #ff4d2e);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 3px 8px rgba(255, 77, 46, 0.35);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.brand-text b {
  font-size: 15px;
  color: #fff;
  letter-spacing: 1px;
}

.brand-text em {
  font-style: normal;
  font-size: 7px;
  color: rgba(255, 255, 255, 0.65);
  letter-spacing: 1.5px;
}

.top-search {
  flex: 1;
  height: 32px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 12px;
  font-size: 12px;
  color: #969799;
}

.top-search .van-icon {
  font-size: 15px;
  color: #5b6cff;
}

.top-msg {
  color: #fff;
  font-size: 20px;
  flex: none;
}

.top-nav {
  display: flex;
  gap: 6px;
  margin-top: 10px;
  overflow-x: auto;
  scrollbar-width: none;
}

.top-nav::-webkit-scrollbar {
  display: none;
}

.top-nav-item {
  flex: none;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.78);
  padding: 5px 10px;
  border-radius: 11px;
  white-space: nowrap;
}

.top-nav-item.on {
  color: #3a46c9;
  background: #fff;
  font-weight: 700;
}

/* ---------- 金刚区 ---------- */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  margin: 10px;
  padding: 12px 4px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 4px 14px rgba(31, 45, 94, 0.06);
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.quick-icon {
  width: 38px;
  height: 38px;
  border-radius: 13px;
  color: #fff;
  font-size: 19px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 5px 10px rgba(43, 60, 180, 0.16);
}

.quick-name {
  font-size: 10.5px;
  color: #4a4f63;
  white-space: nowrap;
}

/* ---------- 通用区块 ---------- */
.card {
  background: #fff;
  border-radius: 14px;
  margin: 10px;
  padding: 13px 12px 14px;
  box-shadow: 0 4px 14px rgba(31, 45, 94, 0.06);
}

.sec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.sec-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 7px;
  min-width: 0;
}

.sec-title {
  font-size: 16px;
  font-weight: 800;
  color: #1f2430;
  letter-spacing: 0.5px;
}

.sec-title--fire {
  background: linear-gradient(90deg, #ff4d2e, #ff8a3d);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.sec-title--group {
  background: linear-gradient(90deg, #ff7a18, #ff3d6e);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.sec-sub {
  font-size: 10.5px;
  color: #a3a9bd;
  white-space: nowrap;
}

.sec-more {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  font-size: 11px;
  color: #7a8199;
  flex: none;
}

/* ---------- 秒杀 ---------- */
.seckill-sec {
  padding-bottom: 12px;
}

.sk-feature {
  display: flex;
  gap: 10px;
  background: linear-gradient(135deg, #fff6f2, #fff);
  border-radius: 12px;
  padding: 10px;
  position: relative;
}

.sk-imgwrap {
  position: relative;
  flex: none;
}

.sk-img {
  width: 104px;
  height: 104px;
  border-radius: 10px;
  overflow: hidden;
  display: block;
}

.sk-img-ph {
  width: 104px;
  height: 104px;
  line-height: 104px;
  text-align: center;
  background: #f2f3f5;
  color: #c8c9cc;
  border-radius: 10px;
}

.sk-flag {
  position: absolute;
  top: 6px;
  left: 6px;
  font-size: 9px;
  color: #fff;
  background: linear-gradient(135deg, #ff6a2f, #ff2d2d);
  padding: 1px 6px;
  border-radius: 7px;
}

.sk-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding-right: 2px;
}

.sk-name {
  font-size: 13px;
  line-height: 17px;
  color: #222;
}

.sk-price {
  font-size: 20px;
  font-weight: 800;
  color: #ff3b2f;
  line-height: 1;
}

.sk-symbol {
  font-size: 12px;
}

.sk-price s {
  margin-left: 5px;
  font-size: 10px;
  color: #b3b7c4;
  font-weight: 400;
}

.sk-cd {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 10.5px;
  color: #8a8f9f;
}

.sk-cd .van-icon {
  color: #ff4d2e;
}

.sk-cd-num {
  color: #ff4d2e;
  font-size: 12px;
  font-weight: 700;
}

.sk-bar {
  height: 7px;
  border-radius: 4px;
  background: #ffe7e2;
  overflow: hidden;
}

.sk-bar i {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #ff7a45, #ff2d2d);
}

.sk-stock {
  font-size: 10px;
  color: #a3a9bd;
}

.sk-btn {
  position: absolute;
  right: 10px;
  bottom: 10px;
  height: 28px;
  padding: 0 13px;
  font-size: 11px;
  font-weight: 600;
}

.sk-next {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 9px;
  padding: 8px 10px;
  border-radius: 9px;
  background: #f6f7fd;
  font-size: 11px;
  color: #6b728e;
}

.sk-next-tag {
  flex: none;
  color: #fff;
  background: #5b6cff;
  border-radius: 6px;
  padding: 1px 6px;
  font-size: 10px;
}

.sk-next-name {
  flex: 1;
  min-width: 0;
  color: #3c4257;
}

.sk-next-time {
  flex: none;
  color: #5b6cff;
  font-weight: 600;
}

/* ---------- 拼团 ---------- */
.group-row {
  display: flex;
  gap: 9px;
  overflow-x: auto;
  scrollbar-width: none;
  padding-bottom: 2px;
}

.group-row::-webkit-scrollbar {
  display: none;
}

.group-item {
  flex: none;
  width: 132px;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 12px;
  padding: 8px;
  position: relative;
}

.group-imgwrap {
  position: relative;
}

.group-img {
  width: 100%;
  height: 96px;
  border-radius: 9px;
  overflow: hidden;
  display: block;
}

.group-img-ph {
  width: 100%;
  height: 96px;
  line-height: 96px;
  text-align: center;
  background: #f2f3f5;
  color: #c8c9cc;
  border-radius: 9px;
}

.group-badge {
  position: absolute;
  top: 5px;
  left: 5px;
  font-size: 9px;
  color: #fff;
  background: linear-gradient(135deg, #ff7a18, #ff3d6e);
  border-radius: 7px;
  padding: 1px 6px;
}

.group-name {
  margin-top: 7px;
  font-size: 11.5px;
  color: #33384d;
}

.group-price {
  margin-top: 3px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.group-price b {
  font-size: 16px;
  color: #ff3d6e;
}

.group-price s {
  font-size: 9.5px;
  color: #b3b7c4;
}

.group-cta {
  display: block;
  margin-top: 6px;
  text-align: center;
  font-size: 10.5px;
  color: #fff;
  background: linear-gradient(135deg, #ff7a18, #ff3d6e);
  border-radius: 9px;
  line-height: 22px;
}

/* ---------- 抽奖 ---------- */
.lottery-card {
  position: relative;
  margin: 10px;
  border-radius: 14px;
  padding: 16px 14px;
  overflow: hidden;
  background: linear-gradient(120deg, #2b2167 0%, #6a3fd6 55%, #b04ee6 100%);
  box-shadow: 0 10px 22px rgba(90, 58, 190, 0.28);
  display: flex;
  align-items: center;
}

.lottery-deco {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}

.lottery-deco--1 {
  width: 110px;
  height: 110px;
  right: -26px;
  top: -38px;
}

.lottery-deco--2 {
  width: 64px;
  height: 64px;
  right: 62px;
  bottom: -30px;
  background: rgba(255, 255, 255, 0.08);
}

.lottery-main {
  flex: 1;
  min-width: 0;
  position: relative;
}

.lottery-eyebrow {
  font-size: 8px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.6);
}

.lottery-title {
  margin-top: 2px;
  font-size: 18px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 1px;
}

.lottery-desc {
  margin-top: 3px;
  font-size: 10.5px;
  color: rgba(255, 255, 255, 0.72);
}

.lottery-prizes {
  display: flex;
  gap: 5px;
  margin-top: 8px;
  overflow: hidden;
}

.lottery-prize {
  font-size: 9.5px;
  color: #ffe2a8;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 226, 168, 0.35);
  border-radius: 7px;
  padding: 1px 6px;
  max-width: 96px;
}

.lottery-btn {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 9px;
  font-size: 11px;
  font-weight: 700;
  color: #4a2379;
  background: linear-gradient(135deg, #ffe6a8, #ffc74d);
  border-radius: 12px;
  padding: 4px 12px;
  box-shadow: 0 4px 10px rgba(255, 199, 77, 0.35);
}

.lottery-gift {
  flex: none;
  width: 74px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  position: relative;
}

.lottery-gift-orb {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 27px;
  color: #ffc74d;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 230, 168, 0.4);
  box-shadow: 0 6px 14px rgba(0, 0, 0, 0.2);
}

.lottery-gift-label {
  font-size: 9px;
  color: #ffe6a8;
  border: 1px solid rgba(255, 230, 168, 0.45);
  border-radius: 6px;
  padding: 0 5px;
}

/* ---------- 分类 ---------- */
.cat-row {
  display: flex;
  gap: 8px;
}

.cat-item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 9px 3px;
  border-radius: 11px;
  background: #fafbff;
  border: 1px solid #eff2fa;
}

.cat-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
}

.cat-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cat-icon--ph {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #edf1ff, #dfe6ff);
  color: #5b6cff;
  font-size: 15px;
  font-weight: 700;
}

.cat-name {
  font-size: 11.5px;
  color: #33384d;
  font-weight: 600;
  max-width: 100%;
}

.cat-sub {
  font-size: 8.5px;
  color: #a3a9bd;
  max-width: 100%;
}

/* ---------- 店铺 ---------- */
.shop-row {
  display: flex;
  gap: 11px;
  overflow-x: auto;
  scrollbar-width: none;
}

.shop-row::-webkit-scrollbar {
  display: none;
}

.shop-item {
  flex: none;
  width: 74px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.shop-logo {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  overflow: hidden;
  background: linear-gradient(135deg, #eef1ff, #e0e6ff);
  color: #5b6cff;
  font-size: 17px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.shop-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.shop-name {
  font-size: 10.5px;
  color: #33384d;
  max-width: 74px;
}

.shop-cate {
  font-size: 8.5px;
  color: #b3b7c4;
  max-width: 74px;
}

/* ---------- 推荐 ---------- */
.recommend-head {
  margin: 4px 2px 10px;
}
</style>
