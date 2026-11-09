<template>
  <div class="page-wrap">
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">拼团专区</span>
        <a class="gr-my-link" @click="$router.push('/group/my')">我的拼团 ›</a>
      </div>

      <div v-loading="loading">
        <div v-if="activities.length" class="gr-grid">
          <div v-for="a in activities" :key="a.id" class="gr-card" @click="goDetail(a)">
            <pc-image
              :src="thumb(a.mainImage, 300)"
              :img-style="{ width: '100%', height: '180px', objectFit: 'cover', display: 'block' }"
            />
            <div class="gr-body">
              <div class="gr-name">{{ a.goodsName }}</div>
              <div class="gr-price-row">
                <span class="gr-price"><span class="yen">¥</span>{{ $price(a.groupPrice) }}</span>
                <span class="gr-origin">¥{{ $price(a.originPrice) }}</span>
              </div>
              <div class="gr-sub">
                <el-tag size="mini" type="danger" effect="plain">{{ a.groupSize }}人团</el-tag>
                <span class="gr-time">{{ shortTime(a.endTime) }} 截止</span>
              </div>
              <el-button type="danger" size="small" round plain class="gr-btn">去拼团</el-button>
            </div>
          </div>
        </div>
        <el-empty v-else-if="loaded" description="暂无进行中的拼团活动" />
      </div>
    </div>
  </div>
</template>

<script>
import { getGroupActivities } from '@/api/marketing'
import { shortTime, thumb } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'

export default {
  name: 'GroupList',
  components: { PcImage },
  data() {
    return { activities: [], loading: false, loaded: false }
  },
  created() {
    this.load()
  },
  methods: {
    thumb,
    shortTime,
    async load() {
      this.loading = true
      try {
        this.activities = (await getGroupActivities()) || []
      } catch (e) {
        this.activities = []
      } finally {
        this.loading = false
        this.loaded = true
      }
    },
    goDetail(a) {
      this.$router.push(`/group/${a.id}`)
    }
  }
}
</script>

<style scoped>
.gr-my-link {
  font-size: 13px;
  color: #e1251b;
}
.gr-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.gr-card {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.gr-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.gr-body {
  padding: 12px;
}
.gr-name {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gr-price-row {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.gr-price {
  color: #e1251b;
  font-size: 18px;
  font-weight: 700;
}
.gr-price .yen {
  font-size: 12px;
}
.gr-origin {
  color: #c0c4cc;
  text-decoration: line-through;
  font-size: 12px;
}
.gr-sub {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.gr-time {
  font-size: 12px;
  color: #909399;
}
.gr-btn {
  margin-top: 10px;
  width: 100%;
}
</style>
