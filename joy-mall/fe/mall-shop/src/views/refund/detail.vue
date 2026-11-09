<template>
  <div class="page">
    <van-nav-bar title="售后详情" left-arrow @click-left="router.back()" />

    <template v-if="refund">
      <div class="banner">
        <div class="st">{{ refundStatusText(refund.status) }}</div>
        <div class="sub">
          {{ refund.status === 4 ? `退款 ¥${fenToYuan(refund.amount)} 已于 ${fmtTime(refund.refundTime)} 原路退回`
            : refund.status === 1 ? (refund.platformStatus === 1 ? '平台仲裁中，请耐心等待' : '商家会尽快审核您的申请，超 48 小时未处理可申请平台介入')
            : refund.status === 2 ? (refund.type === 3 ? '商家已同意，请尽快寄回商品' : '商家已同意，退款处理中')
            : refund.status === 3 ? `审核意见：${refund.auditRemark || '无'}`
            : refund.status === 6 ? '已寄回，等待商家收货确认'
            : refund.status === 7 ? '商家已收货，等待发出换货'
            : refund.status === 8 ? '换货完成，商家已发出换货商品'
            : '售后已关闭' }}
        </div>
      </div>

      <van-cell-group inset class="block">
        <div v-for="it in items" :key="it.id" class="o-item">
          <van-image :src="it.skuImage" fit="cover" class="oi-img" />
          <div class="oi-mid">
            <div class="van-ellipsis">{{ it.goodsName }}</div>
            <div class="text-gray oi-spec">{{ specText(it.specJson) }} x{{ it.quantity }}</div>
          </div>
          <div class="oi-right"><span class="price">¥{{ fenToYuan(it.price) }}</span></div>
        </div>
      </van-cell-group>

      <van-cell-group inset class="block">
        <van-cell title="售后单号" :value="refund.refundNo" />
        <van-cell title="售后类型" :value="refundTypeText(refund.type)" />
        <van-cell title="退款金额">
          <template #value><span class="price">¥{{ fenToYuan(refund.amount) }}</span></template>
        </van-cell>
        <van-cell title="申请原因" :label="refund.reason" />
        <!-- V15：换货信息 -->
        <van-cell v-if="refund.type === 3" title="换入规格" :value="`SKU #${refund.exchangeSkuId} × ${refund.quantity || 1}`" />
        <van-cell v-if="refund.shipTime" title="寄回时间" :value="fmtTime(refund.shipTime)" />
        <!-- V15：平台介入进度 -->
        <van-cell
          v-if="Number(refund.platformStatus) > 0"
          title="平台介入"
          :value="{ 1: '仲裁中', 2: '已强制退款', 3: '已驳回' }[Number(refund.platformStatus)] || '—'"
          :label="refund.platformRemark || ''"
        />
        <van-cell v-if="refund.status === 3" title="审核意见" :value="refund.auditRemark || '无'" />
        <van-cell v-if="refund.refundTime" title="退款时间" :value="fmtTime(refund.refundTime)" />
      </van-cell-group>

      <van-cell-group v-if="images.length" inset class="block">
        <div class="sec-title">凭证图</div>
        <div class="imgs">
          <van-image v-for="(img, i) in images" :key="i" :src="img" fit="cover" class="img" @click="onPreview(i)" />
        </div>
      </van-cell-group>

      <van-cell-group inset class="block">
        <div class="sec-title">进度时间线</div>
        <div class="tl">
          <div v-for="(log, i) in timeline" :key="i" class="tl-item">
            <div class="dot" :class="{ on: i === timeline.length - 1 }"></div>
            <div class="tl-txt">
              <div class="tt">{{ log.content }}</div>
              <div class="text-gray">{{ log.createTime }} · {{ log.operatorType === 1 ? '会员' : log.operatorType === 2 ? '商家' : '系统' }}</div>
            </div>
          </div>
        </div>
      </van-cell-group>

      <div v-if="refund.status === 1 || refund.status === 2" class="op-bar">
        <!-- V15：换货已同意（待寄回）→ 买家寄回 -->
        <van-button
          v-if="refund.status === 2 && refund.type === 3"
          round
          type="warning"
          size="small"
          :loading="acting"
          @click="onShipBack"
        >我已寄回</van-button>
        <van-button round plain size="small" :loading="acting" @click="onClose">撤销申请</van-button>
        <!-- V15：待审核且未介入过 → 可申请平台介入（商家超 48 小时未处理） -->
        <van-button
          v-if="refund.status === 1 && Number(refund.platformStatus) === 0"
          round
          plain
          type="warning"
          size="small"
          :loading="acting"
          @click="onIntervene"
        >申请平台介入</van-button>
        <van-button round color="#4a6cf7" size="small" @click="router.push(`/order/${refund.orderId}`)">查看订单</van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showImagePreview, showSuccessToast, showToast } from 'vant'
import { closeRefund, getRefundDetail, shipRefundBack, interveneRefund } from '@/api/refund'
import { fenToYuan, refundStatusText, refundTypeText, specText } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const refundId = route.params.id

const refund = ref(null)
const items = ref([])
const timeline = ref([])
const acting = ref(false)

const images = computed(() => {
  try {
    const parsed = JSON.parse(refund.value?.evidenceImages || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
})

const fmtTime = t => (t ? String(t).replace('T', ' ').slice(0, 19) : '')

const load = async () => {
  const data = await getRefundDetail(refundId)
  refund.value = data.refund
  items.value = data.items || []
  timeline.value = data.timeline || []
}

const onPreview = i => showImagePreview({ images: images.value, startPosition: i })

const onClose = async () => {
  await showConfirmDialog({
    title: '撤销售后',
    message: '确定撤销该售后申请吗？撤销后可重新发起。',
    confirmButtonColor: '#4a6cf7',
  })
  acting.value = true
  try {
    await closeRefund(refundId)
    showSuccessToast('已撤销')
    await load()
  } catch (e) {
    showToast(e.message || '撤销失败')
  } finally {
    acting.value = false
  }
}

// V15：换货买家寄回（仅「已同意待寄回」）
const onShipBack = async () => {
  await showConfirmDialog({
    title: '填写寄回',
    message: '确认已将商品寄出吗？确认后等待商家收货。',
    confirmButtonColor: '#4a6cf7',
  })
  acting.value = true
  try {
    await shipRefundBack(refundId)
    showSuccessToast('已确认寄回')
    await load()
  } catch (e) {
    showToast(e.message || '操作失败')
  } finally {
    acting.value = false
  }
}

// V15：申请平台介入（后端校验 48 小时超时，未超时会提示）
const onIntervene = async () => {
  await showConfirmDialog({
    title: '申请平台介入',
    message: '商家超 48 小时未处理时可申请平台仲裁，确定申请吗？',
    confirmButtonColor: '#4a6cf7',
  })
  acting.value = true
  try {
    await interveneRefund(refundId)
    showSuccessToast('已提交平台介入申请')
    await load()
  } catch (e) {
    showToast(e.message || '申请失败')
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.banner {
  background: linear-gradient(135deg, #4a6cf7, #7a9bff);
  color: #fff;
  padding: 18px 16px;
}
.banner .st { font-size: 19px; font-weight: 700; }
.banner .sub { font-size: 12px; opacity: 0.88; margin-top: 5px; }
.block { margin-top: 10px; }
.o-item { display: flex; gap: 10px; padding: 12px 16px 4px; }
.oi-img { width: 48px; height: 48px; border-radius: 6px; overflow: hidden; flex: none; }
.oi-mid { flex: 1; min-width: 0; font-size: 13px; }
.oi-spec { font-size: 12px; margin-top: 3px; }
.oi-right { font-size: 13px; }
.price { color: #ee0a24; font-weight: 600; }
.sec-title { padding: 12px 16px 8px; font-size: 14px; font-weight: 600; }
.imgs { display: flex; gap: 8px; flex-wrap: wrap; padding: 0 16px 12px; }
.img { width: 72px; height: 72px; border-radius: 8px; overflow: hidden; }
.tl { padding: 4px 16px 16px; }
.tl-item { display: flex; gap: 10px; position: relative; padding-bottom: 16px; }
.tl-item::before {
  content: '';
  position: absolute;
  left: 4px;
  top: 14px;
  bottom: -2px;
  width: 1px;
  background: #e0e0e0;
}
.tl-item:last-child::before { display: none; }
.dot { width: 9px; height: 9px; border-radius: 50%; background: #ccc; flex: none; margin-top: 3px; z-index: 1; }
.dot.on { background: #4a6cf7; }
.tl-txt { flex: 1; font-size: 12px; }
.tt { font-size: 13px; font-weight: 600; margin-bottom: 2px; }
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
</style>
