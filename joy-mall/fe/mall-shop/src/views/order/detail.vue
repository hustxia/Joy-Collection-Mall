<template>
  <div class="page">
    <van-nav-bar title="订单详情" left-arrow @click-left="router.back()" />
    <template v-if="order">
      <!-- 状态条 -->
      <div class="status-banner">
        {{ orderStatusText(order.status) }}
        <span v-if="order.expressNo" class="express">{{ order.expressCompany }} {{ order.expressNo }}</span>
      </div>

      <!-- 地址快照 -->
      <van-cell-group>
        <van-cell :title="`${order.receiverName}  ${order.receiverPhone}`" :label="order.receiverAddress">
          <template #icon><van-icon name="location-o" class="addr-icon" /></template>
        </van-cell>
      </van-cell-group>

      <!-- 商品明细 + 金额 -->
      <van-cell-group inset class="block">
        <div v-for="it in items" :key="it.id" class="o-item">
          <van-image :src="it.skuImage" fit="cover" class="oi-img" />
          <div class="oi-mid">
            <div class="van-ellipsis">{{ it.goodsName }}</div>
            <div class="text-gray oi-spec">{{ specText(it.specJson) }}</div>
            <!-- V7：已完成订单的订单项可评价（每项限一次） -->
            <van-button
              v-if="order.status === 40 && !reviewedItems.has(String(it.id))"
              size="mini"
              round
              plain
              type="primary"
              class="oi-review-btn"
              @click="openReview(it)"
            >评价</van-button>
            <span v-else-if="order.status === 40" class="text-gray oi-reviewed">已评价</span>
            <!-- V15：订单项级售后入口（换货 / 部分退款） -->
            <div v-if="canApplyRefund" class="oi-aftersale">
              <van-button size="mini" round plain type="warning" @click="goApplyExchange(it)">换货</van-button>
              <van-button size="mini" round plain type="danger" @click="goApplyPartial(it)">部分退款</van-button>
            </div>
          </div>
          <div class="oi-right">
            <div class="price">¥{{ fenToYuan(it.price) }}</div>
            <div class="text-gray">x{{ it.quantity }}</div>
          </div>
        </div>
        <van-cell title="商品总额" :value="`¥${fenToYuan(order.totalAmount)}`" />
        <van-cell title="运费">
          <template #value>
            <span v-if="freightFen > 0" class="price">+¥{{ fenToYuan(freightFen) }}</span>
            <span v-else class="free">免运费</span>
          </template>
        </van-cell>
        <van-cell title="优惠" :value="`-¥${fenToYuan(order.discountAmount)}`" />
        <van-cell title="实付">
          <template #value><span class="price">¥{{ fenToYuan(order.payAmount) }}</span></template>
        </van-cell>
      </van-cell-group>

      <!-- 订单信息 -->
      <van-cell-group inset class="block">
        <van-cell title="订单号" :value="order.orderNo" />
        <van-cell title="下单时间" :value="order.createTime" />
        <van-cell v-if="order.payTime" title="支付时间" :value="order.payTime" />
        <van-cell v-if="order.buyerRemark" title="买家留言" :value="order.buyerRemark" />
      </van-cell-group>

      <!-- 物流轨迹（V12）：一单可能多包裹，默认只展开最新 3 条节点 -->
      <van-cell-group v-if="packages.length" inset class="block">
        <div class="tl-title">
          物流信息
          <span class="text-gray pk-total">{{ packages.length }} 个包裹</span>
        </div>
        <div v-for="(pkg, pi) in packages" :key="pi" class="pk">
          <div class="pk-head">
            <van-tag type="primary" plain>{{ pkg.expressCompany }}</van-tag>
            <span class="pk-no">{{ pkg.expressNo }}</span>
            <span v-if="pkg.items && pkg.items.length" class="text-gray pk-goods">
              {{ pkg.items.map(i => `${i.goodsName}×${i.quantity}`).join('、') }}
            </span>
          </div>
          <div class="tl pk-tl">
            <div v-for="(node, ni) in visibleNodes(pkg, pi)" :key="ni" class="tl-item">
              <div class="dot" :class="{ on: ni === 0 }"></div>
              <div class="tl-txt">
                <div class="tt">{{ node.desc }}</div>
                <div class="text-gray">{{ node.status }} · {{ node.time }}</div>
              </div>
            </div>
          </div>
          <div
            v-if="(pkg.nodes || []).length > 3"
            class="pk-more"
            @click="togglePkg(pi)"
          >{{ expandedPkg.has(pi) ? '收起轨迹' : `展开全部 ${pkg.nodes.length} 条轨迹` }}</div>
        </div>
      </van-cell-group>

      <!-- 发票（V12）：已申请展示状态，已完成未申请给入口 -->
      <van-cell-group v-if="invoice || order.status === 40" inset class="block">
        <van-cell
          v-if="invoice"
          :title="`发票：${invoiceStatusText(invoice.status)}`"
          :label="`${invoice.title} · ¥${fenToYuan(invoice.amount)}${invoice.invoiceNo ? ' · ' + invoice.invoiceNo : ''}`"
          :is-link="Boolean(invoice.fileUrl)"
          @click="onInvoiceCell"
        />
        <van-cell v-else title="申请开票" label="按订单实付金额开具电子发票" is-link @click="openInvoice" />
      </van-cell-group>

      <!-- 售后状态卡（V4） -->
      <van-cell-group v-if="refund" inset class="block refund-card" @click="router.push(`/refund/${refund.id}`)">
        <van-cell :title="`售后：${refundStatusText(refund.status)}`" is-link
          :label="`售后单号 ${refund.refundNo} · ${refundTypeText(refund.type)} · ¥${fenToYuan(refund.amount)}`" />
      </van-cell-group>

      <!-- 状态时间线 -->
      <van-cell-group inset class="block">
        <div class="tl-title">订单动态</div>
        <div class="tl">
          <div v-for="(log, i) in logs" :key="i" class="tl-item">
            <div class="dot" :class="{ on: i === logs.length - 1 }"></div>
            <div class="tl-txt">
              <div class="tt">{{ log.content }}</div>
              <div class="text-gray">{{ log.createTime }}</div>
            </div>
          </div>
        </div>
      </van-cell-group>

      <!-- 底部操作 -->
      <div v-if="order.status === 10 || order.status === 30 || canApplyRefund" class="op-bar">
        <template v-if="order.status === 10">
          <van-button round plain size="small" :loading="acting" @click="onCancel">取消订单</van-button>
          <van-button round color="#4a6cf7" size="small" @click="router.push(`/pay/${order.orderNo}?payAmount=${order.payAmount}`)">去支付</van-button>
        </template>
        <template v-else>
          <van-button v-if="order.status === 30" round color="#4a6cf7" size="small" :loading="acting" @click="onConfirmReceipt">确认收货</van-button>
          <van-button v-if="canApplyRefund" round plain type="danger" size="small" @click="router.push(`/refund/apply?orderId=${orderId}`)">申请售后</van-button>
        </template>
      </div>
      <!-- 评价弹窗（V7）：评分必填 + 文字选填 + 图片选填（复用 /shop/upload） -->
      <van-popup v-model:show="showReview" position="bottom" round>
        <div class="rv-sheet">
          <div class="rv-title">评价商品</div>
          <div class="rv-goods">{{ reviewingItem?.goodsName }}</div>
          <div class="rv-rate">
            <span class="rv-rate-label">评分</span>
            <van-rate v-model="reviewForm.rating" color="#ffb400" />
          </div>
          <van-field
            v-model="reviewForm.content"
            type="textarea"
            rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说说商品怎么样吧（选填）"
          />
          <div class="uploader">
            <van-uploader
              v-model="reviewFiles"
              :max-count="6"
              :max-size="5 * 1024 * 1024"
              :after-read="afterReviewRead"
              :deletable="true"
            />
          </div>
          <van-button block round color="#4a6cf7" :loading="reviewing" @click="onSubmitReview">
            提交评价
          </van-button>
        </div>
      </van-popup>

      <!-- 申请开票弹窗（V12）：金额由服务端取订单实付，此处只读 -->
      <van-popup v-model:show="showInvoice" position="bottom" round>
        <div class="iv-sheet">
          <div class="rv-title">申请开票</div>
          <div class="iv-amount">
            开票金额 <span class="price">¥{{ fenToYuan(order.payAmount) }}</span>
            <span class="text-gray iv-hint">按实付金额开具，不可修改</span>
          </div>
          <van-field label="抬头类型">
            <template #input>
              <van-radio-group v-model="invoiceForm.titleType" direction="horizontal">
                <van-radio :name="1">个人</van-radio>
                <van-radio :name="2">企业</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field
            v-model="invoiceForm.title"
            label="发票抬头"
            placeholder="个人姓名或企业全称"
            maxlength="60"
          />
          <van-field
            v-if="invoiceForm.titleType === 2"
            v-model="invoiceForm.taxNo"
            label="税号"
            placeholder="15-20 位字母或数字"
            maxlength="20"
          />
          <van-field v-model="invoiceForm.email" label="接收邮箱" placeholder="选填" maxlength="60" />
          <van-button block round color="#4a6cf7" :loading="invoiceSubmitting" @click="submitInvoice">
            提交申请
          </van-button>
        </div>
      </van-popup>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getOrderDetail, cancelOrder, confirmReceipt } from '@/api/order'
import { submitReview, getMyReviews, uploadReviewImage } from '@/api/review'
import { getDeliveryTrack } from '@/api/delivery'
import { applyInvoice, getInvoiceByOrder } from '@/api/invoice'
import { fenToYuan, orderStatusText, refundStatusText, refundTypeText, specText } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const orderId = route.params.id

const order = ref(null)
const items = ref([])
const logs = ref([])
const refund = ref(null)
const acting = ref(false)

// V12 物流：packages 可能多个；expandedPkg 记录哪些包裹展开了全部轨迹
const packages = ref([])
const expandedPkg = ref(new Set())
const visibleNodes = (pkg, pi) => {
  const nodes = pkg.nodes || []
  return expandedPkg.value.has(pi) ? nodes : nodes.slice(0, 3)
}
const togglePkg = pi => {
  const next = new Set(expandedPkg.value)
  if (next.has(pi)) next.delete(pi)
  else next.add(pi)
  expandedPkg.value = next
}

// V12 发票：一单一票，invoice 有值即已申请
const invoice = ref(null)
const showInvoice = ref(false)
const invoiceSubmitting = ref(false)
const invoiceForm = ref({ titleType: 1, title: '', taxNo: '', email: '' })
const INVOICE_STATUS = { 0: '待开票', 1: '已开票', 2: '已红冲' }
const invoiceStatusText = s => INVOICE_STATUS[Number(s)] || '—'

const freightFen = computed(() => Number(order.value?.freightAmount) || 0)

const loadTrack = async () => {
  if (![30, 40].includes(order.value?.status)) return
  try {
    const data = await getDeliveryTrack(orderId)
    packages.value = data?.packages || []
  } catch (e) {
    packages.value = []
  }
}

const loadInvoice = async () => {
  try {
    invoice.value = (await getInvoiceByOrder(orderId)) || null
  } catch (e) {
    invoice.value = null
  }
}

const openInvoice = () => {
  invoiceForm.value = {
    titleType: 1,
    title: order.value?.receiverName || '',
    taxNo: '',
    email: '',
  }
  showInvoice.value = true
}

const submitInvoice = async () => {
  const f = invoiceForm.value
  if (!f.title.trim()) return showToast('请填写发票抬头')
  if (f.titleType === 2 && !/^[0-9A-Za-z]{15,20}$/.test((f.taxNo || '').trim())) {
    return showToast('税号需为 15-20 位字母或数字')
  }
  invoiceSubmitting.value = true
  try {
    await applyInvoice({
      orderId,
      titleType: Number(f.titleType),
      title: f.title.trim(),
      taxNo: (f.taxNo || '').trim() || null,
      email: (f.email || '').trim() || null,
    })
    showToast('申请已提交，开票后可下载')
    showInvoice.value = false
    await loadInvoice()
  } finally {
    invoiceSubmitting.value = false
  }
}

const onInvoiceCell = () => {
  if (invoice.value?.fileUrl) window.open(invoice.value.fileUrl, '_blank')
}

// V7 评价：已评价的订单项集合（隐藏按钮），弹窗表单
const reviewedItems = ref(new Set())
const showReview = ref(false)
const reviewingItem = ref(null)
const reviewForm = ref({ rating: 5, content: '' })
const reviewFiles = ref([])
const reviewing = ref(false)

const openReview = it => {
  reviewingItem.value = it
  reviewForm.value = { rating: 5, content: '' }
  reviewFiles.value = []
  showReview.value = true
}

// 图片逐张上传，url 留在 file 对象上，提交时收集
const afterReviewRead = async fileItem => {
  fileItem.status = 'uploading'
  fileItem.message = '上传中'
  try {
    const fd = new FormData()
    fd.append('file', fileItem.file)
    const data = await uploadReviewImage(fd)
    fileItem.url = data.url
    fileItem.status = 'done'
    fileItem.message = ''
  } catch (e) {
    fileItem.status = 'failed'
    fileItem.message = '上传失败'
    const idx = reviewFiles.value.indexOf(fileItem)
    if (idx >= 0) reviewFiles.value.splice(idx, 1)
  }
}

const onSubmitReview = async () => {
  const it = reviewingItem.value
  if (!it) return
  if (!reviewForm.value.rating) return showToast('请给商品打分')
  reviewing.value = true
  try {
    await submitReview({
      orderItemId: it.id,
      orderId: order.value.id,
      goodsId: it.goodsId,
      skuId: it.skuId,
      rating: reviewForm.value.rating,
      content: reviewForm.value.content.trim(),
      images: reviewFiles.value.map(f => f.url).filter(Boolean).join(','),
    })
    showToast('评价成功')
    reviewedItems.value = new Set([...reviewedItems.value, String(it.id)])
    showReview.value = false
  } finally {
    reviewing.value = false
  }
}

// 仅 30 已发货 / 40 已完成 且无进行中售后（refund 有值即说明有售后记录，
// 其中 3/5 是终态可重新申请）时可申请
const canApplyRefund = computed(() => {
  if (!order.value || ![30, 40].includes(order.value.status)) return false
  if (!refund.value) return true
  return [3, 5].includes(refund.value.status)
})

const load = async () => {
  const data = await getOrderDetail(orderId)
  order.value = data.order
  items.value = data.items || []
  logs.value = data.logs || []
  refund.value = data.refund || null
  // V12：已发货/已完成才拉物流与发票，避免无谓请求
  if ([30, 40].includes(order.value?.status)) loadTrack()
  if (order.value?.status === 40) loadInvoice()
  // V7：已完成订单拉一次「我的评价」标出已评项（失败静默，按钮仍可点、后端幂等拦截）
  if (order.value?.status === 40) {
    getMyReviews({ pageNum: 1, pageSize: 60 })
      .then(d => {
        reviewedItems.value = new Set((d.records || []).map(r => String(r.orderItemId)))
      })
      .catch(() => {})
  }
}

// V15：订单项级售后入口 → 售后申请页（带订单项参数）
const goApplyExchange = it => {
  router.push(`/refund/apply?orderId=${orderId}&mode=exchange&orderItemId=${it.id}`)
}
const goApplyPartial = it => {
  router.push(`/refund/apply?orderId=${orderId}&mode=part&orderItemId=${it.id}`)
}

const onCancel = async () => {
  await showConfirmDialog({ title: '取消订单', message: '确定取消该订单吗？', confirmButtonColor: '#4a6cf7' })
  acting.value = true
  try {
    await cancelOrder(orderId)
    showToast('已取消')
    await load()
  } finally {
    acting.value = false
  }
}

const onConfirmReceipt = async () => {
  await showConfirmDialog({ title: '确认收货', message: '确认已收到商品吗？', confirmButtonColor: '#4a6cf7' })
  acting.value = true
  try {
    await confirmReceipt(orderId)
    showToast('已确认收货')
    await load()
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.status-banner {
  background: linear-gradient(135deg, #4a6cf7, #7a9bff);
  color: #fff;
  padding: 16px;
  font-size: 16px;
  font-weight: 600;
}

.express {
  font-size: 12px;
  font-weight: 400;
  opacity: 0.85;
  margin-left: 10px;
}

.addr-icon {
  font-size: 22px;
  color: #4a6cf7;
  margin-right: 10px;
  align-self: center;
}

.block {
  margin-top: 8px;
}

.o-item {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
}

.oi-img {
  width: 56px;
  height: 56px;
  border-radius: 6px;
  overflow: hidden;
  flex: none;
}

.oi-mid {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}

.oi-spec {
  font-size: 12px;
  margin-top: 4px;
}

.oi-right {
  text-align: right;
  font-size: 12px;
}

.tl-title {
  padding: 12px 16px 0;
  font-size: 14px;
  font-weight: 600;
}

.tl {
  padding: 12px 16px 16px;
}

.tl-item {
  display: flex;
  gap: 10px;
  position: relative;
  padding-bottom: 18px;
}

.tl-item::before {
  content: '';
  position: absolute;
  left: 4px;
  top: 14px;
  bottom: -2px;
  width: 1px;
  background: #e0e0e0;
}

.tl-item:last-child::before {
  display: none;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #ccc;
  flex: none;
  margin-top: 3px;
  z-index: 1;
}

.dot.on {
  background: #4a6cf7;
}

.tl-txt {
  flex: 1;
  font-size: 12px;
}

.tt {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 2px;
}

.op-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  max-width: 750px;
  margin: 0 auto;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  background: #fff;
  border-top: 1px solid #ebedf0;
  padding: 10px 12px;
}

.refund-card { cursor: pointer; }

.oi-review-btn {
  margin-top: 6px;
}

/* V15 订单项级售后入口 */
.oi-aftersale {
  margin-top: 6px;
  display: flex;
  gap: 6px;
}

.oi-reviewed {
  display: inline-block;
  margin-top: 6px;
  font-size: 11px;
}

.rv-sheet {
  padding: 16px 16px 24px;
}

.rv-title {
  font-size: 15px;
  font-weight: 600;
  text-align: center;
}

.rv-goods {
  margin-top: 6px;
  text-align: center;
  font-size: 12px;
  color: #969799;
}

.rv-rate {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 14px 0;
}

.rv-rate-label {
  font-size: 13px;
  color: #646566;
}

.uploader {
  padding: 10px 0 14px;
}

/* V12 物流 / 发票 */
.free {
  color: #07c160;
  font-size: 13px;
}

.pk-total {
  font-size: 12px;
  font-weight: 400;
  margin-left: 8px;
}

.pk {
  padding: 10px 16px 14px;
}

.pk + .pk {
  border-top: 1px dashed #ebedf0;
}

.pk-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.pk-no {
  font-family: Consolas, Monaco, monospace;
  color: #323233;
}

.pk-goods {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: right;
}

.pk-tl {
  padding: 10px 0 0;
}

.pk-more {
  padding-top: 4px;
  font-size: 12px;
  color: #4a6cf7;
  text-align: center;
}

.iv-sheet {
  padding: 16px 16px 24px;
}

.iv-amount {
  margin: 10px 0 6px;
  text-align: center;
  font-size: 13px;
  color: #646566;
}

.iv-hint {
  display: block;
  margin-top: 2px;
  font-size: 11px;
}

.iv-sheet .van-button {
  margin-top: 16px;
}
</style>
