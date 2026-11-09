<template>
  <div>
    <el-card shadow="never">
      <template #header><div class="head">
        <span>结算单</span>
        <div>
          <el-button size="mini" icon="el-icon-download" @click="exportBill">导出对账单</el-button>
          <el-button size="mini" type="primary" icon="el-icon-money" @click="openApply">申请提现</el-button>
        </div>
      </div></template>
      <el-table v-loading="loadingSettle" :data="settlements" size="small" stripe empty-text="暂无结算单">
        <el-table-column prop="settlementNo" label="结算单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="结算周期" min-width="200">
          <template #default="{ row }">{{ short(row.periodStart) }} ~ {{ short(row.periodEnd) }}</template>
        </el-table-column>
        <el-table-column prop="orderCount" label="订单数" width="90" />
        <el-table-column label="商品金额" width="120">
          <template #default="{ row }">¥{{ fenToYuan(row.goodsAmount) }}</template>
        </el-table-column>
        <el-table-column label="平台服务费" width="120">
          <template #default="{ row }">-¥{{ fenToYuan(row.platformFee) }}</template>
        </el-table-column>
        <el-table-column label="退款" width="110">
          <template #default="{ row }">-¥{{ fenToYuan(row.refundAmount) }}</template>
        </el-table-column>
        <el-table-column label="应结金额" width="120">
          <template #default="{ row }"><b class="red">¥{{ fenToYuan(row.actualAmount) }}</b></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="mini" :type="Number(row.status) === 1 ? 'success' : 'warning'">
              {{ Number(row.status) === 1 ? '已结算' : '待结算' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="settleTotal" v-model:current-page="settleQuery.pageNum" :page-size="settleQuery.pageSize" @current-change="loadSettlements" />
    </el-card>

    <el-card shadow="never" class="mt">
<template #header>      <div>提现记录</div></template>
      <el-table v-loading="loadingWithdraw" :data="withdrawals" size="small" stripe empty-text="暂无提现申请">
        <el-table-column prop="id" label="单号" width="170" show-overflow-tooltip />
        <el-table-column label="金额" width="130">
          <template #default="{ row }"><b class="red">¥{{ fenToYuan(row.amount) }}</b></template>
        </el-table-column>
        <el-table-column label="到账方式" width="110">
          <template #default="{ row }">{{ Number(row.method) === 2 ? '微信' : '银行转账' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="mini" :type="withdrawTag(row.status)">{{ withdrawText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rejectReason" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="申请时间" width="170" />
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="withdrawTotal" v-model:current-page="withdrawQuery.pageNum" :page-size="withdrawQuery.pageSize" @current-change="loadWithdrawals" />
      <p class="note">申请提现即时冻结余额；平台审核拒绝后余额自动恢复，到账时间以平台处理为准。</p>
    </el-card>

    <el-dialog title="申请提现" v-model="applyVisible" width="420px">
      <el-form label-width="90px" size="small">
        <el-form-item label="提现金额">
          <el-input v-model="applyForm.amountYuan" placeholder="元" style="width:150px" />
          <span class="unit">元（余额 ¥{{ fenToYuan(balance) }}）</span>
        </el-form-item>
        <el-form-item label="到账方式">
          <el-radio-group v-model="applyForm.method">
            <el-radio :label="1">银行转账</el-radio>
            <el-radio :label="2">微信</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer><span>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button>
      </span></template>
    </el-dialog>
  </div>
</template>

<script>
import { settlementPage, withdrawalPage, withdrawalApply, billExport, overview } from '@/api/merchant'
import { fenToYuan, yuanToFen } from '@/utils/format'

export default {
  name: 'MerchantFund',
  data() {
    return {
      settlements: [],
      settleTotal: 0,
      settleQuery: { pageNum: 1, pageSize: 10 },
      loadingSettle: false,
      withdrawals: [],
      withdrawTotal: 0,
      withdrawQuery: { pageNum: 1, pageSize: 10 },
      loadingWithdraw: false,
      balance: 0,
      applyVisible: false,
      applying: false,
      applyForm: { amountYuan: '', method: 1 }
    }
  },
  created() {
    this.loadSettlements()
    this.loadWithdrawals()
    this.loadBalance()
  },
  methods: {
    fenToYuan,
    short(t) {
      return t ? String(t).slice(0, 10) : '—'
    },
    withdrawText(s) {
      return { 0: '待审核', 1: '已打款', 2: '已拒绝' }[Number(s)] || '未知'
    },
    withdrawTag(s) {
      return { 0: 'warning', 1: 'success', 2: 'danger' }[Number(s)] || ''
    },
    async loadBalance() {
      const res = await overview()
      this.balance = (res.data || {}).accountBalance || 0
    },
    async loadSettlements() {
      this.loadingSettle = true
      try {
        const res = await settlementPage(this.settleQuery)
        const d = res.data || {}
        this.settlements = d.records || []
        this.settleTotal = d.total || 0
      } finally {
        this.loadingSettle = false
      }
    },
    async loadWithdrawals() {
      this.loadingWithdraw = true
      try {
        const res = await withdrawalPage(this.withdrawQuery)
        const d = res.data || {}
        this.withdrawals = d.records || []
        this.withdrawTotal = d.total || 0
      } finally {
        this.loadingWithdraw = false
      }
    },
    openApply() {
      this.applyForm = { amountYuan: '', method: 1 }
      this.applyVisible = true
    },
    async submitApply() {
      const amount = yuanToFen(this.applyForm.amountYuan)
      if (amount <= 0) {
        this.$message.warning('请输入正确的提现金额')
        return
      }
      if (amount > Number(this.balance)) {
        this.$message.warning('提现金额超过可用余额')
        return
      }
      this.applying = true
      try {
        await withdrawalApply({ amount, method: this.applyForm.method })
        this.$message.success('提现申请已提交，余额已冻结')
        this.applyVisible = false
        this.loadWithdrawals()
        this.loadBalance()
      } finally {
        this.applying = false
      }
    },
    async exportBill() {
      const res = await billExport({})
      // 后端返回 CSV（带 BOM），用 Blob 直接下载：不要走 JSON 拦截器，否则中文全部乱码
      const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `对账单-${new Date().toISOString().slice(0, 10)}.csv`
      a.click()
      window.URL.revokeObjectURL(url)
    }
  }
}
</script>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; }
.mt { margin-top: 14px; }
.red { color: #e1251b; }
.pager { margin-top: 12px; text-align: right; }
.note { color: #a8abb2; font-size: 12px; margin: 8px 0 0; }
.unit { margin-left: 8px; color: #909399; font-size: 12px; }
</style>
