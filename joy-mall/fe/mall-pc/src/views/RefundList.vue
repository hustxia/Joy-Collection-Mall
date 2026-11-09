<template>
  <div class="page-wrap">
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">我的售后</span>
        <el-button size="small" @click="$router.push('/order/list')">去订单列表申请</el-button>
      </div>

      <el-tabs v-model="active" @tab-click="onTab">
        <el-tab-pane v-for="t in tabs" :key="t.label" :label="t.label" :name="t.label" />
      </el-tabs>

      <div v-loading="loading">
        <div v-for="r in list" :key="r.id" class="rf-card" @click="goDetail(r.id)">
          <div class="rf-card-head">
            <span class="rf-no">售后单号 {{ r.refundNo }}</span>
            <el-tag size="mini" :type="refundStatusType(r.status)">{{ refundStatusText(r.status) }}</el-tag>
          </div>
          <div class="rf-card-body">
            <div class="rf-card-col">
              <div class="muted">订单号：{{ r.orderNo }}</div>
              <div class="muted">{{ refundTypeText(r.type) }} · 申请于 {{ shortTime(r.createTime) }}</div>
            </div>
            <div class="rf-card-amt">
              <div class="muted" style="font-size:12px">退款金额</div>
              <div class="money-lg" style="font-size:20px">¥{{ $price(r.amount) }}</div>
            </div>
          </div>
        </div>
        <el-empty v-if="!loading && !list.length" description="暂无售后记录" :image-size="90" />
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
import { getRefundPage } from '@/api/refund'
import { refundStatusText, refundStatusType, refundTypeText, shortTime } from '@/utils/format'

export default {
  name: 'RefundList',
  data() {
    return {
      active: '全部',
      tabs: [{ label: '全部', status: undefined }, { label: '待审核', status: 1 }, { label: '已同意', status: 2 }, { label: '已退款', status: 4 }, { label: '已拒绝', status: 3 }, { label: '已关闭', status: 5 }],
      list: [],
      total: 0,
      loading: false,
      query: { pageNum: 1, pageSize: 5 }
    }
  },
  created() {
    this.load()
  },
  methods: {
    refundStatusText,
    refundStatusType,
    refundTypeText,
    shortTime,
    onTab() {
      this.query.pageNum = 1
      this.load()
    },
    async load() {
      this.loading = true
      try {
        const tab = this.tabs.find(t => t.label === this.active)
        const params = { ...this.query }
        if (tab && tab.status !== undefined) params.status = tab.status
        const res = await getRefundPage(params)
        const page = res || {}
        this.list = page.records || []
        this.total = Number(page.total) || 0
      } finally {
        this.loading = false
      }
    },
    goDetail(id) {
      this.$router.push('/refund/detail/' + id)
    }
  }
}
</script>

<style scoped>
.rf-card { border: 1px solid #f0f0f0; border-radius: 8px; padding: 14px 18px; margin-bottom: 12px; cursor: pointer; transition: box-shadow 0.2s; }
.rf-card:hover { box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08); }
.rf-card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.rf-no { font-size: 13px; color: #606266; }
.rf-card-body { display: flex; justify-content: space-between; align-items: flex-end; }
.rf-card-amt { text-align: right; }
</style>
