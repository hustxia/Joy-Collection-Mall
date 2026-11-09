<template>
  <div class="home-banner">
    <van-swipe
      ref="swipeRef"
      :autoplay="3500"
      :show-indicators="false"
      lazy-render
      @change="onChange"
    >
      <van-swipe-item v-for="s in slides" :key="s.key">
        <div class="hb-slide" @click="onClick(s)">
          <img v-if="s.image" :src="s.image" class="hb-img" :alt="s.title" />
          <div v-else class="hb-img hb-img--grad" :style="{ background: s.bg }"></div>
          <div class="hb-mask"></div>
          <div class="hb-copy">
            <span class="hb-tag">{{ s.tag }}</span>
            <div class="hb-title">{{ s.title }}</div>
            <div v-if="s.sub" class="hb-sub">{{ s.sub }}</div>
            <span class="hb-cta">{{ s.cta }}<van-icon name="arrow" /></span>
          </div>
        </div>
      </van-swipe-item>
    </van-swipe>

    <div v-if="slides.length > 1" class="hb-dots">
      <i
        v-for="(s, i) in slides"
        :key="'dot-' + s.key"
        :class="{ on: i === active }"
        @click.stop="to(i)"
      ></i>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  banners: { type: Array, default: () => [] }
})
const emit = defineEmits(['select'])

// Banner 接口无数据或失败时用活动入口兜底，保证首页首屏始终有轮播
const FALLBACK_SLIDES = [
  {
    key: 'f-seckill',
    tag: '限时秒杀',
    title: '每日 10 点开抢，爆款直降',
    sub: '限时低价 · 抢完即止',
    cta: '去抢购',
    linkUrl: '/seckill',
    bg: 'linear-gradient(120deg,#ff7a45,#ff3b30)'
  },
  {
    key: 'f-group',
    tag: '超值拼团',
    title: '邀好友拼团，低至 5 折',
    sub: '2 人成团 · 团价更省',
    cta: '去拼团',
    linkUrl: '/group',
    bg: 'linear-gradient(120deg,#ff9f43,#ff3d6e)'
  },
  {
    key: 'f-lottery',
    tag: '幸运抽奖',
    title: '每日免费抽，100% 中奖',
    sub: '积分 · 优惠券 · 实物好礼',
    cta: '去抽奖',
    linkUrl: '/lottery',
    bg: 'linear-gradient(120deg,#6a3fd6,#b04ee6)'
  }
]

const GRADIENTS = [
  'linear-gradient(120deg,#ff7a45,#ff3b30)',
  'linear-gradient(120deg,#ff9f43,#ff3d6e)',
  'linear-gradient(120deg,#6a3fd6,#b04ee6)'
]

const active = ref(0)

const slides = computed(() => {
  const list = props.banners || []
  if (!list.length) return FALLBACK_SLIDES
  return list.map((b, i) => ({
    key: b.id || `banner-${i}`,
    image: b.imageUrl || b.image || '',
    tag: '悦集精选',
    title: b.title || '悦集商城',
    sub: '',
    cta: '立即查看',
    linkUrl: b.linkUrl || '',
    bg: GRADIENTS[i % GRADIENTS.length]
  }))
})

const onChange = idx => {
  active.value = idx
}

const to = idx => {
  const swipe = swipeRef.value
  if (swipe && typeof swipe.swipeTo === 'function') swipe.swipeTo(idx)
}

const onClick = s => {
  if (s.linkUrl) emit('select', s)
}

const swipeRef = ref(null)
</script>

<style scoped>
.home-banner {
  position: relative;
  margin: 10px 10px 0;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 8px 20px rgba(31, 45, 94, 0.12);
}

.hb-slide {
  position: relative;
  height: 180px;
}

.hb-img {
  width: 100%;
  height: 180px;
  object-fit: cover;
  display: block;
}

.hb-img--grad {
  position: relative;
}

.hb-img--grad::after {
  content: '';
  position: absolute;
  right: -26px;
  top: -40px;
  width: 132px;
  height: 132px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}

.hb-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(10, 16, 50, 0) 32%, rgba(10, 16, 50, 0.66) 100%);
}

.hb-copy {
  position: absolute;
  left: 14px;
  right: 64px;
  bottom: 14px;
}

.hb-tag {
  display: inline-block;
  font-size: 10px;
  color: #ffe6d4;
  background: rgba(255, 77, 46, 0.85);
  border-radius: 8px;
  padding: 1px 7px;
  margin-bottom: 5px;
}

.hb-title {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  line-height: 1.35;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);
}

.hb-sub {
  margin-top: 3px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.78);
}

.hb-cta {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 6px;
  font-size: 11px;
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 10px;
  padding: 2px 9px;
}

.hb-dots {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 4px;
}

.hb-dots i {
  width: 6px;
  height: 6px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.45);
  transition: width 0.2s, background 0.2s;
}

.hb-dots i.on {
  width: 14px;
  background: #fff;
}
</style>
