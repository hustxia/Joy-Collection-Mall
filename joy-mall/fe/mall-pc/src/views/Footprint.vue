<template>
  <div class="page-wrap">
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">我的足迹</span>
        <el-button size="small" :disabled="!list.length" @click="onClear">清空足迹</el-button>
      </div>

      <div v-loading="loading">
        <div v-for="it in list" :key="it.goodsId" class="fp-card" @click="goGoods(it.goodsId)">
          <pc-image
            :src="thumbUrl(it.mainImage)"
            :img-style="{ width: '96px', height: '96px', objectFit: 'cover', display: 'block', borderRadius: '6px' }"
          />
          <div class="fp-main">
            <div class="fp-name">{{ it.goodsName || '商品已下架' }}</div>
            <div class="fp-price">¥{{ $price(it.priceMin) }}</div>
          </div>
          <div class="fp-time">{{ shortTime(it.viewTime) }}</div>
        </div>
        <el-empty v-if="!loading && !list.length" description="还没有浏览记录" :image-size="90" />
      </div>

      <div v-if="total > query.pageSize" style="text-align:right;margin-top:12px">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          v-model:current-page="query.pageNum"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script>
import { getFootprintList, clearFootprint } from '@/api/footprint'
import { thumb, shortTime as formatTime } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'

export default {
  name: 'Footprint',
  components: { PcImage },
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      query: { pageNum: 1, pageSize: 10 }
    }
  },
  created() {
    this.load()
  },
  methods: {
    thumbUrl(src) {
      return thumb(src, 200)
    },
    shortTime(v) {
      return formatTime(v)
    },
    async load() {
      this.loading = true
      try {
        const data = await getFootprintList(this.query)
        this.list = (data && data.list) || []
        this.total = (data && data.total) || 0
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    onClear() {
      this.$confirm('确定清空全部浏览记录吗？', '清空足迹', { type: 'warning' })
        .then(async () => {
          const data = await clearFootprint()
          this.$message.success(`已清空 ${(data && data.cleared) || 0} 条`)
          this.query.pageNum = 1
          this.load()
        })
        .catch(() => {})
    },
    goGoods(id) {
      this.$router.push(`/goods/${id}`)
    }
  }
}
</script>

<style scoped>
.fp-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 6px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}
.fp-card:last-child {
  border-bottom: none;
}
.fp-main {
  flex: 1;
  min-width: 0;
}
.fp-name {
  font-size: 15px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fp-price {
  margin-top: 8px;
  color: #e1251b;
  font-size: 18px;
  font-weight: 600;
}
.fp-time {
  font-size: 12px;
  color: #909399;
  flex: none;
}
</style>
