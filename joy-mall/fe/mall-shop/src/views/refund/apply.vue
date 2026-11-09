<template>
  <div class="page">
    <van-nav-bar title="申请售后" left-arrow @click-left="router.back()" />

    <template v-if="loaded && order">
      <div class="banner">
        <div class="st">{{ TYPE_NAME[type] || '仅退款' }}</div>
        <div class="sub">{{ type === 3 ? '换货需商家审核，通过后请寄回商品' : '退款金额将原路退回（mock 渠道），提交后等待商家审核' }}</div>
      </div>

      <van-cell-group inset class="block">
        <div v-for="it in items" :key="it.id" class="o-item" :class="{ selectable: needItem, selected: needItem && selectedItemId === it.id }" @click="needItem && selectItem(it)">
          <van-icon v-if="needItem" :name="selectedItemId === it.id ? 'checked' : 'circle'" class="pick" :class="{ on: selectedItemId === it.id }" />
          <van-image :src="thumb(it.skuImage, 80)" fit="cover" class="oi-img" />
          <div class="oi-mid">
            <div class="van-ellipsis">{{ it.goodsName }}</div>
            <div class="text-gray oi-spec">{{ specText(it.specJson) }} x{{ it.quantity }}</div>
          </div>
          <div class="oi-right price">¥{{ fenToYuan(it.totalAmount || it.price * it.quantity) }}</div>
        </div>
        <van-cell v-if="type !== 3" title="可退金额（实付）">
          <template #value><span class="price">¥{{ fenToYuan(order.payAmount) }}</span></template>
        </van-cell>
      </van-cell-group>

      <!-- 换货：选换入 SKU（V15） -->
      <van-cell-group v-if="type === 3" inset class="block">
        <div class="sec-title">换入商品规格 <span class="opt">（必选）</span></div>
        <div v-if="skuLoading" class="sku-loading text-gray">规格加载中…</div>
        <template v-else>
          <div v-for="sku in skus" :key="sku.id" class="sku-opt" :class="{ on: exchangeSkuId === sku.id }" @click="exchangeSkuId = sku.id">
            <div class="van-ellipsis">{{ skuSpecText(sku) }}</div>
            <div class="sku-meta">¥{{ fenToYuan(sku.price) }} · 库存 {{ sku.stock }}</div>
          </div>
          <div v-if="!skus.length" class="sku-loading text-gray">该商品暂无可换规格</div>
          <van-stepper v-model="quantity" :min="1" :max="selectedItem ? selectedItem.quantity : 1" class="qty-step" />
          <div class="up-tip">换货数量不超过该订单项购买数量</div>
        </template>
      </van-cell-group>

      <!-- 部分退款：按订单项填金额（V15） -->
      <van-cell-group v-if="type === 4" inset class="block">
        <van-field
          v-model="amountYuan"
          type="number"
          label="退款金额（元）"
          :placeholder="selectedItem ? `最多 ${fenToYuan(selectedItem.totalAmount || selectedItem.price * selectedItem.quantity)}` : '请先选择订单项'"
        />
        <van-cell title="订单实付" :value="`¥${fenToYuan(order.payAmount)}`" />
        <div class="up-tip">一单可多次部分退款，累计不超过实付金额</div>
      </van-cell-group>

      <van-cell-group v-if="type !== 3" inset class="block">
        <div class="sec-title">售后类型</div>
        <div class="type-opts">
          <div class="type-opt" :class="{ on: type === 1 }" @click="switchType(1)">
            仅退款
            <div class="t2">未收到货或不需退回</div>
          </div>
          <div class="type-opt" :class="{ on: type === 2 }" @click="switchType(2)">
            退货退款
            <div class="t2">已收到货，退回后退款</div>
          </div>
          <div class="type-opt" :class="{ on: type === 3 }" @click="switchType(3)">
            换货
            <div class="t2">同商品换规格/型号</div>
          </div>
          <div class="type-opt" :class="{ on: type === 4 }" @click="switchType(4)">
            部分退款
            <div class="t2">按订单项选金额</div>
          </div>
        </div>
      </van-cell-group>

      <van-cell-group inset class="block">
        <van-field
          v-model="reason"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          placeholder="请详细描述您遇到的问题（必填）"
        />
      </van-cell-group>

      <van-cell-group inset class="block">
        <div class="sec-title">上传凭证 <span class="opt">（选填，最多 6 张）</span></div>
        <div class="uploader">
          <van-uploader
            v-model="fileList"
            :max-count="6"
            :max-size="5 * 1024 * 1024"
            :after-read="afterRead"
            :deletable="true"
          />
        </div>
        <div class="up-tip">支持 jpg/png/webp，单张不超过 5MB</div>
      </van-cell-group>

      <div class="note">
        · 换货/部分退款需选择具体订单项；<br>
        · 换货审核通过后请寄回商品，商家收货确认后发出换货；<br>
        · 部分退款一单可多次申请，累计不超过实付金额。
      </div>

      <div class="footer">
        <span class="sum">
          <template v-if="type === 4 && amountYuan">退 <b>¥{{ amountYuan }}</b></template>
          <template v-else-if="type === 3">换货 <b>×{{ quantity }}</b></template>
          <template v-else>可退 <b>¥{{ fenToYuan(order.payAmount) }}</b></template>
        </span>
        <van-button round color="#4a6cf7" :loading="submitting" @click="onSubmit">提交申请</van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { getOrderDetail } from '@/api/order'
import { getGoodsDetail } from '@/api/shop'
import { applyRefund, applyExchange, applyPartialRefund, uploadEvidence } from '@/api/refund'
import { fenToYuan, specText, thumb } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const orderId = route.query.orderId

const TYPE_NAME = { 1: '仅退款', 2: '退货退款', 3: '换货', 4: '部分退款' }

const order = ref(null)
const items = ref([])
const loaded = ref(false)
const type = ref(Number(route.query.mode) === 3 || route.query.mode === 'exchange' ? 3
  : Number(route.query.mode) === 4 || route.query.mode === 'part' ? 4 : 1)
const reason = ref('')
const fileList = ref([])
const submitting = ref(false)

// V15：换货/部分退款需选定订单项
const selectedItemId = ref(route.query.orderItemId ? Number(route.query.orderItemId) : null)
const needItem = computed(() => type.value === 3 || type.value === 4)
const selectedItem = computed(() => items.value.find(i => i.id === selectedItemId.value) || null)

// V15 换货：换入 SKU 与数量
const skus = ref([])
const skuLoading = ref(false)
const exchangeSkuId = ref(null)
const quantity = ref(1)
// V15 部分退款：金额（元，提交转分）
const amountYuan = ref('')

const skuSpecText = sku => {
  try {
    const spec = JSON.parse(sku.specJson || '[]')
    return spec.map(s => `${s.name}:${s.value}`).join(' ') || '默认规格'
  } catch (e) {
    return '默认规格'
  }
}

const load = async () => {
  const data = await getOrderDetail(orderId)
  order.value = data.order
  items.value = data.items || []
  loaded.value = true
  // 从订单项入口带过来的默认选中项
  if (selectedItemId.value && !items.value.some(i => i.id === selectedItemId.value)) {
    selectedItemId.value = null
  }
  if (needItem.value && selectedItemId.value) loadSkus()
}

const selectItem = it => {
  selectedItemId.value = it.id
  exchangeSkuId.value = null
  quantity.value = 1
  if (type.value === 3) loadSkus()
}

const loadSkus = async () => {
  if (!selectedItem.value) return
  skuLoading.value = true
  try {
    const goods = await getGoodsDetail(selectedItem.value.goodsId)
    skus.value = (goods && (goods.skus || goods.skuList)) || []
  } catch (e) {
    skus.value = []
  } finally {
    skuLoading.value = false
  }
}

const switchType = t => {
  type.value = t
  if (t === 3 && selectedItemId.value) loadSkus()
}

// van-uploader after-read：逐张上传后把 url 留在 file 对象上，提交时收集
const afterRead = async fileItem => {
  fileItem.status = 'uploading'
  fileItem.message = '上传中'
  try {
    const fd = new FormData()
    fd.append('file', fileItem.file)
    const data = await uploadEvidence(fd)
    fileItem.url = data.url
    fileItem.status = 'done'
    fileItem.message = ''
  } catch (e) {
    fileItem.status = 'failed'
    fileItem.message = '上传失败'
    const idx = fileList.value.indexOf(fileItem)
    if (idx >= 0) fileList.value.splice(idx, 1)
  }
}

const onSubmit = async () => {
  if (!reason.value.trim()) {
    showToast('请填写售后原因')
    return
  }
  if (needItem.value && !selectedItemId.value) {
    showToast('请选择订单项')
    return
  }
  const urls = fileList.value.map(f => f.url).filter(Boolean)
  const common = {
    orderId,
    orderItemId: selectedItemId.value,
    reason: reason.value.trim(),
    evidenceImages: urls,
  }
  submitting.value = true
  try {
    if (type.value === 3) {
      if (!exchangeSkuId.value) {
        showToast('请选择换入商品规格')
        return
      }
      await applyExchange({ ...common, exchangeSkuId: exchangeSkuId.value, quantity: quantity.value })
    } else if (type.value === 4) {
      const fen = Math.round(Number(amountYuan.value) * 100)
      if (!fen || fen <= 0) {
        showToast('请填写退款金额')
        return
      }
      await applyPartialRefund({ ...common, amount: fen })
    } else {
      await applyRefund({ orderId, type: type.value, reason: reason.value.trim(), evidenceImages: urls })
    }
    showSuccessToast('已提交申请')
    router.replace(`/refund/list`)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (!orderId) {
    showToast('缺少订单参数')
    router.replace('/order/list')
    return
  }
  load()
})
</script>

<style scoped>
.banner {
  background: linear-gradient(135deg, #4a6cf7, #7a9bff);
  color: #fff;
  padding: 16px;
}
.banner .st { font-size: 17px; font-weight: 700; }
.banner .sub { font-size: 12px; opacity: 0.85; margin-top: 4px; }
.block { margin-top: 10px; }
.o-item { display: flex; gap: 10px; padding: 12px 16px 4px; align-items: center; }
.o-item.selectable { cursor: pointer; padding: 12px 16px; }
.o-item.selected { background: #f0f3ff; }
.pick { font-size: 18px; color: #c8c9cc; flex: none; }
.pick.on { color: #4a6cf7; }
.oi-img { width: 48px; height: 48px; border-radius: 6px; overflow: hidden; flex: none; }
.oi-mid { flex: 1; min-width: 0; font-size: 13px; }
.oi-spec { font-size: 12px; margin-top: 3px; }
.oi-right { font-size: 13px; flex: none; }
.sec-title { padding: 12px 16px 8px; font-size: 14px; font-weight: 600; }
.sec-title .opt { font-size: 12px; font-weight: 400; color: #969799; }
.type-opts { display: flex; gap: 10px; padding: 0 16px 14px; flex-wrap: wrap; }
.type-opt {
  flex: 1;
  min-width: 42%;
  border: 1px solid #ebedf0;
  border-radius: 8px;
  padding: 10px;
  text-align: center;
  font-size: 13px;
  color: #646566;
}
.type-opt.on { border-color: #4a6cf7; color: #4a6cf7; background: #f0f3ff; font-weight: 600; }
.type-opt .t2 { font-size: 11px; color: #969799; margin-top: 3px; font-weight: 400; }
.sku-loading { padding: 4px 16px 14px; font-size: 12px; }
.sku-opt {
  margin: 0 16px 8px;
  border: 1px solid #ebedf0;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
}
.sku-opt.on { border-color: #4a6cf7; color: #4a6cf7; background: #f0f3ff; }
.sku-meta { font-size: 11px; color: #969799; margin-top: 2px; }
.qty-step { padding: 4px 16px 6px; }
.uploader { padding: 0 16px 4px; }
.up-tip { padding: 0 16px 12px; font-size: 11px; color: #969799; }
.note { padding: 12px 16px 90px; font-size: 11px; color: #969799; line-height: 1.7; }
.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  max-width: 750px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-top: 1px solid #ebedf0;
  padding: 10px 14px;
}
.sum { font-size: 12px; color: #646566; }
.sum b { color: #ee0a24; font-size: 17px; }
.price { color: #ee0a24; font-weight: 600; }
</style>
