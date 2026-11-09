<template>
  <div class="page-wrap">
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">我的收藏</span>
        <span style="font-size:13px;color:#909399">共 {{ total }} 件</span>
      </div>

      <div v-loading="loading">
        <div v-for="it in list" :key="it.id" class="fv-card">
          <pc-image
            :src="thumbUrl(it.mainImage)"
            :img-style="{ width: '96px', height: '96px', objectFit: 'cover', display: 'block', borderRadius: '6px', cursor: 'pointer' }"
            @click="goGoods(it.goodsId)"
          />
          <div class="fv-main" @click="goGoods(it.goodsId)">
            <div class="fv-name">{{ it.goodsName || '商品已下架' }}</div>
            <div class="fv-price">¥{{ $price(it.priceMin) }}</div>
          </div>
          <el-button size="small" plain @click="onCancel(it)">取消收藏</el-button>
        </div>
        <el-empty v-if="!loading && !list.length" description="还没有收藏商品" :image-size="90" />
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
import { getFavoriteList, toggleFavorite } from '@/api/favorite'
import { thumb } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'

export default {
  name: 'Favorite',
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
    async load() {
      this.loading = true
      try {
        const data = await getFavoriteList(this.query)
        this.list = (data && data.list) || []
        this.total = (data && data.total) || 0
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    async onCancel(it) {
      await toggleFavorite(it.goodsId)
      this.$message.success('已取消收藏')
      this.total = Math.max(this.total - 1, 0)
      this.load()
    },
    goGoods(id) {
      if (id) this.$router.push(`/goods/${id}`)
    }
  }
}
</script>

<style scoped>
.fv-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 6px;
  border-bottom: 1px solid #f0f0f0;
}
.fv-card:last-child {
  border-bottom: none;
}
.fv-main {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.fv-name {
  font-size: 15px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fv-price {
  margin-top: 8px;
  color: #e1251b;
  font-size: 18px;
  font-weight: 600;
}
</style>
