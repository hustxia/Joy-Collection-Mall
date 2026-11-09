<template>
  <div class="app-container">
    <!-- 页头：一眼看清「我在哪个页面、这页能干什么」 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">售后退款单</h2>
        <p class="page-desc">会员售后申请的审核、换货处理与平台仲裁（售后单由 C 端申请产生，本页只读 + 业务操作）</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 条记录</span>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" @tab-click="onTabChange">
        <!-- ===== Tab 1：售后单列表（V4 + V15 换货/部分退款操作） ===== -->
        <el-tab-pane label="售后单" name="list">
<template #header>          <div class="card-header"><!-- 占位，样式沿用 --></div></template>
          <div class="rf-filters tab-bar">
            <el-input v-model="query.refundNo" placeholder="售后单号" size="small" clearable style="width:180px" @keyup.enter="search" @clear="search" />
            <el-input v-model="query.orderNo" placeholder="订单号" size="small" clearable style="width:180px" @keyup.enter="search" @clear="search" />
            <el-select v-model="query.status" placeholder="状态" size="small" clearable style="width:130px" @change="search">
              <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-select v-model="query.type" placeholder="类型" size="small" clearable style="width:130px" @change="search">
              <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-date-picker
              v-model="applyRange"
              type="daterange"
              size="small"
              value-format="yyyy-MM-dd"
              range-separator="至"
              start-placeholder="申请开始日"
              end-placeholder="申请结束日"
              style="width:240px"
              @change="search"
            />
            <el-button type="primary" icon="el-icon-search" size="small" @click="search">查询</el-button>
            <el-button icon="el-icon-download" size="small" :loading="exporting" @click="handleExport">导出台账</el-button>
            <el-button icon="el-icon-refresh" size="small" @click="reset">重置</el-button>
          </div>

          <el-table
            v-loading="loading"
            :data="list"
            class="data-table"
            stripe
            header-cell-class-name="table-head-cell"
            empty-text="暂无数据"
          >
            <el-table-column prop="refundNo" label="售后单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
            <el-table-column label="会员" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.memberNickname || '—' }}</span>
                <span class="muted-cell">{{ row.memberPhone || '' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">{{ typeText(row.type) }}</template>
            </el-table-column>
            <el-table-column label="金额/换货" width="110" align="right">
              <template #default="{ row }">
                <span v-if="Number(row.type) === 3" class="muted-cell">换入SKU #{{ row.exchangeSkuId }} ×{{ row.quantity || 1 }}</span>
                <span v-else class="money-cell">¥{{ yuan(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="mini" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
                <el-tag v-if="Number(row.platformStatus) > 0" size="mini" type="danger" style="margin-left:4px">{{ platText(row.platformStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请原因" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.reason || '—' }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="申请时间" width="160" align="center" />
            <el-table-column label="操作" width="230" fixed="right" align="center">
              <template #default="{ row }">
                <el-button type="text" @click="openDetail(row)">详情</el-button>
                <el-button v-if="Number(row.status) === 1" v-hasPermi="['mall_mall_refund:edit']" type="text" @click="openAudit(row)">审核</el-button>
                <el-button v-if="Number(row.status) === 2 && Number(row.type) !== 3" v-hasPermi="['mall_mall_refund:edit']" type="text" class="danger-btn" @click="onRefund(row)">确认退款</el-button>
                <!-- V15 换货节点 -->
                <el-button v-if="Number(row.status) === 6" v-hasPermi="['mall_mall_refund:edit']" type="text" @click="onConfirmReceipt(row)">收货确认</el-button>
                <el-button v-if="Number(row.status) === 7" v-hasPermi="['mall_mall_refund:edit']" type="text" @click="onSendExchange(row)">发出换货</el-button>
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
        </el-tab-pane>

        <!-- ===== Tab 2：平台仲裁（V15 REQ-P1-06） ===== -->
        <el-tab-pane name="arbitration">
          <template #label><span>
            平台仲裁
            <el-badge v-if="arbTotal > 0" :value="arbTotal" type="danger" />
          </span></template>
          <div class="tab-bar muted-line">商家超 48 小时未处理的售后单自动进入仲裁队列（状态 + 超时时间惰性判定，无需定时任务）；仲裁可强制退款或驳回。</div>
          <el-table
            v-loading="arbLoading"
            :data="arbList"
            class="data-table"
            stripe
            header-cell-class-name="table-head-cell"
            empty-text="暂无超时工单"
          >
            <el-table-column prop="refundNo" label="售后单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
            <el-table-column label="会员" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.memberNickname || row.memberId }}</template>
            </el-table-column>
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">{{ typeText(row.type) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }"><span class="money-cell">¥{{ yuan(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="介入状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="mini" :type="Number(row.platformStatus) === 1 ? 'danger' : 'warning'">{{ platText(row.platformStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请原因" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.reason || '—' }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="申请时间（超48h）" width="170" align="center" />
            <el-table-column label="操作" width="110" fixed="right" align="center">
              <template #default="{ row }">
                <el-button v-hasPermi="['mall_mall_refund:edit']" type="text" @click="openVerdict(row)">裁决</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pagination"
            background
            layout="total, prev, pager, next"
            :total="arbTotal"
            :page-size="arbQuery.size"
            v-model:current-page="arbQuery.page"
            @current-change="fetchArbitration"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog title="售后详情" v-model="detailVisible" width="640px" top="8vh">
      <div class="rf-detail">
        <div class="rf-detail-row"><span class="k">售后单号</span><span>{{ detail.refundNo }}</span></div>
        <div class="rf-detail-row"><span class="k">订单号</span><span>{{ detail.orderNo }}</span></div>
        <div class="rf-detail-row"><span class="k">会员</span><span>{{ detail.memberNickname || '—' }} {{ detail.memberPhone || '' }}</span></div>
        <div class="rf-detail-row"><span class="k">售后类型</span><span>{{ typeText(detail.type) }}</span></div>
        <div class="rf-detail-row"><span class="k">金额/换货</span>
          <span v-if="Number(detail.type) === 3">换入SKU #{{ detail.exchangeSkuId }} × {{ detail.quantity || 1 }}（关联订单项 {{ detail.orderItemId }}）</span>
          <span v-else class="money-cell">¥{{ yuan(detail.amount) }}<template v-if="detail.orderItemId">（订单项 {{ detail.orderItemId }}）</template></span>
        </div>
        <div class="rf-detail-row"><span class="k">状态</span><span>{{ statusText(detail.status) }}</span></div>
        <div class="rf-detail-row"><span class="k">平台介入</span><span>{{ platText(detail.platformStatus) }}{{ detail.platformRemark ? ' · ' + detail.platformRemark : '' }}</span></div>
        <div class="rf-detail-row"><span class="k">申请原因</span><span>{{ detail.reason || '—' }}</span></div>
        <div class="rf-detail-row"><span class="k">审核意见</span><span>{{ detail.auditRemark || '—' }}</span></div>
        <div class="rf-detail-row"><span class="k">寄回时间</span><span>{{ detail.shipTime || '—' }}</span></div>
        <div class="rf-detail-row"><span class="k">退款时间</span><span>{{ detail.refundTime || '—' }}</span></div>
        <div v-if="evidenceImages.length" class="rf-detail-row">
          <span class="k">凭证图</span>
          <span class="rf-imgs">
            <el-image
              v-for="(img, i) in evidenceImages"
              :key="i"
              :src="img"
              :preview-src-list="evidenceImages"
              fit="cover"
              class="rf-img"
            />
          </span>
        </div>
      </div>
      <template #footer><div>
        <el-button size="small" @click="detailVisible = false">关闭</el-button>
      </div></template>
    </el-dialog>

    <!-- 审核弹窗 -->
    <el-dialog :title="`审核售后单 ${auditForm.refundNo}`" v-model="auditVisible" width="480px" top="12vh">
      <div class="rf-detail" style="margin-bottom:12px">
        <div class="rf-detail-row"><span class="k">售后类型</span><span>{{ typeText(auditForm.type) }}<template v-if="Number(auditForm.type) !== 3"> · 退款 ¥{{ yuan(auditForm.amount) }}</template><template v-else> · 换入SKU #{{ auditForm.exchangeSkuId }} × {{ auditForm.quantity || 1 }}</template></span></div>
        <div class="rf-detail-row"><span class="k">申请原因</span><span>{{ auditForm.reason || '—' }}</span></div>
      </div>
      <el-form label-width="80px">
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditForm.agree">
            <el-radio :label="true">同意</el-radio>
            <el-radio :label="false">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="auditForm.auditRemark" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="拒绝时建议填写原因" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button size="small" @click="auditVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submitAudit">提交</el-button>
      </div></template>
    </el-dialog>

    <!-- V15 平台仲裁裁决弹窗 -->
    <el-dialog :title="`平台仲裁 ${verdictForm.refundNo}`" v-model="verdictVisible" width="480px" top="12vh">
      <div class="rf-detail" style="margin-bottom:12px">
        <div class="rf-detail-row"><span class="k">售后类型</span><span>{{ typeText(verdictForm.type) }} · ¥{{ yuan(verdictForm.amount) }}</span></div>
        <div class="rf-detail-row"><span class="k">申请原因</span><span>{{ verdictForm.reason || '—' }}</span></div>
        <div class="rf-detail-row"><span class="k">等待时长</span><span>申请时间 {{ verdictForm.createTime }}，已超 48 小时未处理</span></div>
      </div>
      <el-form label-width="80px">
        <el-form-item label="裁决结果">
          <el-radio-group v-model="verdictForm.forceRefund">
            <el-radio :label="true">强制退款</el-radio>
            <el-radio :label="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="仲裁意见">
          <el-input v-model="verdictForm.remark" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="强制退款时必填" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button size="small" @click="verdictVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submitVerdict">提交裁决</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import {
  getMallRefundList, auditMallRefund, refundMallRefund, exportMallRefund,
  confirmReceipt, sendExchange, arbitrationPage, arbitrationVerdict
} from '@/api/mall_refund'

// 状态文案与标签色（设计文档 2.4：warning/primary/danger/success/info）
const STATUS = {
  1: { text: '待审核', tag: 'warning' },
  2: { text: '已同意', tag: 'primary' },
  3: { text: '已拒绝', tag: 'danger' },
  4: { text: '已退款', tag: 'success' },
  5: { text: '已关闭', tag: 'info' },
  6: { text: '买家已寄回', tag: 'primary' },
  7: { text: '商家已收货', tag: 'primary' },
  8: { text: '已换货', tag: 'success' }
}
const TYPE = { 1: '仅退款', 2: '退货退款', 3: '换货', 4: '部分退款' }
const PLAT = { 0: '—', 1: '仲裁中', 2: '已强制退款', 3: '已驳回' }

export default {
  name: 'MallRefund',
  data() {
    return {
      activeTab: 'list',
      loading: false,
      acting: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10, refundNo: '', orderNo: '', status: undefined, type: undefined },
      // V4 P2-3 申请时间区间（yyyy-MM-dd），提交时补成当天 00:00:00 / 23:59:59
      applyRange: [],
      exporting: false,
      statusOptions: Object.entries(STATUS).map(([value, s]) => ({ value: Number(value), label: s.text })),
      typeOptions: Object.entries(TYPE).map(([value, t]) => ({ value: Number(value), label: t })),
      detailVisible: false,
      detail: {},
      auditVisible: false,
      auditForm: { id: undefined, refundNo: '', type: undefined, amount: undefined, exchangeSkuId: undefined, quantity: undefined, reason: '', agree: true, auditRemark: '' },
      // V15 平台仲裁
      arbLoading: false,
      arbList: [],
      arbTotal: 0,
      arbQuery: { page: 1, size: 10 },
      verdictVisible: false,
      verdictForm: { id: undefined, refundNo: '', type: undefined, amount: undefined, reason: '', createTime: '', forceRefund: true, remark: '' }
    }
  },
  computed: {
    evidenceImages() {
      try {
        const parsed = JSON.parse(this.detail.evidenceImages || '[]')
        return Array.isArray(parsed) ? parsed : []
      } catch (e) {
        return []
      }
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    statusText: s => (STATUS[Number(s)] || {}).text || '未知',
    statusTag: s => (STATUS[Number(s)] || {}).tag || 'info',
    typeText: t => TYPE[Number(t)] || '未知',
    platText: s => PLAT[Number(s)] || '—',
    yuan(fen) {
      const n = Number(fen)
      return Number.isFinite(n) ? (n / 100).toFixed(2) : '0.00'
    },
    search() {
      this.query.page = 1
      this.fetchList()
    },
    reset() {
      this.query = { page: 1, size: 10, refundNo: '', orderNo: '', status: undefined, type: undefined }
      this.applyRange = []
      this.fetchList()
    },
    onTabChange(tab) {
      if (tab.name === 'arbitration') this.fetchArbitration()
    },
    /** 列表与导出共用的筛选参数：时间区间统一补成整日边界 */
    filterParams() {
      const params = {
        refundNo: this.query.refundNo || undefined,
        orderNo: this.query.orderNo || undefined,
        status: this.query.status,
        type: this.query.type
      }
      if (this.applyRange && this.applyRange.length === 2) {
        // 后端 LocalDateTime 走 Jackson 默认 ISO 解析，必须用 T 分隔（空格会 400）
        params.applyStartTime = `${this.applyRange[0]}T00:00:00`
        params.applyEndTime = `${this.applyRange[1]}T23:59:59`
      }
      return params
    },
    async handleExport() {
      this.exporting = true
      try {
        const res = await exportMallRefund(this.filterParams())
        const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = '售后退款台账.xlsx'
        a.click()
        window.URL.revokeObjectURL(url)
      } finally {
        this.exporting = false
      }
    },
    handleSizeChange() {
      this.query.page = 1
      this.fetchList()
    },
    async fetchList() {
      this.loading = true
      try {
        // toPageData 包装：{ code, data: { list, total } }
        const res = await getMallRefundList({
          page: this.query.page,
          size: this.query.size,
          ...this.filterParams()
        })
        this.list = (res.data && res.data.list) || []
        this.total = Number((res.data && res.data.total) || 0)
      } finally {
        this.loading = false
      }
    },
    /** V15：平台仲裁队列（超 48h 未处理，惰性判定） */
    async fetchArbitration() {
      this.arbLoading = true
      try {
        const res = await arbitrationPage({ page: this.arbQuery.page, size: this.arbQuery.size })
        this.arbList = (res.data && res.data.list) || []
        this.arbTotal = Number((res.data && res.data.total) || 0)
      } finally {
        this.arbLoading = false
      }
    },
    openDetail(row) {
      this.detail = row
      this.detailVisible = true
    },
    openAudit(row) {
      this.auditForm = {
        id: row.id,
        refundNo: row.refundNo,
        type: row.type,
        amount: row.amount,
        exchangeSkuId: row.exchangeSkuId,
        quantity: row.quantity,
        reason: row.reason,
        agree: true,
        auditRemark: ''
      }
      this.auditVisible = true
    },
    async submitAudit() {
      const f = this.auditForm
      if (!f.agree && !f.auditRemark.trim()) {
        this.$message.warning('拒绝时请填写审核意见')
        return
      }
      this.acting = true
      try {
        await auditMallRefund({ id: f.id, agree: f.agree, auditRemark: f.auditRemark.trim() || undefined })
        this.$message.success(f.agree ? (Number(f.type) === 3 ? '已同意，等待买家寄回' : '已同意，请确认退款') : '已拒绝')
        this.auditVisible = false
        this.fetchList()
      } finally {
        this.acting = false
      }
    },
    async onRefund(row) {
      await this.$confirm(
        `确认向会员 ${row.memberNickname || row.memberId} 退款 ¥${this.yuan(row.amount)}？` +
          (Number(row.type) === 2 ? '（退货退款：将回补库存并回减销量）' : ''),
        '确认退款',
        { type: 'warning', confirmButtonText: '确认退款', cancelButtonText: '取消' }
      )
      await refundMallRefund(row.id)
      this.$message.success('退款完成，已原路退回（mock 渠道）')
      this.fetchList()
    },
    /** V15 换货：收货确认（6→7，回补换出 SKU 库存） */
    async onConfirmReceipt(row) {
      await this.$confirm(
        `确认已收到买家寄回的商品？确认后回补换出 SKU（#${row.exchangeSkuId ? '见详情' : '—'}）库存，可继续发出换货。`,
        '收货确认',
        { type: 'warning', confirmButtonText: '确认收货', cancelButtonText: '取消' }
      )
      await confirmReceipt(row.id)
      this.$message.success('已收货确认，换出商品库存已回补')
      this.fetchList()
    },
    /** V15 换货：发出换货（7→8，扣减换入 SKU 库存） */
    async onSendExchange(row) {
      await this.$confirm(
        `确认已发出换货商品（换入SKU #${row.exchangeSkuId} × ${row.quantity || 1}）？确认后扣减换入库存，售后单完结。`,
        '发出换货',
        { type: 'warning', confirmButtonText: '确认发出', cancelButtonText: '取消' }
      )
      await sendExchange(row.id)
      this.$message.success('换货已发出，售后单完结')
      this.fetchList()
    },
    /** V15 平台仲裁：打开裁决弹窗 */
    openVerdict(row) {
      this.verdictForm = {
        id: row.id,
        refundNo: row.refundNo,
        type: row.type,
        amount: row.amount,
        reason: row.reason,
        createTime: row.createTime,
        forceRefund: true,
        remark: ''
      }
      this.verdictVisible = true
    },
    async submitVerdict() {
      const f = this.verdictForm
      if (f.forceRefund && !f.remark.trim()) {
        this.$message.warning('强制退款必须填写仲裁意见')
        return
      }
      this.acting = true
      try {
        await arbitrationVerdict({
          id: f.id,
          forceRefund: f.forceRefund,
          remark: f.remark.trim() || undefined
        })
        this.$message.success(f.forceRefund ? '已强制退款（走退款主流程终态）' : '已驳回')
        this.verdictVisible = false
        this.fetchArbitration()
      } finally {
        this.acting = false
      }
    }
  }
}
</script>

<style scoped>
.rf-filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.tab-bar { padding: 0 0 12px; }
.muted-line { color: #909399; font-size: 12px; }
.muted-cell { color: #909399; font-size: 12px; margin-left: 6px; }
.money-cell { color: #e1251b; font-weight: 600; }
.rf-detail { font-size: 14px; }
.rf-detail-row { display: flex; padding: 6px 0; }
.rf-detail-row .k { width: 90px; color: #909399; flex: none; }
.rf-imgs { display: flex; gap: 8px; flex-wrap: wrap; }
.rf-img { width: 72px; height: 72px; border-radius: 6px; }
</style>
