<template>
  <div class="app-container">
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">支付对账</h2>
        <p class="page-desc">按日把渠道账单与本地支付单逐笔比对，只落差异行。同日同渠道可重复执行：已处理的差异保留，未处理的会按本次结果重建。</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 条差异</span>
        <el-button v-hasPermi="['mall_mall_pay_reconcile:run']" type="primary" size="small" icon="el-icon-refresh-right" @click="openRun">执行对账</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="small" @submit.prevent>
        <el-form-item label="账单日期">
          <el-date-picker v-model="query.billDate" type="date" value-format="yyyy-MM-dd" placeholder="全部" clearable style="width: 160px" @change="handleSearch" />
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="query.channel" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option label="沙箱" value="sandbox" />
            <el-option label="微信" value="wxpay" />
            <el-option label="支付宝" value="alipay" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="query.handleStatus" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option label="待处理" :value="0" />
            <el-option label="已确认" :value="1" />
            <el-option label="已忽略" :value="2" />
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
          <span class="title">对账差异</span>
          <span class="subtitle">共 {{ total }} 条</span>
        </div>
        <span class="hint">长款/短款 = 金额对不上；渠道账单缺失 / 本地掉单 = 单边有单</span>
      </div></template>

      <el-table v-loading="loading" :data="list" class="data-table" stripe empty-text="暂无差异，账目一致">
        <el-table-column prop="billDate" label="账单日期" width="120" align="center" />
        <el-table-column prop="channel" label="渠道" width="100" align="center" />
        <el-table-column prop="payNo" label="支付单号" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.payNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="channelTradeNo" label="渠道交易号" min-width="190" show-overflow-tooltip />
        <el-table-column label="渠道金额" width="110" align="right">
          <template #default="{ row }">{{ row.channelAmount === null ? '—' : '¥' + yuan(row.channelAmount) }}</template>
        </el-table-column>
        <el-table-column label="本地金额" width="110" align="right">
          <template #default="{ row }">{{ row.localAmount === null ? '—' : '¥' + yuan(row.localAmount) }}</template>
        </el-table-column>
        <el-table-column label="差异类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag size="mini" type="danger">{{ diffText(row.diffType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="mini" :type="handleTag(row.handleStatus)">{{ handleText(row.handleStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="Number(row.handleStatus) === 0">
              <el-button v-hasPermi="['mall_mall_pay_reconcile:run']" type="text" @click="handle(row, 1)">确认</el-button>
              <el-button v-hasPermi="['mall_mall_pay_reconcile:run']" type="text" @click="handle(row, 2)">忽略</el-button>
            </template>
            <span v-else class="muted">{{ row.handleRemark || '已处理' }}</span>
          </template>
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

    <el-dialog title="执行对账" v-model="runVisible" width="620px">
      <el-form label-width="96px" size="small">
        <el-form-item label="账单日期">
          <el-date-picker v-model="runForm.billDate" type="date" value-format="yyyy-MM-dd" placeholder="默认今天" style="width: 180px" />
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="runForm.channel" style="width: 180px">
            <el-option label="沙箱" value="sandbox" />
            <el-option label="微信" value="wxpay" />
            <el-option label="支付宝" value="alipay" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道账单">
          <el-input
            v-model="runForm.billsText"
            type="textarea"
            :rows="5"
            placeholder="选填：每行 渠道交易号,金额(分)。留空则用本地镜像对账（差异恒为 0）"
          />
        </el-form-item>
      </el-form>
      <div v-if="runResult" class="run-result">
        账单 {{ runResult.billCount }} 笔 · 本地 {{ runResult.localCount }} 笔 · 差异
        <b :class="runResult.diffCount ? 'bad' : 'ok'">{{ runResult.diffCount }}</b> 笔
      </div>
      <template #footer><div>
        <el-button size="small" @click="runVisible = false">关闭</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submitRun">开始对账</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { getMallPayReconcilePage, runMallPayReconcile, handleMallPayReconcile } from '@/api/mall_payment'

export default {
  name: 'MallPayReconcile',
  data() {
    return {
      loading: false,
      acting: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10, billDate: undefined, channel: undefined, handleStatus: undefined },
      runVisible: false,
      runForm: { billDate: undefined, channel: 'sandbox', billsText: '' },
      runResult: null
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    yuan(fen) {
      const n = Number(fen)
      return Number.isFinite(n) ? (n / 100).toFixed(2) : '0.00'
    },
    diffText(type) {
      const n = Number(type)
      if (n === 1) return '本地多记（长款）'
      if (n === 2) return '本地少记（短款）'
      if (n === 3) return '渠道账单缺失'
      return '本地掉单'
    },
    handleText(status) {
      const n = Number(status)
      if (n === 1) return '已确认'
      if (n === 2) return '已忽略'
      return '待处理'
    },
    handleTag(status) {
      const n = Number(status)
      if (n === 1) return 'success'
      if (n === 2) return 'info'
      return 'warning'
    },
    handleSearch() {
      this.query.page = 1
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: this.query.size, billDate: undefined, channel: undefined, handleStatus: undefined }
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
        const res = await getMallPayReconcilePage({
          page: this.query.page,
          size: this.query.size,
          billDate: this.query.billDate || undefined,
          channel: this.query.channel,
          handleStatus: this.query.handleStatus
        })
        this.list = (res.data && res.data.list) || []
        this.total = Number((res.data && res.data.total) || 0)
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    openRun() {
      this.runForm = { billDate: undefined, channel: 'sandbox', billsText: '' }
      this.runResult = null
      this.runVisible = true
    },
    async submitRun() {
      const bills = this.runForm.billsText
        .split('\n')
        .map(l => l.trim())
        .filter(Boolean)
        .map(line => {
          const [channelTradeNo, amount] = line.split(',').map(s => (s || '').trim())
          return { channelTradeNo, amount: Number(amount || 0) }
        })
      this.acting = true
      try {
        const res = await runMallPayReconcile({
          billDate: this.runForm.billDate || undefined,
          channel: this.runForm.channel,
          bills
        })
        this.runResult = res.data
        this.fetchList()
      } finally {
        this.acting = false
      }
    },
    async handle(row, handleStatus) {
      const label = handleStatus === 1 ? '确认' : '忽略'
      await this.$confirm(`将这笔差异标记为「${label}」？`, '提示', { type: 'warning' })
      await handleMallPayReconcile(row.id, handleStatus, `${label}（操作人）`)
      this.$message.success('已处理')
      this.fetchList()
    }
  }
}
</script>

<style scoped>
.run-result {
  margin-top: 10px;
  font-size: 13px;
}
.run-result .ok {
  color: #67c23a;
}
.run-result .bad {
  color: #f56c6c;
}
.muted {
  color: #c0c4cc;
  font-size: 12px;
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
