<template>
  <div class="pc-banner">
    <el-carousel
      ref="carousel"
      height="330px"
      :interval="4500"
      arrow="hover"
      indicator-position="none"
      @change="active = $event"
    >
      <el-carousel-item v-for="s in slides" :key="s.key">
        <div class="pb-slide" @click="onClick(s)">
          <pc-image
            v-if="s.image"
            :src="s.image"
            :alt="s.title"
            :img-style="{ width: '100%', height: '330px', objectFit: 'cover', display: 'block' }"
          />
          <div v-else class="pb-img pb-img--grad" :style="{ background: s.bg }">
            <i :class="s.icon"></i>
          </div>
          <div class="pb-mask"></div>
          <div class="pb-copy">
            <span class="pb-tag">{{ s.tag }}</span>
            <div class="pb-title">{{ s.title }}</div>
            <div v-if="s.sub" class="pb-sub">{{ s.sub }}</div>
            <span class="pb-cta">{{ s.cta }} ›</span>
          </div>
        </div>
      </el-carousel-item>
    </el-carousel>

    <div v-if="slides.length > 1" class="pb-dots">
      <i
        v-for="(s, i) in slides"
        :key="'dot-' + s.key"
        :class="{ on: i === active }"
        @click.stop="to(i)"
      ></i>
    </div>
  </div>
</template>

<script>
import PcImage from '@/components/PcImage.vue'

// Banner 接口无数据或失败时用活动入口兜底，保证首屏始终有轮播
const FALLBACK_SLIDES = [
  {
    key: 'f-seckill',
    tag: '限时秒杀',
    title: '每日 10 点开抢，爆款直降',
    sub: '限时低价 · 抢完即止',
    cta: '立即抢购',
    linkUrl: '/seckill',
    icon: 'el-icon-time',
    bg: 'linear-gradient(120deg,#ff7a45,#ff3b30)'
  },
  {
    key: 'f-group',
    tag: '超值拼团',
    title: '邀好友拼团，低至 5 折',
    sub: '2 人成团 · 团价更省',
    cta: '去拼团',
    linkUrl: '/group',
    icon: 'el-icon-user-solid',
    bg: 'linear-gradient(120deg,#ff9f43,#ff3d6e)'
  },
  {
    key: 'f-lottery',
    tag: '幸运抽奖',
    title: '每日免费抽，100% 中奖',
    sub: '积分 · 优惠券 · 实物好礼',
    cta: '去抽奖',
    linkUrl: '/lottery',
    icon: 'el-icon-present',
    bg: 'linear-gradient(120deg,#6a3fd6,#b04ee6)'
  }
]

const GRADIENTS = [
  'linear-gradient(120deg,#ff7a45,#ff3b30)',
  'linear-gradient(120deg,#ff9f43,#ff3d6e)',
  'linear-gradient(120deg,#6a3fd6,#b04ee6)'
]

export default {
  name: 'HomeBanner',
  components: { PcImage },
  props: {
    banners: { type: Array, default: () => [] }
  },
  data() {
    return { active: 0 }
  },
  computed: {
    slides() {
      const list = this.banners || []
      if (!list.length) return FALLBACK_SLIDES
      return list.map((b, i) => ({
        key: b.id || `banner-${i}`,
        image: b.image || '',
        tag: '悦集精选',
        title: b.title || '悦集商城',
        sub: '',
        cta: '立即查看',
        linkUrl: b.linkUrl || '',
        icon: '',
        bg: GRADIENTS[i % GRADIENTS.length]
      }))
    }
  },
  methods: {
    to(idx) {
      const carousel = this.$refs.carousel
      if (carousel && typeof carousel.setActiveItem === 'function') carousel.setActiveItem(idx)
    },
    onClick(s) {
      if (s.linkUrl) this.$emit('select', s)
    }
  }
}
</script>

<style scoped>
.pc-banner {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 8px 22px rgba(24, 32, 66, 0.1);
}

.pb-slide {
  position: relative;
  cursor: pointer;
}

.pb-img {
  width: 100%;
  height: 330px;
}

.pb-img--grad {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pb-img--grad::after {
  content: '';
  position: absolute;
  right: 40px;
  top: -70px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}

.pb-img--grad i {
  position: absolute;
  right: 96px;
  top: 84px;
  font-size: 92px;
  color: rgba(255, 255, 255, 0.28);
}

.pb-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(16, 22, 48, 0) 40%, rgba(16, 22, 48, 0.62) 100%);
}

.pb-copy {
  position: absolute;
  left: 24px;
  bottom: 20px;
  right: 90px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.pb-tag {
  font-size: 11px;
  color: #fff;
  background: rgba(225, 37, 27, 0.9);
  border-radius: 8px;
  padding: 1px 8px;
}

.pb-title {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.35);
}

.pb-sub {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
}

.pb-cta {
  margin-top: 9px;
  font-size: 12px;
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 12px;
  padding: 3px 12px;
}

.pb-dots {
  position: absolute;
  right: 18px;
  bottom: 16px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 5px;
}

.pb-dots i {
  width: 7px;
  height: 7px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: width 0.2s, background 0.2s;
}

.pb-dots i.on {
  width: 18px;
  background: #fff;
}
</style>
