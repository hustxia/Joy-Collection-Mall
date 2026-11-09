<template>
  <div class="page-wrap">
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">秒杀专场</span>
        <span class="sk-tip">库存有限，先到先得</span>
      </div>

      <div v-loading="loading">
        <div v-if="activities.length" class="sk-list">
          <div v-for="a in activities" :key="a.id" class="sk-card" @click="goDetail(a)">
            <pc-image
              :src="thumb(a.mainImage, 300)"
              :img-style="{ width: '220px', height: '160px', objectFit: 'cover', display: 'block', borderRadius: '8px' }"
            />
            <div class="sk-body">
              <div class="sk-name">{{ a.goodsName }}</div>
              <div class="sk-price-row">
                <span class="sk-price"><span class="yen">¥</span>{{ $price(a.seckillPrice) }}</span>
                <span class="sk-origin">¥{{ $price(a.originPrice) }}</span>
                <span class="sk-limit">限购 {{ a.perLimit }} 件</span>
              </div>
              <div class="sk-progress-row">
                <el-progress
                  class="sk-progress"
                  :percentage="progress(a)"
                  :stroke-width="10"
                  :show-text="false"
                  color="#e1251b"
                />
                <span class="sk-progress-text">已抢 {{ soldText(a) }}</span>
              </div>
              <div class="sk-foot">
                <span class="sk-countdown">距结束 <b>{{ countdownText }}</b></span>
                <el-button type="danger" size="small" round>立即抢购</el-button>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else-if="loaded && !upcoming.length" description="暂无秒杀活动" />

        <!-- 即将开始（V15）：scope=upcoming，未开抢不可下单 -->
        <template v-if="upcoming.length">
          <div class="sk-upcoming-head">即将开始 <span class="sk-upcoming-sub">共 {{ upcoming.length }} 场</span></div>
          <div class="sk-list">
            <div v-for="a in upcoming" :key="a.id" class="sk-card" @click="goDetail(a)">
              <pc-image
                :src="thumb(a.mainImage, 300)"
                :img-style="{ width: '220px', height: '160px', objectFit: 'cover', display: 'block', borderRadius: '8px' }"
              />
              <div class="sk-body">
                <div class="sk-name">{{ a.goodsName }}</div>
                <div class="sk-price-row">
                  <span class="sk-price"><span class="yen">¥</span>{{ $price(a.seckillPrice) }}</span>
                  <span class="sk-origin">¥{{ $price(a.originPrice) }}</span>
                  <span class="sk-limit">限购 {{ a.perLimit }} 件</span>
                </div>
                <div class="sk-progress-row">
                  <span class="sk-progress-text">库存 {{ a.availableStock }}/{{ a.totalStock }}</span>
                </div>
                <div class="sk-foot">
                  <span class="sk-countdown">开抢 <b class="sk-start-time">{{ (a.startTime || '').slice(0, 16) }}</b></span>
                  <el-button type="danger" size="small" round disabled>未开始</el-button>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import { getSeckillActivities } from '@/api/marketing'
import PcImage from '@/components/PcImage.vue'
import countdown from '@/utils/countdown'

export default {
  name: 'SeckillList',
  components: { PcImage },
  mixins: [countdown],
  data() {
    return { activities: [], upcoming: [], loading: false, loaded: false }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        // 进行中 + 即将开始两组并行；旧版服务无 scope 参数时 upcoming 请求返回原列表，需过滤
        const [ongoing, future] = await Promise.all([
          getSeckillActivities(),
          getSeckillActivities('upcoming').catch(() => [])
        ])
        this.activities = ongoing || []
        this.upcoming = (future || []).filter(a => !a.onSale)
      } catch (e) {
        this.activities = []
        this.upcoming = []
      } finally {
        this.loading = false
        this.loaded = true
      }
    },
    progress(a) {
      const total = Number(a.totalStock) || 0
      const remain = Number(a.availableStock) || 0
      if (total <= 0) return 0
      return Math.min(100, Math.round(((total - remain) / total) * 100))
    },
    soldText(a) {
      const total = Number(a.totalStock) || 0
      const remain = Number(a.availableStock) || 0
      return `${total - remain}/${total}`
    },
    goDetail(a) {
      this.$router.push(`/seckill/${a.id}`)
    }
  }
}
</script>

<style scoped>
.sk-tip {
  font-size: 13px;
  color: #909399;
}
.sk-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.sk-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.sk-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.sk-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.sk-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sk-price-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: 8px;
}
.sk-price {
  color: #e1251b;
  font-size: 20px;
  font-weight: 700;
}
.sk-price .yen {
  font-size: 13px;
}
.sk-origin {
  color: #c0c4cc;
  text-decoration: line-through;
  font-size: 13px;
}
.sk-limit {
  font-size: 12px;
  color: #909399;
}
.sk-progress-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}
.sk-progress {
  flex: 1;
}
.sk-progress-text {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.sk-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.sk-countdown {
  font-size: 13px;
  color: #909399;
}
.sk-countdown b {
  color: #e1251b;
  font-family: monospace;
}
.sk-upcoming-head {
  margin: 24px 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.sk-upcoming-sub {
  margin-left: 8px;
  font-size: 13px;
  font-weight: 400;
  color: #909399;
}
.sk-start-time {
  color: #4a6cf7;
}
</style>
