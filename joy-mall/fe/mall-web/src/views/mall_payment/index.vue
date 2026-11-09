<template>
  <div class="app-container">
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">支付流水</h2>
        <p class="page-desc">一次支付尝试一条支付单：下单→预支付→渠道回调→推进订单。同一回调重放不会重复推进，渠道交易号即对账依据。</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 笔</span>
        <el-tag v-for="c in channels" :key="c.code" size="small" :type="c.configured ? 'success' : 'info'" class="channel-tag">
          {{ c.code }} · {{ c.text }}
        </el-tag>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="small" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="支付单号 / 订单号" clearable style="width: 220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="query.channel" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option label="沙箱" value="sandbox" />
            <el-option label="微信" value="wxpay" />
            <el-option label="支付宝" value="alipay" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option label="待支付" :value="0" />
            <el-option label="支付成功" :value="1" />
            <el-option label="支付失败" :value="2" />
            <el-option label="已全额退款" :value="3" />
            <el-option label="部分退款" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header><div class="card-header">
        <div class="card-title">
          <span class="title">支付单</span>
          <span class="subtitle">共 {{ total }} 笔</span>
        </div>
        <span class="hint">微信/支付宝未配置商户资质时不可用，沙箱渠道用于本地联调</span>
      </div></template>

      <el-table v-loading="loading" :data="list" class="data-table" stripe empty-text="暂无支付流水">
        <el-table-column prop="payNo" label="支付单号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="渠道" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="mini">{{ row.channel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tradeType" label="类型" width="90" align="center" />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }"><span class="money-cell">¥{{ yuan(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="mini" :type="statusTag(row.status)">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="channelTradeNo" label="渠道交易号" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.channelTradeNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="回调时间" width="160" align="center">
          <template #default="{ row }">{{ fmt(row.notifyTime) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160" align="center">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :page-size="query.size"
        v-model:current-page="query.page"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
    </el-card>
  </div>
</template>

<script>
import { getMallPaymentPage, getMallPaymentChannels } from '@/api/mall_payment'

export default {
  name: 'MallPayment',
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      channels: [],
      query: { page: 1, size: 10, keyword: '', channel: undefined, status: undefined }
    }
  },
  created() {
    this.loadChannels()
    this.fetchList()
  },
  methods: {
    yuan(fen) {
      const n = Number(fen)
      return Number.isFinite(n) ? (n / 100).toFixed(2) : '0.00'
    },
    fmt(v) {
      if (!v) return '—'
      return String(v).replace('T', ' ').slice(0, 19)
    },
    statusTag(status) {
      const n = Number(status)
      if (n === 1) return 'success'
      if (n === 2) return 'danger'
      if (n === 3 || n === 4) return 'info'
      return 'warning'
    },
    async loadChannels() {
      try {
        const res = await getMallPaymentChannels()
        this.channels = res.data || []
      } catch (e) {
        this.channels = []
      }
    },
    handleSearch() {
      this.query.page = 1
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: this.query.size, keyword: '', channel: undefined, status: undefined }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.query.page = 1
      this.fetchList()
    },
    async fetchList() {
      this.loading = true
      try {
        const res = await getMallPaymentPage({
          page: this.query.page,
          size: this.query.size,
          keyword: this.query.keyword || undefined,
          channel: this.query.channel,
          status: this.query.status
        })
        this.list = (res.data && res.data.list) || []
        this.total = Number((res.data && res.data.total) || 0)
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.channel-tag {
  margin-left: 8px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title .title {
  font-weight: 600;
}
.card-title .subtitle {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.card-header .hint {
  color: #909399;
  font-size: 12px;
}
</style>
