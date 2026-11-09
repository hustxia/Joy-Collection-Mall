<template>
  <div class="page-wrap">
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">消息中心</span>
        <div>
          <el-radio-group v-model="type" size="mini" @change="onTypeChange">
            <el-radio-button :label="0">全部</el-radio-button>
            <el-radio-button :label="1">订单</el-radio-button>
            <el-radio-button :label="2">售后</el-radio-button>
            <el-radio-button :label="3">营销</el-radio-button>
          </el-radio-group>
          <el-button size="mini" style="margin-left:10px" :disabled="!list.length" @click="onReadAll">全部已读</el-button>
        </div>
      </div>

      <div v-loading="loading">
        <div v-for="it in list" :key="it.id" class="nt-row" :class="{ unread: it.readStatus === 0 }" @click="onRead(it)">
          <div class="nt-dot" v-if="it.readStatus === 0"></div>
          <div class="nt-mid">
            <div class="nt-title">{{ it.title }}</div>
            <div class="nt-content">{{ it.content }}</div>
          </div>
          <div class="nt-time">{{ it.createTime }}</div>
        </div>
        <el-empty v-if="!loading && !list.length" description="暂无消息" :image-size="90" />
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
import { getNoticeList, readNotice, readAllNotices } from '@/api/notice'

export default {
  name: 'Notice',
  data() {
    return {
      type: 0,
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
    async load() {
      this.loading = true
      try {
        const params = { ...this.query }
        if (this.type) params.type = this.type
        const data = await getNoticeList(params)
        this.list = (data && data.list) || []
        this.total = (data && data.total) || 0
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    onTypeChange() {
      this.query.pageNum = 1
      this.load()
    },
    async onRead(it) {
      if (it.readStatus !== 0) return
      try {
        await readNotice(it.id)
        it.readStatus = 1
      } catch (e) { /* 拦截器已提示 */ }
    },
    async onReadAll() {
      const data = await readAllNotices()
      this.$message.success(`已读 ${(data && data.read) || 0} 条`)
      this.list.forEach(it => { it.readStatus = 1 })
    }
  }
}
</script>

<style scoped>
.nt-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 6px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}
.nt-row:last-child {
  border-bottom: none;
}
.nt-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e1251b;
  margin-top: 6px;
  flex: none;
}
.nt-mid {
  flex: 1;
  min-width: 0;
}
.nt-title {
  font-size: 14px;
  color: #303133;
}
.nt-row.unread .nt-title {
  font-weight: 600;
}
.nt-content {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.nt-time {
  font-size: 12px;
  color: #909399;
  flex: none;
}
</style>
