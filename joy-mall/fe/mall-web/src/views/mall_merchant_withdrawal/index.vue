<template>
  <div class="app-container">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">商家提现</h2>
        <p class="page-desc">平台代商家发起提现：提交即冻结余额，审核通过标记打款，驳回则余额原路退回</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 笔提现单</span>
        <el-button v-hasPermi="['mall_mall_merchant_withdrawal:apply']" type="primary" size="small" icon="el-icon-money" @click="openApply">发起提现</el-button>
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
            <el-option v-for="(s, k) in STATUS" :key="k" :label="s.text" :value="Number(k)" />
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
          <span class="title">提现单</span>
          <span class="subtitle">共 {{ total }} 笔</span>
        </div>
        <span class="hint">“申请”与“审核中”占用余额，拒绝会退回；已打款不可回退</span>
      </div></template>

      <el-table
        v-loading="loading"
        :data="list"
        class="data-table"
        stripe
        header-cell-class-name="table-head-cell"
        empty-text="暂无提现单"
      >
        <el-table-column prop="id" label="提现单号" width="180" show-overflow-tooltip />
        <el-table-column label="商家" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ merchantName(row.merchantId) }}</template>
        </el-table-column>
        <el-table-column label="提现金额" width="130" align="right">
          <template #default="{ row }"><span class="money-cell">¥{{ yuan(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="提现方式" width="110" align="center">
          <template #default="{ row }">{{ methodText(row.method) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="mini" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditBy" label="审核人" width="120" align="center">
          <template #default="{ row }">{{ row.auditBy || '—' }}</template>
        </el-table-column>
        <el-table-column label="审核时间" width="160" align="center">
          <template #default="{ row }">{{ fmt(row.auditTime) }}</template>
        </el-table-column>
        <el-table-column label="拒绝原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.rejectReason || '—' }}</template>
        </el-table-column>
        <el-table-column label="申请时间" width="160" align="center">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="Number(row.status) === 0 || Number(row.status) === 1"
              v-hasPermi="['mall_mall_merchant_withdrawal:audit']"
              type="text"
              @click="openAudit(row)"
            >审核</el-button>
            <span v-else class="muted-cell">—</span>
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

    <!-- 发起提现 -->
    <el-dialog title="发起提现" v-model="applyVisible" width="520px" top="12vh">
      <el-form label-width="96px" size="small">
        <el-form-item label="商家">
          <el-select v-model="applyForm.merchantId" placeholder="选择商家" filterable style="width: 100%" @change="onMerchantChange">
            <el-option v-for="m in merchantOptions" :key="m.id" :label="`${m.name}（${m.merchantNo || m.id}）`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="可提现余额">
          <span class="balance">¥{{ yuan(balance) }}</span>
        </el-form-item>
        <el-form-item label="提现金额">
          <el-input-number v-model="applyForm.amountYuan" :min="0.01" :precision="2" :step="100" controls-position="right" style="width: 180px" />
          <span class="form-tip">元</span>
        </el-form-item>
        <el-form-item label="提现方式">
          <el-radio-group v-model="applyForm.method">
            <el-radio :label="0">余额</el-radio>
            <el-radio :label="1">微信</el-radio>
            <el-radio :label="2">银行卡</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div class="build-tip">提交即从可提现余额中冻结该金额；余额不足会被拒绝，审核驳回后金额原路退回。</div>
      <template #footer><div>
        <el-button size="small" @click="applyVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submitApply">提交</el-button>
      </div></template>
    </el-dialog>

    <!-- 审核 -->
    <el-dialog :title="`提现审核 · ¥${yuan(auditForm.amount)}`" v-model="auditVisible" width="480px" top="12vh">
      <div class="kv">
        <div class="kv-row"><span class="k">商家</span><span>{{ merchantName(auditForm.merchantId) }}</span></div>
        <div class="kv-row"><span class="k">提现金额</span><span class="money-cell">¥{{ yuan(auditForm.amount) }}</span></div>
        <div class="kv-row"><span class="k">提现方式</span><span>{{ methodText(auditForm.method) }}</span></div>
      </div>
      <el-form label-width="80px" size="small" style="margin-top:12px">
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditForm.pass">
            <el-radio :label="true">通过并打款</el-radio>
            <el-radio :label="false">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditForm.pass === false" label="拒绝原因">
          <el-input v-model="auditForm.rejectReason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="拒绝后金额原路退回商家余额" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button size="small" @click="auditVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submitAudit">提交</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { getMallMerchantWithdrawalPage, applyMallMerchantWithdrawal, auditMallMerchantWithdrawal } from '@/api/mall_merchant_withdrawal'
import { getMallMerchantOptions, getMallMerchantPage } from '@/api/mall_merchant'

// 0 申请 / 1 审核中 / 2 已打款 / 3 拒绝（与 MallMerchantWithdrawalServiceImpl 常量一致）
const STATUS = {
  0: { text: '申请', tag: 'warning' },
  1: { text: '审核中', tag: '' },
  2: { text: '已打款', tag: 'success' },
  3: { text: '已拒绝', tag: 'danger' }
}
const METHOD = { 0: '余额', 1: '微信', 2: '银行卡' }

export default {
  name: 'MallMerchantWithdrawal',
  data() {
    return {
      loading: false,
      acting: false,
      list: [],
      total: 0,
      STATUS,
      query: { page: 1, size: 10, merchantId: undefined, status: undefined },
      merchantOptions: [],
      merchantNames: {},
      // 当前选中商家的可提现余额（分）
      balanceCache: {},
      balance: 0,
      applyVisible: false,
      applyForm: { merchantId: undefined, amountYuan: undefined, method: 0 },
      auditVisible: false,
      auditForm: { id: undefined, merchantId: undefined, amount: 0, method: 0, pass: true, rejectReason: '' }
    }
  },
  created() {
    this.loadMerchants()
    this.fetchList()
  },
  methods: {
    statusText: s => (STATUS[Number(s)] || {}).text || '未知',
    statusTag: s => (STATUS[Number(s)] || {}).tag || 'info',
    methodText: m => METHOD[Number(m)] || '—',
    yuan(fen) {
      const n = Number(fen)
      return Number.isFinite(n) ? (n / 100).toFixed(2) : '0.00'
    },
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
      try {
        const page = await getMallMerchantPage({ page: 1, size: 200 })
        const names = {}
        const balances = {}
        ;(page.data.list || []).forEach(m => {
          names[String(m.id)] = m.name
          balances[String(m.id)] = Number(m.accountBalance || 0)
        })
        this.merchantNames = names
        this.balanceCache = balances
      } catch (e) {
        this.merchantNames = {}
        this.balanceCache = {}
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
        const res = await getMallMerchantWithdrawalPage({
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
    openApply() {
      this.applyForm = { merchantId: undefined, amountYuan: undefined, method: 0 }
      this.balance = 0
      this.applyVisible = true
    },
    onMerchantChange(id) {
      this.balance = this.balanceCache[String(id)] || 0
    },
    async submitApply() {
      const f = this.applyForm
      if (!f.merchantId) {
        this.$message.warning('请选择商家')
        return
      }
      const amount = Math.round(Number(f.amountYuan || 0) * 100)
      if (amount <= 0) {
        this.$message.warning('请填提现金额')
        return
      }
      if (amount > this.balance) {
        this.$message.warning(`可提现余额不足，当前 ¥${this.yuan(this.balance)}`)
        return
      }
      this.acting = true
      try {
        await applyMallMerchantWithdrawal({ merchantId: f.merchantId, amount, method: f.method })
        this.$message.success('提现申请已提交，金额已冻结')
        this.applyVisible = false
        await this.loadMerchants()
        this.handleSearch()
      } finally {
        this.acting = false
      }
    },
    openAudit(row) {
      this.auditForm = {
        id: row.id,
        merchantId: row.merchantId,
        amount: row.amount,
        method: row.method,
        pass: true,
        rejectReason: ''
      }
      this.auditVisible = true
    },
    async submitAudit() {
      const f = this.auditForm
      if (f.pass === false && !(f.rejectReason || '').trim()) {
        this.$message.warning('拒绝时请填写原因')
        return
      }
      this.acting = true
      try {
        await auditMallMerchantWithdrawal({
          id: f.id,
          pass: f.pass,
          rejectReason: f.pass ? undefined : f.rejectReason.trim()
        })
        this.$message.success(f.pass ? '已标记打款' : '已拒绝，金额退回商家余额')
        this.auditVisible = false
        await this.loadMerchants()
        this.fetchList()
      } finally {
        this.acting = false
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

.money-cell {
  color: #e1251b;
  font-weight: 600;
}

.muted-cell {
  color: #b6bcc8;
}

.pagination {
  margin-top: 18px;
  text-align: right;
}

.kv {
  font-size: 14px;
}

.kv-row {
  display: flex;
  padding: 6px 0;
  gap: 8px;
}

.kv-row .k {
  width: 90px;
  flex: none;
  color: #909399;
}

.balance {
  font-weight: 600;
  color: #111827;
}

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
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
</style>
