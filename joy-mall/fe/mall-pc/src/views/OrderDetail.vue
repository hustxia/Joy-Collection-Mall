<template>
  <div v-loading="loading">
    <template v-if="order.id">
      <!-- 状态条 -->
      <div class="page-card" style="margin-bottom:16px;display:flex;align-items:center;gap:18px">
        <div style="flex:1">
          <div style="font-size:20px;font-weight:700">{{ orderStatusText(order.status) }}</div>
          <div class="muted" style="margin-top:6px">
            {{ statusHint }}
          </div>
        </div>
        <el-button v-if="Number(order.status) === 10" size="small" @click="onCancel">取消订单</el-button>
        <el-button v-if="Number(order.status) === 10" size="small" type="danger" @click="goPay">去支付</el-button>
        <el-button v-if="Number(order.status) === 30" size="small" type="danger" @click="onReceipt">确认收货</el-button>
        <!-- V12：已发货/已完成才可能有包裹，点开再拉轨迹（避免详情页多打一次物流接口） -->
        <el-button v-if="canViewLogistics" size="small" @click="loadTrack">查看物流</el-button>
        <!-- V12：一单一票，已申请就只展示不再给按钮 -->
        <el-button v-if="canApplyInvoice" size="small" type="primary" plain @click="openInvoice">申请开票</el-button>
        <el-button v-if="canApply" size="small" type="warning" @click="$router.push({ path: '/refund/apply', query: { orderId: order.id } })">申请售后</el-button>
        <el-button v-if="activeRefund.id" size="small" type="warning" plain @click="$router.push('/refund/detail/' + activeRefund.id)">查看售后</el-button>
        <el-button size="small" @click="$router.push('/order/list')">返回订单列表</el-button>
      </div>

      <!-- 订单信息 + 收货信息 -->
      <div class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">订单信息</span></div>
        <div class="kv-grid">
          <div class="kv"><b>订单号</b><span>{{ order.orderNo }}</span></div>
          <div class="kv"><b>店铺</b><span>{{ shopName || '—' }}</span></div>
          <div class="kv"><b>下单时间</b><span>{{ shortTime(order.createTime) }}</span></div>
          <div class="kv"><b>支付状态</b><span>{{ Number(order.payStatus) === 1 ? '已支付' : '未支付' }}</span></div>
          <div class="kv"><b>支付时间</b><span>{{ shortTime(order.payTime) }}</span></div>
          <div class="kv"><b>发货时间</b><span>{{ shortTime(order.deliverTime) }}</span></div>
          <div class="kv"><b>完成时间</b><span>{{ shortTime(order.finishTime) }}</span></div>
          <div class="kv"><b>收货人</b><span>{{ order.receiverName }}　{{ order.receiverPhone }}</span></div>
          <div class="kv"><b>收货地址</b><span>{{ order.receiverAddress }}</span></div>
          <div class="kv"><b>买家留言</b><span>{{ order.buyerRemark || '—' }}</span></div>
        </div>
      </div>

      <!-- 售后状态 -->
      <div v-if="activeRefund.id" class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">售后进度</span></div>
        <div style="display:flex;align-items:center;gap:14px">
          <el-tag :type="refundStatusType(activeRefund.status)" size="small">{{ refundStatusText(activeRefund.status) }}</el-tag>
          <div style="flex:1;font-size:13px">
            {{ refundTypeText(activeRefund.type) }} · 退款金额 <span class="money">¥{{ $price(activeRefund.amount) }}</span>
            <span class="muted" style="margin-left:12px">{{ refundStatusText(activeRefund.status) === '待审核' ? '商家审核中' : '点击右上角「查看售后」了解详情' }}</span>
          </div>
        </div>
      </div>

      <!-- 物流轨迹（V12）：一单可能多包裹，每个包裹独立时间线 -->
      <div v-if="trackLoaded" class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">物流信息</span></div>
        <el-empty v-if="!packages.length" :description="trackTip || '暂无物流信息'" :image-size="70" />
        <div v-for="(pkg, pi) in packages" :key="pi" class="pkg">
          <div class="pkg-head">
            <el-tag size="mini" type="success">{{ pkg.expressCompany }}</el-tag>
            <span class="pkg-no">{{ pkg.expressNo }}</span>
            <span class="muted">{{ shortTime(pkg.deliveryTime) }}</span>
            <span class="muted pkg-items">{{ pkgItems(pkg) }}</span>
          </div>
          <el-timeline v-if="pkg.nodes && pkg.nodes.length" class="timeline-box">
            <el-timeline-item
              v-for="(node, ni) in pkg.nodes"
              :key="ni"
              :timestamp="shortTime(node.time)"
              placement="top"
              :type="ni === 0 ? 'primary' : ''"
            >
              <div style="font-size:14px">{{ node.desc }}</div>
              <div class="muted" style="margin-top:2px">{{ node.status }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="该包裹暂无轨迹（渠道未返回）" :image-size="60" />
        </div>
      </div>

      <!-- 发票（V12）：已申请则展示状态与下载入口 -->
      <div v-if="invoice && invoice.id" class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">发票信息</span></div>
        <div class="kv-grid">
          <div class="kv"><b>发票号</b><span>{{ invoice.invoiceNo }}</span></div>
          <div class="kv"><b>状态</b><span>{{ invoiceStatusText(invoice.status) }}</span></div>
          <div class="kv"><b>抬头</b><span>{{ invoice.title }}</span></div>
          <div class="kv"><b>税号</b><span>{{ invoice.taxNo || '—' }}</span></div>
          <div class="kv"><b>金额</b><span class="money">¥{{ $price(invoice.amount) }}</span></div>
          <div class="kv"><b>邮箱</b><span>{{ invoice.email || '—' }}</span></div>
          <div v-if="invoice.issueTime" class="kv"><b>开票时间</b><span>{{ shortTime(invoice.issueTime) }}</span></div>
          <div v-if="invoice.redFlushTime" class="kv"><b>红冲时间</b><span>{{ shortTime(invoice.redFlushTime) }}</span></div>
        </div>
        <div v-if="invoice.fileUrl" style="margin-top:10px">
          <el-button size="small" type="primary" plain @click="openInvoiceFile">查看发票文件</el-button>
        </div>
        <div v-else-if="Number(invoice.status) === 0" class="muted" style="margin-top:10px">
          商家正在开票，开票后可在「我的发票」下载
        </div>
      </div>

      <!-- 商品明细 -->
      <div class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">商品明细</span></div>
        <el-table :data="items" size="small" border>
          <el-table-column label="商品" min-width="320">
            <template #default="{ row }">
              <div class="cart-prod">
                <pc-image :src="row.skuImage" :img-style="{ width: '56px', height: '56px', objectFit: 'cover', borderRadius: '6px', display: 'block' }" />
                <div>
                  <div class="cart-prod-name">{{ row.goodsName }}</div>
                  <div class="cart-prod-spec">{{ specText(row.specJson) || '默认规格' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="130" align="right">
            <template #default="{ row }">¥{{ $price(row.price) }}</template>
          </el-table-column>
          <el-table-column label="数量" width="90" align="center" prop="quantity" />
          <el-table-column label="小计" width="130" align="right">
            <template #default="{ row }"><span class="money">¥{{ $price(row.totalAmount) }}</span></template>
          </el-table-column>
          <!-- V15：订单项级售后入口（换货 / 部分退款） -->
          <el-table-column v-if="canApply" label="售后" width="160" align="center">
            <template #default="{ row }">
              <el-button type="text" class="danger-btn" @click="goPart(row)">部分退款</el-button>
              <el-button type="text" @click="goExchange(row)">换货</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div style="display:flex;justify-content:flex-end;margin-top:16px">
          <div style="width:280px">
            <div class="sum-row"><span>商品总额</span><span>¥{{ $price(order.totalAmount) }}</span></div>
            <div class="sum-row"><span>运费</span><span>{{ freightText }}</span></div>
            <div class="sum-row"><span>优惠</span><span class="money">-¥{{ $price(order.discountAmount) }}</span></div>
            <div class="sum-row pay"><span>实付金额</span><span class="money-lg">¥{{ $price(order.payAmount) }}</span></div>
          </div>
        </div>
      </div>

      <!-- 状态时间线 -->
      <div class="page-card">
        <div class="page-head"><span class="page-title">订单流转</span></div>
        <el-timeline class="timeline-box">
          <el-timeline-item
            v-for="(log, i) in logs"
            :key="i"
            :timestamp="shortTime(log.createTime)"
            placement="top"
            :type="i === 0 ? 'primary' : ''"
          >
            <div style="font-size:14px">{{ log.content }}</div>
            <div class="muted" style="margin-top:2px">{{ log.operator }} · {{ operatorTypeText(log.operatorType) }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="!logs.length" description="暂无流转记录" :image-size="80" />
      </div>
    </template>

    <div v-else-if="loaded" class="page-card">
      <el-empty description="订单不存在或无权查看" />
    </div>

    <!-- 申请开票弹窗（V12）：金额由服务端取订单实付，这里只读展示 -->
    <el-dialog title="申请开票" v-model="invoiceDialog" width="480px" append-to-body>
      <el-form :model="invoiceForm" label-width="96px" size="small">
        <el-form-item label="开票金额">
          <span class="money">¥{{ $price(order.payAmount) }}</span>
          <span class="muted" style="margin-left:10px">按订单实付金额开具，不可修改</span>
        </el-form-item>
        <el-form-item label="抬头类型">
          <el-radio-group v-model="invoiceForm.titleType">
            <el-radio :label="1">个人</el-radio>
            <el-radio :label="2">企业</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发票抬头" required>
          <el-input v-model="invoiceForm.title" maxlength="60" placeholder="个人姓名或企业全称" />
        </el-form-item>
        <el-form-item v-if="Number(invoiceForm.titleType) === 2" label="纳税人识别号" required>
          <el-input v-model="invoiceForm.taxNo" maxlength="20" placeholder="15-20 位字母或数字" />
        </el-form-item>
        <el-form-item label="接收邮箱">
          <el-input v-model="invoiceForm.email" maxlength="60" placeholder="选填，开票后发送电子发票" />
        </el-form-item>
      </el-form>
      <template #footer><span>
        <el-button size="small" @click="invoiceDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="invoiceSubmitting" @click="submitInvoice">提交申请</el-button>
      </span></template>
    </el-dialog>
  </div>
</template>

<script>
import { getOrderDetail, cancelOrder, confirmReceipt } from '@/api/order'
import { getOrderRefundState } from '@/api/refund'
import { getDeliveryTrack } from '@/api/delivery'
import { applyInvoice, getInvoiceByOrder } from '@/api/invoice'
import { orderStatusText, refundStatusText, refundStatusType, refundTypeText, specText, shortTime } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'

const STATUS_HINT = {
  10: '订单已提交，请尽快完成支付',
  20: '已支付，等待商家发货',
  30: '商家已发货，收到商品后请确认收货',
  40: '订单已完成，感谢购买',
  50: '订单已取消，库存与优惠券已回补',
  60: '订单已关闭'
}

const INVOICE_STATUS = { 0: '待开票', 1: '已开票', 2: '已红冲' }

export default {
  name: 'OrderDetail',
  components: { PcImage },
  data() {
    return {
      order: {},
      items: [],
      logs: [],
      // V10 订单所属店铺（详情接口在 data 根上返回 merchantName）
      shopName: '',
      activeRefund: {},
      canApply: false,
      // V12 物流：packages 为多包裹数组，trackLoaded 控制卡片显隐（不点不请求）
      packages: [],
      trackTip: '',
      trackLoaded: false,
      // V12 发票：null 表示该订单未申请
      invoice: null,
      invoiceDialog: false,
      invoiceSubmitting: false,
      invoiceForm: { titleType: 1, title: '', taxNo: '', email: '' },
      loading: false,
      loaded: false
    }
  },
  computed: {
    statusHint() {
      return STATUS_HINT[Number(this.order.status)] || ''
    },
    freightText() {
      const fen = Number(this.order.freightAmount) || 0
      return fen > 0 ? '¥' + this.$price(fen) : '免运费'
    },
    canViewLogistics() {
      return [30, 40].includes(Number(this.order.status))
    },
    canApplyInvoice() {
      // 一单一票：已申请（含已开票/已红冲）就不再给入口
      return Number(this.order.status) === 40 && !(this.invoice && this.invoice.id)
    }
  },
  created() {
    this.load()
  },
  methods: {
    orderStatusText,
    refundStatusText,
    refundStatusType,
    refundTypeText,
    specText,
    shortTime,
    // V15：订单项级售后入口 → 售后申请页（带订单项参数）
    goExchange(row) {
      this.$router.push({ path: '/refund/apply', query: { orderId: this.order.id, mode: 'exchange', orderItemId: row.id } })
    },
    goPart(row) {
      this.$router.push({ path: '/refund/apply', query: { orderId: this.order.id, mode: 'part', orderItemId: row.id } })
    },
    invoiceStatusText(status) {
      return INVOICE_STATUS[Number(status)] || '—'
    },
    pkgItems(pkg) {
      const list = pkg.items || []
      if (!list.length) return ''
      return '含 ' + list.map(i => `${i.goodsName}×${i.quantity}`).join('、')
    },
    operatorTypeText(type) {
      return { 1: '会员', 2: '管理员', 3: '系统' }[Number(type)] || '系统'
    },
    async load() {
      this.loading = true
      try {
        const res = await getOrderDetail(this.$route.params.id)
        this.order = (res && res.order) || {}
        this.shopName = (res && res.merchantName) || ''
        this.items = (res && res.items) || []
        this.logs = ((res && res.logs) || []).slice().reverse()
        this.loadRefundState()
        this.loadInvoice()
      } finally {
        this.loading = false
        this.loaded = true
      }
    },
    // 发票查询失败静默：拿不到状态不影响看订单
    async loadInvoice() {
      if (!this.order.id) return
      try {
        const res = await getInvoiceByOrder(this.order.id)
        this.invoice = res || null
      } catch (e) {
        this.invoice = null
      }
    },
    async loadTrack() {
      try {
        const res = await getDeliveryTrack(this.order.id)
        this.packages = (res && res.packages) || []
        this.trackTip = (res && res.tip) || ''
      } catch (e) {
        this.packages = []
        this.trackTip = '物流信息查询失败'
      } finally {
        this.trackLoaded = true
      }
    },
    openInvoice() {
      this.invoiceForm = {
        titleType: 1,
        title: this.order.receiverName || '',
        taxNo: '',
        email: ''
      }
      this.invoiceDialog = true
    },
    async submitInvoice() {
      const f = this.invoiceForm
      if (!f.title.trim()) {
        this.$message.warning('请填写发票抬头')
        return
      }
      if (Number(f.titleType) === 2 && !/^[0-9A-Za-z]{15,20}$/.test((f.taxNo || '').trim())) {
        this.$message.warning('纳税人识别号需为 15-20 位字母或数字')
        return
      }
      this.invoiceSubmitting = true
      try {
        await applyInvoice({
          orderId: this.order.id,
          titleType: Number(f.titleType),
          title: f.title.trim(),
          taxNo: (f.taxNo || '').trim() || null,
          email: (f.email || '').trim() || null
        })
        this.$message.success('开票申请已提交，商家开票后可下载')
        this.invoiceDialog = false
        this.loadInvoice()
      } finally {
        this.invoiceSubmitting = false
      }
    },
    openInvoiceFile() {
      if (this.invoice && this.invoice.fileUrl) {
        window.open(this.invoice.fileUrl, '_blank')
      }
    },
    async loadRefundState() {
      try {
        const res = await getOrderRefundState(this.order.id)
        this.canApply = Boolean(res && res.canApply)
        this.activeRefund = (res && res.refund) || {}
      } catch (e) {
        this.canApply = false
      }
    },
    goPay() {
      this.$router.push({ path: '/pay/' + this.order.orderNo, query: { payAmount: this.order.payAmount } })
    },
    async onCancel() {
      await this.$confirm('取消后库存与优惠券会回补，确定取消该订单？', '取消订单', { type: 'warning' })
      await cancelOrder(this.order.id)
      this.$message.success('订单已取消')
      this.load()
    },
    async onReceipt() {
      await this.$confirm('确认已经收到商品？', '确认收货', { type: 'warning' })
      await confirmReceipt(this.order.id)
      this.$message.success('已确认收货')
      this.load()
    }
  }
}
</script>

<style scoped>
/* V12 物流：多包裹时每块之间给分隔线，单包裹时无线 */
.pkg + .pkg {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed #ebeef5;
}

.pkg-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  margin-bottom: 10px;
}

.pkg-no {
  font-family: Consolas, Monaco, monospace;
  color: #303133;
}

.pkg-items {
  margin-left: auto;
  max-width: 46%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
