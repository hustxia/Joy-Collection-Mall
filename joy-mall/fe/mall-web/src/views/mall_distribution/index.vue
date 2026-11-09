<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>
        <span>分销管理</span>
        <span class="hint">分销员 → 佣金台账 → 佣金规则 → 提现审核；金额单位分，比例单位‰</span>
      </div></template>
      <el-tabs v-model="activeTab" @tab-click="onTabChange">

        <!-- ══ 分销员 ══ -->
        <el-tab-pane label="分销员" name="distributor">
          <el-form :inline="true" size="small" @submit.prevent>
            <el-form-item label="状态">
              <el-select v-model="distributorQuery.status" placeholder="全部" clearable style="width:130px" @change="searchDistributor">
                <el-option label="待审核" :value="0" />
                <el-option label="已启用" :value="1" />
                <el-option label="已停用" :value="2" />
                <el-option label="已驳回" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="el-icon-search" @click="searchDistributor">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="distributorLoading" :data="distributorList" stripe empty-text="暂无分销员" size="small">
            <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
            <el-table-column prop="memberId" label="会员ID" width="180" show-overflow-tooltip />
            <el-table-column prop="inviteCode" label="邀请码" width="110" />
            <el-table-column prop="level" label="等级" width="60" align="center" />
            <el-table-column label="累计佣金" width="110" align="right">
              <template #default="{ row }">¥{{ fenToYuan(row.totalCommission) }}</template>
            </el-table-column>
            <el-table-column label="已结算" width="100" align="right">
              <template #default="{ row }">¥{{ fenToYuan(row.settled) }}</template>
            </el-table-column>
            <el-table-column label="可提现" width="100" align="right">
              <template #default="{ row }">¥{{ fenToYuan(row.withdrawable) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="mini" :type="distTag(row.status)">{{ distText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="驳回原因" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.rejectReason || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="190" align="right">
              <template #default="{ row }">
                <template v-if="Number(row.status) === 0">
                  <el-button type="text" @click="auditDist(row, true)">通过</el-button>
                  <el-button type="text" style="color:#e6a23c" @click="auditDist(row, false)">驳回</el-button>
                </template>
                <el-button v-if="Number(row.status) === 1" type="text" style="color:#e6a23c" @click="toggleDist(row, false)">停用</el-button>
                <el-button v-if="Number(row.status) === 2" type="text" @click="toggleDist(row, true)">启用</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" background layout="total, prev, pager, next" :total="distributorTotal" v-model:current-page="distributorQuery.pageNum" :page-size="distributorQuery.pageSize" @current-change="loadDistributor" />
        </el-tab-pane>

        <!-- ══ 佣金台账 ══ -->
        <el-tab-pane label="佣金台账" name="commission">
          <el-form :inline="true" size="small" @submit.prevent>
            <el-form-item label="分销员ID">
              <el-input v-model="commissionQuery.distributorId" placeholder="精确匹配" clearable style="width:180px" @keyup.enter="searchCommission" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="commissionQuery.status" placeholder="全部" clearable style="width:120px" @change="searchCommission">
                <el-option label="待结算" :value="0" />
                <el-option label="已结算" :value="1" />
                <el-option label="失效" :value="2" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="el-icon-search" @click="searchCommission">查询</el-button>
              <el-button type="success" icon="el-icon-plus" @click="openSettle">手动结算</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="commissionLoading" :data="commissionList" stripe empty-text="暂无佣金记录" size="small">
            <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
            <el-table-column prop="distributorId" label="分销员ID" width="180" show-overflow-tooltip />
            <el-table-column prop="orderId" label="订单ID" width="180" show-overflow-tooltip />
            <el-table-column prop="memberId" label="下单会员" width="170" show-overflow-tooltip />
            <el-table-column prop="goodsId" label="商品ID" width="170" show-overflow-tooltip />
            <el-table-column label="佣金金额" width="100" align="right">
              <template #default="{ row }">¥{{ fenToYuan(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="比例‰" width="80" align="center">
              <template #default="{ row }">{{ row.rate }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="mini" :type="commTag(row.status)">{{ commText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="结算时间" min-width="150">
              <template #default="{ row }">{{ short(row.settleTime) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" background layout="total, prev, pager, next" :total="commissionTotal" v-model:current-page="commissionQuery.pageNum" :page-size="commissionQuery.pageSize" @current-change="loadCommission" />
        </el-tab-pane>

        <!-- ══ 佣金规则 ══ -->
        <el-tab-pane label="佣金规则" name="rule">
          <el-form :inline="true" size="small" @submit.prevent>
            <el-form-item>
              <el-button type="success" icon="el-icon-plus" @click="openRuleCreate">新建规则</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="ruleLoading" :data="ruleList" stripe empty-text="暂无佣金规则" size="small">
            <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
            <el-table-column label="作用域" width="110">
              <template #default="{ row }">
                <el-tag size="mini">{{ Number(row.scope) === 1 ? '指定商品' : '全局' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="商品ID" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">{{ row.goodsId || '—' }}</template>
            </el-table-column>
            <el-table-column label="佣金比例‰" width="100" align="center">
              <template #default="{ row }">{{ row.rate == null ? '—' : row.rate }}</template>
            </el-table-column>
            <el-table-column label="固定佣金" width="110" align="right">
              <template #default="{ row }">{{ row.fixed == null ? '—' : '¥' + fenToYuan(row.fixed) }}</template>
            </el-table-column>
            <el-table-column prop="level" label="层级" width="70" align="center" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="mini" :type="Number(row.status) === 1 ? 'success' : 'info'">
                  {{ Number(row.status) === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="right">
              <template #default="{ row }">
                <el-button type="text" @click="openRuleEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" background layout="total, prev, pager, next" :total="ruleTotal" v-model:current-page="ruleQuery.pageNum" :page-size="ruleQuery.pageSize" @current-change="loadRule" />
        </el-tab-pane>

        <!-- ══ 提现审核 ══ -->
        <el-tab-pane label="提现审核" name="withdrawal">
          <el-form :inline="true" size="small" @submit.prevent>
            <el-form-item label="状态">
              <el-select v-model="withdrawalQuery.status" placeholder="全部" clearable style="width:130px" @change="searchWithdrawal">
                <el-option label="申请" :value="0" />
                <el-option label="审核中" :value="1" />
                <el-option label="已打款" :value="2" />
                <el-option label="已拒绝" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="el-icon-search" @click="searchWithdrawal">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="withdrawalLoading" :data="withdrawalList" stripe empty-text="暂无提现单" size="small">
            <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
            <el-table-column prop="distributorId" label="分销员ID" width="180" show-overflow-tooltip />
            <el-table-column label="提现金额" width="110" align="right">
              <template #default="{ row }">¥{{ fenToYuan(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="方式" width="90" align="center">
              <template #default="{ row }">{{ methodText(row.method) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="mini" :type="wdTag(row.status)">{{ wdText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="auditBy" label="审核人" width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.auditBy || '—' }}</template>
            </el-table-column>
            <el-table-column label="审核时间" min-width="150">
              <template #default="{ row }">{{ short(row.auditTime) }}</template>
            </el-table-column>
            <el-table-column label="审核备注" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.auditRemark || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="right">
              <template #default="{ row }">
                <template v-if="Number(row.status) === 0 || Number(row.status) === 1">
                  <el-button type="text" @click="auditWd(row, true)">通过打款</el-button>
                  <el-button type="text" style="color:#e6a23c" @click="auditWd(row, false)">驳回</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" background layout="total, prev, pager, next" :total="withdrawalTotal" v-model:current-page="withdrawalQuery.pageNum" :page-size="withdrawalQuery.pageSize" @current-change="loadWithdrawal" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 手动结算弹窗 -->
    <el-dialog title="手动结算佣金" v-model="settleVisible" width="500px">
      <el-form label-width="100px" size="small">
        <el-form-item label="分销员" required>
          <el-select v-model="settleForm.distributorId" filterable placeholder="选择分销员" style="width:100%">
            <el-option v-for="d in distributorOptions" :key="d.id" :label="distributorLabel(d)" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单" required>
          <el-select v-model="settleForm.orderId" filterable placeholder="选择订单（自动带出会员与金额）" style="width:100%" @change="onSettleOrderChange">
            <el-option v-for="o in orderOptions" :key="o.id" :label="orderLabel(o)" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="下单会员" required>
          <el-select v-model="settleForm.memberId" filterable placeholder="选择会员" style="width:100%">
            <el-option v-for="m in memberOptions" :key="m.id" :label="memberLabel(m)" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品" required>
          <el-select v-model="settleForm.goodsId" filterable placeholder="选择商品" style="width:100%">
            <el-option v-for="g in goodsOptions" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="实付金额" required>
          <el-input v-model="settleForm.payAmountYuan" style="width:160px"><template #append>元</template></el-input>
        </el-form-item>
      </el-form>
      <template #footer><span>
        <el-button @click="settleVisible = false">取消</el-button>
        <el-button type="primary" :loading="settleSaving" @click="submitSettle">结算</el-button>
      </span></template>
    </el-dialog>

    <!-- 佣金规则弹窗 -->
    <el-dialog :title="ruleForm.id ? '编辑佣金规则' : '新建佣金规则'" v-model="ruleVisible" width="520px">
      <el-form label-width="100px" size="small">
        <el-form-item label="作用域">
          <el-select v-model="ruleForm.scope" style="width:160px" @change="ruleForm.goodsId = ''">
            <el-option label="全局" :value="0" />
            <el-option label="指定商品" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="ruleForm.scope === 1" label="商品" required>
          <el-select v-model="ruleForm.goodsId" filterable clearable placeholder="选择商品" style="width:100%">
            <el-option v-for="g in goodsOptions" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="佣金比例">
          <el-input v-model="ruleForm.rate" placeholder="如 50 表示 5%" style="width:160px"><template #append>‰</template></el-input>
          <span class="hint">与固定佣金二选一，固定佣金优先</span>
        </el-form-item>
        <el-form-item label="固定佣金">
          <el-input v-model="ruleForm.fixedYuan" style="width:160px"><template #append>元</template></el-input>
        </el-form-item>
        <el-form-item label="分销层级">
          <el-input-number v-model="ruleForm.level" :min="1" :max="3" size="small" />
          <span class="hint">1 = 仅一级分销</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="ruleForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer><span>
        <el-button @click="ruleVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="submitRule">保存</el-button>
      </span></template>
    </el-dialog>
  </div>
</template>

<script>
import {
  pageDistributor, auditDistributor, enableDistributor, disableDistributor,
  pageCommission, settleCommission,
  pageCommissionRule, saveCommissionRule,
  pageWithdrawal, auditWithdrawal
} from '@/api/mall_distribution'
import { getMallGoodsList } from '@/api/mall_goods'
import { getMallMemberList } from '@/api/mall_member'
import { getMallOrderList } from '@/api/mall_order'
import { fenToYuan, yuanToFen } from '@/utils/format'

export default {
  name: 'MallDistribution',
  data() {
    return {
      activeTab: 'distributor',
      loaded: { distributor: false, commission: false, rule: false, withdrawal: false },
      /* 分销员 */
      distributorQuery: { status: null, pageNum: 1, pageSize: 10 },
      distributorList: [],
      distributorTotal: 0,
      distributorLoading: false,
      /* 佣金台账 */
      commissionQuery: { distributorId: '', status: null, pageNum: 1, pageSize: 10 },
      commissionList: [],
      commissionTotal: 0,
      commissionLoading: false,
      settleVisible: false,
      settleSaving: false,
      settleForm: {},
      distributorOptions: [],
      memberOptions: [],
      orderOptions: [],
      goodsOptions: [],
      /* 佣金规则 */
      ruleQuery: { pageNum: 1, pageSize: 10 },
      ruleList: [],
      ruleTotal: 0,
      ruleLoading: false,
      ruleVisible: false,
      ruleSaving: false,
      ruleForm: {},
      /* 提现审核 */
      withdrawalQuery: { status: null, pageNum: 1, pageSize: 10 },
      withdrawalList: [],
      withdrawalTotal: 0,
      withdrawalLoading: false
    }
  },
  created() {
    this.loadDistributor()
    this.loaded.distributor = true
    this.loadSettleOptions()
  },
  methods: {
    fenToYuan,
    async loadSettleOptions() {
      const [dist, members, orders, goods] = await Promise.all([
        pageDistributor({ pageNum: 1, pageSize: 200, status: null }),
        getMallMemberList({ page: 1, size: 200 }),
        getMallOrderList({ page: 1, size: 200 }),
        getMallGoodsList({ page: 1, size: 200 })
      ])
      this.distributorOptions = (dist.data && dist.data.records) || []
      this.memberOptions = (members.data && members.data.list) || []
      this.orderOptions = (orders.data && orders.data.list) || []
      this.goodsOptions = (goods.data && goods.data.list) || []
    },
    memberLabel(m) {
      return (m.nickname || '会员') + (m.phone ? '（' + m.phone + '）' : '（' + m.id + '）')
    },
    distributorLabel(d) {
      const m = this.memberOptions.find(x => String(x.id) === String(d.memberId))
      return (m ? this.memberLabel(m) : '分销员 ' + d.memberId) + '｜邀请码 ' + (d.inviteCode || '—')
    },
    orderLabel(o) {
      return (o.orderNo || o.id) + '｜¥' + fenToYuan(o.payAmount)
    },
    onSettleOrderChange(orderId) {
      const o = this.orderOptions.find(x => String(x.id) === String(orderId))
      if (!o) return
      this.settleForm.memberId = o.memberId
      this.settleForm.payAmountYuan = fenToYuan(o.payAmount)
    },
    short(t) {
      return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
    },
    onTabChange(tab) {
      const name = tab.name
      if (!this.loaded[name]) {
        this.loaded[name] = true
        if (name === 'commission') this.loadCommission()
        if (name === 'rule') this.loadRule()
        if (name === 'withdrawal') this.loadWithdrawal()
      }
    },
    distText(s) {
      return { 0: '待审核', 1: '已启用', 2: '已停用', 3: '已驳回' }[Number(s)] || '未知'
    },
    distTag(s) {
      return { 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' }[Number(s)] || 'info'
    },
    commText(s) {
      return { 0: '待结算', 1: '已结算', 2: '失效' }[Number(s)] || '未知'
    },
    commTag(s) {
      return { 0: 'warning', 1: 'success', 2: 'info' }[Number(s)] || 'info'
    },
    methodText(m) {
      return { 0: '余额', 1: '微信', 2: '银行卡' }[Number(m)] || '—'
    },
    wdText(s) {
      return { 0: '申请', 1: '审核中', 2: '已打款', 3: '已拒绝' }[Number(s)] || '未知'
    },
    wdTag(s) {
      return { 0: 'warning', 1: 'warning', 2: 'success', 3: 'danger' }[Number(s)] || 'info'
    },

    /* ── 分销员 ── */
    async loadDistributor() {
      this.distributorLoading = true
      try {
        const res = await pageDistributor({
          pageNum: this.distributorQuery.pageNum,
          pageSize: this.distributorQuery.pageSize,
          status: this.distributorQuery.status === '' ? null : this.distributorQuery.status
        })
        const d = res.data || {}
        this.distributorList = d.records || []
        this.distributorTotal = d.total || 0
      } finally {
        this.distributorLoading = false
      }
    },
    searchDistributor() {
      this.distributorQuery.pageNum = 1
      this.loadDistributor()
    },
    async auditDist(row, pass) {
      let rejectReason = ''
      if (!pass) {
        rejectReason = await this.$prompt('请填写驳回原因', '驳回申请', { type: 'warning', inputValue: '资料不完整' }).then(({ value }) => value).catch(() => null)
        if (rejectReason === null) return
      }
      await auditDistributor({ id: row.id, pass, rejectReason: rejectReason || undefined })
      this.$message.success(pass ? '已通过' : '已驳回')
      this.loadDistributor()
    },
    async toggleDist(row, enable) {
      const tip = enable ? `启用分销员「${row.inviteCode || row.id}」？` : `停用分销员「${row.inviteCode || row.id}」？停用后不再产生佣金。`
      await this.$confirm(tip, '确认', { type: 'warning' })
      if (enable) {
        await enableDistributor(row.id)
      } else {
        await disableDistributor(row.id)
      }
      this.$message.success('状态已更新')
      this.loadDistributor()
    },

    /* ── 佣金台账 ── */
    async loadCommission() {
      this.commissionLoading = true
      try {
        const res = await pageCommission({
          pageNum: this.commissionQuery.pageNum,
          pageSize: this.commissionQuery.pageSize,
          distributorId: this.commissionQuery.distributorId || null,
          status: this.commissionQuery.status === '' ? null : this.commissionQuery.status
        })
        const d = res.data || {}
        this.commissionList = d.records || []
        this.commissionTotal = d.total || 0
      } finally {
        this.commissionLoading = false
      }
    },
    searchCommission() {
      this.commissionQuery.pageNum = 1
      this.loadCommission()
    },
    openSettle() {
      this.settleForm = { distributorId: '', orderId: '', memberId: '', goodsId: '', payAmountYuan: '' }
      this.settleVisible = true
    },
    async submitSettle() {
      const f = this.settleForm
      if (!f.distributorId || !f.orderId || !f.memberId || !f.goodsId || !f.payAmountYuan) {
        this.$message.warning('请填写完整结算信息')
        return
      }
      this.settleSaving = true
      try {
        await settleCommission({
          distributorId: f.distributorId,
          orderId: f.orderId,
          memberId: f.memberId,
          goodsId: f.goodsId,
          payAmount: yuanToFen(f.payAmountYuan)
        })
        this.$message.success('结算成功')
        this.settleVisible = false
        this.loadCommission()
      } finally {
        this.settleSaving = false
      }
    },

    /* ── 佣金规则 ── */
    async loadRule() {
      this.ruleLoading = true
      try {
        const res = await pageCommissionRule({
          pageNum: this.ruleQuery.pageNum,
          pageSize: this.ruleQuery.pageSize
        })
        const d = res.data || {}
        this.ruleList = d.records || []
        this.ruleTotal = d.total || 0
      } finally {
        this.ruleLoading = false
      }
    },
    openRuleCreate() {
      this.ruleForm = { scope: 0, goodsId: '', rate: '', fixedYuan: '', level: 1, enabled: true }
      this.ruleVisible = true
    },
    openRuleEdit(row) {
      this.ruleForm = {
        id: row.id,
        scope: Number(row.scope),
        goodsId: row.goodsId == null ? '' : Number(row.goodsId),
        rate: row.rate == null ? '' : String(row.rate),
        fixedYuan: row.fixed == null ? '' : fenToYuan(row.fixed),
        level: row.level || 1,
        enabled: Number(row.status) === 1
      }
      this.ruleVisible = true
    },
    async submitRule() {
      const f = this.ruleForm
      if (f.scope === 1 && !f.goodsId) {
        this.$message.warning('指定商品作用域需选择商品')
        return
      }
      if (f.rate === '' && f.fixedYuan === '') {
        this.$message.warning('佣金比例与固定佣金至少填一项')
        return
      }
      this.ruleSaving = true
      try {
        await saveCommissionRule({
          id: f.id || undefined,
          scope: f.scope,
          goodsId: f.scope === 1 ? f.goodsId : null,
          rate: f.rate === '' ? null : Number(f.rate),
          fixed: f.fixedYuan === '' ? null : yuanToFen(f.fixedYuan),
          level: f.level,
          status: f.enabled ? 1 : 0
        })
        this.$message.success('已保存')
        this.ruleVisible = false
        this.loadRule()
      } finally {
        this.ruleSaving = false
      }
    },

    /* ── 提现审核 ── */
    async loadWithdrawal() {
      this.withdrawalLoading = true
      try {
        const res = await pageWithdrawal({
          pageNum: this.withdrawalQuery.pageNum,
          pageSize: this.withdrawalQuery.pageSize,
          status: this.withdrawalQuery.status === '' ? null : this.withdrawalQuery.status
        })
        const d = res.data || {}
        this.withdrawalList = d.records || []
        this.withdrawalTotal = d.total || 0
      } finally {
        this.withdrawalLoading = false
      }
    },
    searchWithdrawal() {
      this.withdrawalQuery.pageNum = 1
      this.loadWithdrawal()
    },
    async auditWd(row, pass) {
      let remark = ''
      if (!pass) {
        remark = await this.$prompt('请填写驳回原因', '驳回提现', { type: 'warning', inputValue: '账户信息有误' }).then(({ value }) => value).catch(() => null)
        if (remark === null) return
      }
      await this.$confirm(pass ? `通过提现「¥${fenToYuan(row.amount)}」并标记已打款？` : `驳回该笔提现，金额退回分销员账户？`, '审核确认', { type: 'warning' })
      await auditWithdrawal({ id: row.id, pass, remark: remark || undefined })
      this.$message.success(pass ? '已通过' : '已驳回')
      this.loadWithdrawal()
    }
  }
}
</script>

<style scoped>
.hint { color: #a8abb2; font-size: 12px; margin-left: 8px; }
.pager { margin-top: 12px; text-align: right; }
</style>
