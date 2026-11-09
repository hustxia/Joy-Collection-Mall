<template>
  <div class="home">
    <!-- 欢迎横幅 -->
    <div class="hero">
      <div class="hero-text">
        <div class="hero-title">{{ greeting }}，{{ userName }}</div>
        <div class="hero-sub">欢迎回到商城后台，今天也元气满满地运营吧</div>
      </div>
      <div class="hero-deco"></div>
    </div>

    <!-- 数据概览 -->
    <div class="stats">
      <div v-for="card in stats" :key="card.label" class="stat-card" :class="'stat-' + card.tone">
        <div class="stat-icon"><i :class="card.icon"></i></div>
        <div class="stat-meta">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="quick card">
      <div class="card-head">
        <span class="card-title">快捷入口</span>
      </div>
      <div class="quick-grid">
        <div
          v-for="item in entries"
          :key="item.path"
          class="quick-item"
          @click="$router.push(item.path)"
        >
          <i class="quick-icon" :class="item.icon"></i>
          <span class="quick-name">{{ item.title }}</span>
        </div>
      </div>
    </div>

    <!-- 连通性 -->
    <div class="conn card">
      <div class="card-head">
        <span class="card-title">系统状态</span>
        <el-button type="text" size="small" @click="ping">重新检测</el-button>
      </div>
      <div class="conn-row">
        <span class="conn-dot" :class="ok ? 'is-ok' : 'is-bad'"></span>
        <span>{{ ok ? '后端服务连通正常' : (result || '点击「重新检测」验证后端连通性') }}</span>
      </div>
    </div>
  </div>
</template>

<script>
import request, { toPageData } from '@/utils/request'

function totalOf(url) {
  return request({ url, method: 'post', data: { pageNum: 1, pageSize: 1 } })
    .then(toPageData)
    // toPageData 返回的是 {code,msg,data:{list,total}}，total 在 data 里，
    // 直接取 d.total 永远 undefined，四个指标卡会恒为 0
    .then(d => (d && d.data && d.data.total) || 0)
    .catch(() => null)
}

export default {
  name: 'Home',
  data() {
    return {
      userName: localStorage.getItem('username') || '管理员',
      result: '',
      ok: false,
      stats: [
        { label: '在售商品', value: '-', icon: 'el-icon-goods', tone: 'blue', url: '/api/mall/mall_goods/page' },
        { label: '订单总量', value: '-', icon: 'el-icon-s-order', tone: 'green', url: '/api/mall/mall_order/page' },
        { label: '注册会员', value: '-', icon: 'el-icon-user', tone: 'purple', url: '/api/mall/mall_member/page' },
        { label: '进行中优惠券', value: '-', icon: 'el-icon-present', tone: 'orange', url: '/api/mall/mall_coupon/page' }
      ]
    }
  },
  computed: {
    greeting() {
      const h = new Date().getHours()
      if (h < 6) return '夜深了'
      if (h < 12) return '早上好'
      if (h < 14) return '中午好'
      if (h < 18) return '下午好'
      return '晚上好'
    },
    entries() {
      return [
        { path: '/mall/product/mall_goods/list', title: '商品管理', icon: 'el-icon-goods' },
        { path: '/mall/order/mall_order/list', title: '订单管理', icon: 'el-icon-s-order' },
        { path: '/mall/member/mall_member/list', title: '会员管理', icon: 'el-icon-user' },
        { path: '/mall/marketing/mall_coupon/list', title: '优惠券', icon: 'el-icon-present' },
        { path: '/mall/marketing/mall_banner/list', title: '轮播图', icon: 'el-icon-picture-outline' },
        { path: '/mall/product/mall_brand/list', title: '品牌', icon: 'el-icon-s-shop' }
      ]
    }
  },
  mounted() {
    this.loadStats()
  },
  methods: {
    async loadStats() {
      const results = await Promise.all(this.stats.map(s => totalOf(s.url)))
      this.stats = this.stats.map((s, i) => ({ ...s, value: results[i] === null ? '—' : String(results[i]) }))
    },
    ping() {
      this.result = '检测中…'
      request({ url: '/health', method: 'get' })
        .then(res => {
          this.ok = true
          this.result = '连通正常：' + JSON.stringify(res.data)
        })
        .catch(() => {
          this.ok = false
          this.result = '请求失败，请确认后端服务已启动'
        })
    }
  }
}
</script>

<style scoped>
.home { max-width: 1200px; }

/* 欢迎横幅 */
.hero {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  min-height: 108px;
  padding: 0 28px;
  margin-bottom: 16px;
  border-radius: 14px;
  background: linear-gradient(120deg, #4d7cfe 0%, #6f5bf0 55%, #8e5bf0 100%);
  color: #fff;
  box-shadow: 0 8px 24px rgba(77, 124, 254, 0.28);
}
.hero-title { font-size: 20px; font-weight: 600; }
.hero-sub { margin-top: 6px; font-size: 13px; opacity: 0.85; }
.hero-deco {
  position: absolute;
  right: -40px;
  top: -60px;
  width: 240px;
  height: 240px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}
.hero-deco::after {
  content: '';
  position: absolute;
  left: 60px;
  top: 110px;
  width: 130px;
  height: 130px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}

/* 统计卡片 */
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.05);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(15, 23, 42, 0.09); }
.stat-icon {
  width: 46px;
  height: 46px;
  line-height: 46px;
  text-align: center;
  border-radius: 12px;
  font-size: 22px;
  flex: 0 0 46px;
}
.stat-blue .stat-icon { background: #eef2ff; color: #4d7cfe; }
.stat-green .stat-icon { background: #e8f7ef; color: #22a06b; }
.stat-purple .stat-icon { background: #f3efff; color: #7c5bf0; }
.stat-orange .stat-icon { background: #fff3e8; color: #f08c2e; }
.stat-value { font-size: 22px; font-weight: 700; color: #1f2937; line-height: 1.2; }
.stat-label { margin-top: 3px; font-size: 12px; color: #8a93a6; }

/* 通用卡片 */
.card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.05);
  padding: 16px 20px;
  margin-bottom: 16px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.card-title { font-size: 14px; font-weight: 600; color: #1f2937; }

/* 快捷入口 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 6px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.16s ease, transform 0.16s ease;
}
.quick-item:hover { background: #f4f7fe; transform: translateY(-2px); }
.quick-icon {
  width: 42px;
  height: 42px;
  line-height: 42px;
  text-align: center;
  border-radius: 12px;
  background: #f0f4ff;
  color: #4d7cfe;
  font-size: 20px;
}
.quick-name { font-size: 12.5px; color: #4b5563; }

/* 连通性 */
.conn-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #5a6478;
}
.conn-dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 8px; }
.conn-dot.is-ok { background: #22a06b; box-shadow: 0 0 6px rgba(34, 160, 107, 0.6); }
.conn-dot.is-bad { background: #e5484d; }

@media (max-width: 1100px) {
  .stats { grid-template-columns: repeat(2, 1fr); }
  .quick-grid { grid-template-columns: repeat(3, 1fr); }
}
</style>
