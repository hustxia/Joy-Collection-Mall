<template>
  <div class="page-wrap">
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">我的拼团</span>
        <a class="gr-my-link" @click="$router.push('/group')">去拼团 ›</a>
      </div>

      <div v-loading="loading">
        <div v-if="rows.length" class="gr-list">
          <div v-for="t in rows" :key="t.teamId" class="gr-row">
            <pc-image
              :src="thumb(t.mainImage, 200)"
              :img-style="{ width: '80px', height: '80px', objectFit: 'cover', display: 'block', borderRadius: '6px' }"
            />
            <div class="gr-mid">
              <div class="gr-name">{{ t.goodsName }}</div>
              <div class="gr-sub">
                <el-tag size="mini" :type="teamTagType(t.teamStatus)">{{ teamStatusText(t.teamStatus) }}</el-tag>
                <el-tag size="mini" :type="t.isLeader === 1 ? 'danger' : 'info'" effect="plain">
                  {{ t.isLeader === 1 ? '我是团长' : '我是团员' }}
                </el-tag>
                <span class="gr-size">{{ t.currentSize }} / {{ t.groupSize }} 人</span>
              </div>
              <div class="gr-time">{{ shortTime(t.expireTime) }} 前有效</div>
            </div>
            <div class="gr-right">
              <div class="gr-price">¥{{ $price(t.groupPrice) }}</div>
              <el-button v-if="t.orderId" size="mini" type="text" @click="$router.push(`/order/detail/${t.orderId}`)">
                查看订单
              </el-button>
            </div>
          </div>
        </div>
        <el-empty v-else-if="loaded" description="还没有参与过拼团" />
      </div>
    </div>
  </div>
</template>

<script>
import { getGroupMine } from '@/api/marketing'
import { shortTime, thumb } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'

// 团状态：0拼团中 1已成团 2已失败
const TEAM_STATUS = { 0: { text: '拼团中', tag: 'warning' }, 1: { text: '已成团', tag: 'success' }, 2: { text: '已失败', tag: 'info' } }

export default {
  name: 'GroupMine',
  components: { PcImage },
  data() {
    return { rows: [], loading: false, loaded: false }
  },
  created() {
    this.load()
  },
  methods: {
    thumb,
    shortTime,
    teamStatusText(s) {
      const t = TEAM_STATUS[s]
      return t ? t.text : '未知'
    },
    teamTagType(s) {
      const t = TEAM_STATUS[s]
      return t ? t.tag : 'info'
    },
    async load() {
      this.loading = true
      try {
        this.rows = (await getGroupMine()) || []
      } catch (e) {
        this.rows = []
      } finally {
        this.loading = false
        this.loaded = true
      }
    }
  }
}
</script>

<style scoped>
.gr-my-link {
  font-size: 13px;
  color: #e1251b;
}
.gr-list {
  display: flex;
  flex-direction: column;
}
.gr-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f0;
}
.gr-row:last-child {
  border-bottom: none;
}
.gr-mid {
  flex: 1;
  min-width: 0;
}
.gr-name {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gr-sub {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.gr-size {
  font-size: 12px;
  color: #606266;
}
.gr-time {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
.gr-right {
  text-align: right;
}
.gr-price {
  color: #e1251b;
  font-weight: 700;
  font-size: 16px;
}
</style>
