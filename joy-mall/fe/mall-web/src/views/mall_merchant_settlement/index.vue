<template>
  <div class="app-container">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">商家结算</h2>
        <p class="page-desc">订单确认收货时按费率自动分账落「待出账」明细，本页把待出账明细归集成结算单并支持明细下钻</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 张结算单</span>
        <el-button v-hasPermi="['mall_mall_merchant_settlement:settle']" type="primary" size="small" icon="el-icon-s-order" @click="openBuild">生成结算单</el-button>
      </div>
    </div>

    <!-- 筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="small" @submit.prevent>
        <el-form-item label="商家">
          <el-select v-model="query.merchantId" placeholder="全部商家" clearable filterable style="width: 220px" @change="handleSearch">
            <el-option v-for="m in merchantOptions" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option label="待结算" :value="0" />
            <el-option label="已结算" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <template #header><div class="card-header">
        <div class="card-title">
          <span class="title">结算单</span>
          <span class="subtitle">共 {{ total }} 张</span>
        </div>
        <span class="hint">实结金额 = 商家应收 − 退款冲减；抽成 = 实付 × 费率（‰）</span>
      </div></template>

      <el-table
        v-loading="loading"
        :data="list"
        class="data-table"
        stripe
        header-cell-class-name="table-head-cell"
        empty-text="暂无结算单"
      >
        <el-table-column prop="settlementNo" label="结算单号" min-width="180" show-overflow-tooltip />
        <el-table-column label="商家" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ merchantName(row.merchantId) }}</template>
        </el-table-column>
        <el-table-column label="结算期间" min-width="230" align="center">
          <template #default="{ row }">
            <span class="period">{{ fmt(row.periodStart) }} ~ {{ fmt(row.periodEnd) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderCount" label="订单数" width="90" align="center" />
        <el-table-column label="商家应收" width="120" align="right">
          <template #default="{ row }">¥{{ yuan(row.goodsAmount) }}</template>
        </el-table-column>
        <el-table-column label="平台抽成" width="120" align="right">
          <template #default="{ row }"><span class="muted-money">¥{{ yuan(row.platformFee) }}</span></template>
        </el-table-column>
        <el-table-column label="退款冲减" width="120" align="right">
          <template #default="{ row }"><span class="muted-money">¥{{ yuan(row.refundAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="实结金额" width="130" align="right">
          <template #default="{ row }"><span class="money-cell">¥{{ yuan(row.actualAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag size="mini" :type="Number(row.status) === 1 ? 'success' : 'warning'">
              {{ Number(row.status) === 1 ? '已结算' : '待结算' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" width="160" align="center">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="text" @click="openDetails(row)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :page-size="query.size"
        v-model:current-page="query.page"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
    </el-card>

    <!-- 生成结算单 -->
    <el-dialog title="生成结算单" v-model="buildVisible" width="520px" top="12vh">
      <el-form label-width="96px" size="small">
        <el-form-item label="商家">
          <el-select v-model="buildForm.merchantId" placeholder="选择商家" filterable style="width: 100%">
            <el-option v-for="m in merchantOptions" :key="m.id" :label="`${m.name}（${m.merchantNo || m.id}）`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="结算期间">
          <el-date-picker
            v-model="buildRange"
            type="daterange"
            value-format="yyyy-MM-dd"
            range-separator="至"
            start-placeholder="开始日"
            end-placeholder="结束日"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <div class="build-tip">
        期间留空时按该商家待出账明细的实际时间范围自动取；只归集尚未出账的明细，已出账不会重复结算。
      </div>
      <template #footer><div>
        <el-button size="small" @click="buildVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submitBuild">生成</el-button>
      </div></template>
    </el-dialog>

    <!-- 明细下钻 -->
    <el-dialog :title="`结算明细 · ${detailRow.settlementNo || ''}`" v-model="detailVisible" width="900px" top="8vh">
      <div class="summary">
        <span>订单数 <b>{{ detailRow.orderCount || 0 }}</b></span>
        <span>商家应收 <b class="money-cell">¥{{ yuan(detailRow.goodsAmount) }}</b></span>
        <span>平台抽成 <b>¥{{ yuan(detailRow.platformFee) }}</b></span>
        <span>退款冲减 <b>¥{{ yuan(detailRow.refundAmount) }}</b></span>
        <span>实结 <b class="money-cell">¥{{ yuan(detailRow.actualAmount) }}</b></span>
      </div>
      <el-table v-loading="detailLoading" :data="details" class="data-table" stripe size="mini" empty-text="暂无明细">
        <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
        <el-table-column label="实付" width="110" align="right">
          <template #default="{ row }">¥{{ yuan(row.payAmount) }}</template>
        </el-table-column>
        <el-table-column label="费率" width="90" align="center">
          <template #default="{ row }">{{ rateText(row.settleRate) }}</template>
        </el-table-column>
        <el-table-column label="商家应收" width="120" align="right">
          <template #default="{ row }">¥{{ yuan(row.goodsAmount) }}</template>
        </el-table-column>
        <el-table-column label="平台抽成" width="120" align="right">
          <template #default="{ row }">¥{{ yuan(row.platformFee) }}</template>
        </el-table-column>
        <el-table-column label="退款冲减" width="120" align="right">
          <template #default="{ row }">
            <span :class="{ 'money-cell': Number(row.refundAmount) > 0 }">¥{{ yuan(row.refundAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="出账时间" width="160" align="center" />
      </el-table>
      <template #footer><div>
        <el-button size="small" @click="detailVisible = false">关闭</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { getMallMerchantSettlementPage, buildMallMerchantSettlement, getMallMerchantSettlementDetails } from '@/api/mall_merchant_settlement'
import { getMallMerchantOptions, getMallMerchantPage } from '@/api/mall_merchant'

export default {
  name: 'MallMerchantSettlement',
  data() {
    return {
      loading: false,
      acting: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10, merchantId: undefined, status: undefined },
      merchantOptions: [],
      // merchantId -> name（含停用/驳回商家，下拉只给启用中的，历史单据仍要显示名字）
      merchantNames: {},
      buildVisible: false,
      buildForm: { merchantId: undefined },
      buildRange: [],
      detailVisible: false,
      detailLoading: false,
      detailRow: {},
      details: []
    }
  },
  created() {
    this.loadMerchants()
    this.fetchList()
  },
  methods: {
    yuan(fen) {
      const n = Number(fen)
      return Number.isFinite(n) ? (n / 100).toFixed(2) : '0.00'
    },
    /** 费率 ‰ → 百分比（后端历史单据可能为空，此时代表平台自营 0 费率） */
    rateText(rate) {
      const n = Number(rate)
      return Number.isFinite(n) ? `${(n / 10).toFixed(1)}%` : '—'
    },
    /** LocalDateTime → 展示串（去掉 T，秒可选） */
    fmt(v) {
      if (!v) return '—'
      return String(v).replace('T', ' ').slice(0, 19)
    },
    merchantName(id) {
      if (!id) return '—'
      return this.merchantNames[id] || `商家 #${id}`
    },
    async loadMerchants() {
      try {
        const res = await getMallMerchantOptions()
        this.merchantOptions = res.data || []
      } catch (e) {
        this.merchantOptions = []
      }
      // 下拉只含启用商家，另取一页全量补齐名字映射
      try {
        const page = await getMallMerchantPage({ page: 1, size: 200 })
        const map = {}
        ;(page.data.list || []).forEach(m => { map[String(m.id)] = m.name })
        this.merchantNames = map
      } catch (e) {
        this.merchantNames = {}
      }
    },
    handleSearch() {
      this.query.page = 1
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: this.query.size, merchantId: undefined, status: undefined }
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
        const res = await getMallMerchantSettlementPage({
          page: this.query.page,
          size: this.query.size,
          merchantId: this.query.merchantId,
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
    },
    openBuild() {
      this.buildForm = { merchantId: undefined }
      this.buildRange = []
      this.buildVisible = true
    },
    async submitBuild() {
      if (!this.buildForm.merchantId) {
        this.$message.warning('请选择商家')
        return
      }
      const payload = { merchantId: this.buildForm.merchantId }
      if (this.buildRange && this.buildRange.length === 2) {
        // 后端 LocalDateTime 走 ISO 解析，必须 T 分隔
        payload.periodStart = `${this.buildRange[0]}T00:00:00`
        payload.periodEnd = `${this.buildRange[1]}T23:59:59`
      }
      this.acting = true
      try {
        const res = await buildMallMerchantSettlement(payload)
        const s = (res && res.data) || {}
        this.$message.success(`已生成结算单 ${s.settlementNo || ''}，实结 ¥${this.yuan(s.actualAmount)}`)
        this.buildVisible = false
        this.handleSearch()
      } finally {
        this.acting = false
      }
    },
    async openDetails(row) {
      this.detailRow = row
      this.details = []
      this.detailVisible = true
      this.detailLoading = true
      try {
        const res = await getMallMerchantSettlementDetails(row.id)
        this.details = (res && res.data) || []
      } finally {
        this.detailLoading = false
      }
    }
  }
}
</script>

<style scoped>
.app-container {
  padding: 0;
  background: transparent;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
  letter-spacing: 0.2px;
}

.page-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: #8a94a6;
}

.page-head__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 4px;
}

.meta-pill {
  padding: 4px 12px;
  border-radius: 20px;
  background: #eef2ff;
  color: #4d7cfe;
  font-size: 12px;
  font-weight: 500;
}

.filter-card,
.table-card {
  border: none;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04), 0 6px 20px rgba(16, 24, 40, 0.05);
}

.filter-card {
  margin-bottom: 16px;
}

.filter-card :deep(.el-card__body){
  padding: 18px 18px 4px;
}

.filter-card :deep(.el-form-item){
  margin-right: 16px;
  margin-bottom: 14px;
}

.table-card :deep(.el-card__header){
  padding: 16px 18px;
  border-bottom: 1px solid #eef1f7;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  display: flex;
  align-items: baseline;
}

.card-title .title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.card-title .subtitle {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.hint {
  font-size: 12px;
  color: #a0a6b3;
}

.data-table :deep(.table-head-cell){
  background: #f7f9fc !important;
  color: #5c6270;
  font-weight: 600;
}

.data-table :deep(td){
  padding: 11px 0;
}

.data-table :deep(.el-table__row:hover > td){
  background: #f5f9ff;
}

.period {
  font-size: 12px;
  color: #5a6478;
}

.money-cell {
  color: #e1251b;
  font-weight: 600;
}

.muted-money {
  color: #5a6478;
}

.pagination {
  margin-top: 18px;
  text-align: right;
}

.build-tip {
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f9fc;
  font-size: 12px;
  color: #8a94a6;
  line-height: 1.6;
}

.summary {
  display: flex;
  flex-wrap: wrap;
  gap: 22px;
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f7f9fc;
  font-size: 13px;
  color: #5a6478;
}

.summary b {
  color: #111827;
  margin-left: 4px;
}
</style>
