<template>
  <div class="page">
    <van-nav-bar title="确认订单" left-arrow @click-left="router.back()" />

    <!-- 地址卡片：无地址引导新建 -->
    <van-cell-group inset class="block">
      <van-cell :title="address ? `${address.receiverName}  ${address.receiverPhone}` : '+ 新建收货地址'" is-link @click="pickAddress">
        <template v-if="address" #label>
          {{ address.province }}{{ address.city }}{{ address.district }}{{ address.detail }}
        </template>
        <template #icon>
          <van-icon name="location-o" class="addr-icon" />
        </template>
      </van-cell>
    </van-cell-group>

    <!-- 商品行：V10 按店铺分组，多店铺会拆成多笔订单 -->
    <van-cell-group v-for="grp in shopGroups" :key="grp.merchantId" inset class="block">
      <div class="co-shop-head">
        <van-icon name="shop-o" />
        <span class="co-shop-name van-ellipsis">{{ grp.merchantName || ('店铺 #' + grp.merchantId) }}</span>
        <span class="co-shop-tip">{{ grp.items.length }} 种 · ¥{{ fenToYuan(grp.amount) }}</span>
      </div>
      <div v-for="it in grp.items" :key="it.skuId" class="co-item">
        <van-image :src="it.skuImage" fit="cover" class="co-img" />
        <div class="co-mid">
          <div class="van-ellipsis">{{ it.goodsName }}</div>
          <div class="text-gray co-spec">{{ specText(it.specJson) }}</div>
          <van-tag v-if="!it.buyable" type="danger" plain>{{ it.reason || '不可购买' }}</van-tag>
        </div>
        <div class="co-right">
          <div class="price"><span class="symbol">¥</span>{{ fenToYuan(it.price) }}</div>
          <div class="text-gray">x{{ it.num }}</div>
        </div>
      </div>
    </van-cell-group>
    <div v-if="multiShop" class="split-tip">
      已选 {{ shopGroups.length }} 个店铺的商品，提交后将拆成 {{ shopGroups.length }} 笔订单分别支付；售后也按店铺分别处理。
    </div>

    <!-- 优惠券 / 运费 / 留言 -->
    <van-cell-group inset class="block">
      <van-cell title="优惠券" :is-link="!multiShop" @click="onCouponCell">
        <template #value>
          <span v-if="multiShop" class="text-gray">跨店铺不可用</span>
          <span v-else-if="selectedCoupon" class="price">-¥{{ fenToYuan(selectedCoupon.amount) }}</span>
          <span v-else class="text-gray">{{ preview.usableCoupons.length ? `${preview.usableCoupons.length} 张可用` : '暂无可用' }}</span>
        </template>
      </van-cell>
      <van-cell title="运费" :value="freightAmount > 0 ? `¥${fenToYuan(freightAmount)}` : '免运费'">
        <template v-if="preview.freightTip" #label>
          <span class="freight-tip">{{ preview.freightTip }}</span>
        </template>
      </van-cell>
      <van-field v-model="remark" label="买家留言" placeholder="选填，说点什么" />
      <van-cell :title="`共 ${totalCount} 件  小计`">
        <template #value><span class="price">¥{{ fenToYuan(preview.totalAmount) }}</span></template>
      </van-cell>
      <!-- 促销试算（V15）：仅展示活动优惠明细，实际优惠以下单结算为准 -->
      <template v-if="promoResult">
        <van-cell
          v-for="hit in promoResult.hits"
          :key="hit.promotionId"
          :title="hit.ruleText || hit.promotionName"
        >
          <template #title>
            <van-tag type="danger" style="margin-right:6px">{{ hit.typeText }}</van-tag>
            {{ hit.promotionName }}
          </template>
          <template #value><span class="promo-discount">-¥{{ fenToYuan(hit.amount) }}</span></template>
        </van-cell>
        <van-cell v-if="Number(promoResult.promotionDiscount) > 0" title="促销优惠合计">
          <template #value>
            <span class="promo-discount">-¥{{ fenToYuan(promoResult.promotionDiscount) }}</span>
            <span class="promo-payable">促销后 ¥{{ fenToYuan(promoResult.payableAmount) }}</span>
          </template>
        </van-cell>
      </template>
    </van-cell-group>

    <!-- 提交栏 -->
    <div class="co-bar">
      <div class="co-bar-mid">
        实付：<span class="price co-pay"><span class="symbol">¥</span>{{ fenToYuan(payAmount) }}</span>
      </div>
      <van-button round color="#4a6cf7" :loading="submitting" :disabled="!canSubmit" @click="onSubmit">提交订单</van-button>
    </div>

    <!-- 可用券弹层 -->
    <van-popup v-model:show="showCoupon" position="bottom" round>
      <div class="coupon-sheet">
        <div class="coupon-sheet-title">选择优惠券</div>
        <van-radio-group v-model="selectedRecordId">
          <van-cell
            v-for="c in preview.usableCoupons"
            :key="c.recordId"
            clickable
            @click="selectedRecordId = c.recordId"
          >
            <template #title>
              <span class="price">¥{{ fenToYuan(c.amount) }}</span>
              <span class="coupon-name"> {{ c.name }}</span>
            </template>
            <template #label>
              {{ Number(c.thresholdAmount) > 0 ? `满${fenToYuan(c.thresholdAmount)}可用` : '无门槛' }} · {{ (c.endTime || '').slice(0, 10) }} 到期
            </template>
            <template #right-icon>
              <van-radio :name="c.recordId" checked-color="#4a6cf7" />
            </template>
          </van-cell>
        </van-radio-group>
        <van-cell clickable @click="selectedRecordId = null">
          <template #title>不使用优惠券</template>
          <template #right-icon>
            <van-radio :name="null" checked-color="#4a6cf7" @click.stop="selectedRecordId = null" />
          </template>
        </van-cell>
        <div class="coupon-sheet-btn">
          <van-button block round color="#4a6cf7" @click="showCoupon = false">确 定</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { confirmOrder, submitOrder } from '@/api/order'
import { getAddressList } from '@/api/address'
import { calculatePromotion } from '@/api/promotion'
import { fenToYuan, specText } from '@/utils/format'
import { useCheckoutStore } from '@/stores/checkout'

const route = useRoute()
const router = useRouter()
const checkout = useCheckoutStore()

// 立即购买参数（无则按购物车勾选项下单）
// 雪花 id 超过 2^53，绝不能 Number()：精度丢失后后端 getById 查不到（V3 联调实测）
const skuId = route.query.skuId || null
const num = route.query.num ? Number(route.query.num) : null

const preview = ref({ items: [], totalAmount: 0, discountAmount: 0, payAmount: 0, usableCoupons: [] })
const address = ref(null)
const remark = ref('')
const submitting = ref(false)

// 促销试算（V15）：只做展示，不改金额计算与下单逻辑；失败静默不展示
const promoResult = ref(null)
const loadPromoPreview = async () => {
  const items = preview.value.items
    .filter(i => i.buyable)
    .map(i => ({
      skuId: i.skuId,
      goodsId: i.goodsId || undefined,
      merchantId: i.merchantId || undefined,
      price: Number(i.price),
      quantity: Number(i.num),
    }))
  if (!items.length) return
  try {
    promoResult.value = await calculatePromotion({ items })
  } catch (e) {
    promoResult.value = null
  }
}

// 优惠券选择
const showCoupon = ref(false)
const selectedRecordId = ref(null)
const selectedCoupon = computed(
  () => preview.value.usableCoupons.find(c => String(c.recordId) === String(selectedRecordId.value)) || null,
)
const discount = computed(() => (selectedCoupon.value ? Number(selectedCoupon.value.amount) : 0))
const payAmount = computed(() => Math.max(Number(preview.value.totalAmount) - discount.value, 0) + freightAmount.value)
const freightAmount = computed(() => Number(preview.value.freightAmount || 0))
const totalCount = computed(() => preview.value.items.reduce((s, i) => s + i.num, 0))
const canSubmit = computed(() => preview.value.items.length > 0 && preview.value.items.every(i => i.buyable))

// V10：按店铺分组（保序）。跨店铺下单后端会拒绝用券，前端同步禁用券入口
const shopGroups = computed(() => {
  const groups = []
  const index = {}
  for (const it of preview.value.items) {
    const key = String(it.merchantId || '1')
    if (index[key] === undefined) {
      index[key] = groups.length
      groups.push({ merchantId: key, merchantName: it.merchantName, items: [], amount: 0 })
    }
    const g = groups[index[key]]
    g.items.push(it)
    if (it.buyable) g.amount += Number(it.price) * Number(it.num)
  }
  return groups
})
const multiShop = computed(() => shopGroups.value.length > 1)

const onCouponCell = () => {
  if (multiShop.value) return showToast('跨店铺下单不支持优惠券')
  showCoupon.value = true
}

const pickAddress = () => {
  if (address.value) router.push('/address?select=1')
  else router.push('/address/edit')
}

const onSubmit = async () => {
  if (!address.value) return showToast('请选择收货地址')
  submitting.value = true
  try {
    const res = await submitOrder({
      addressId: address.value.id,
      ...(skuId ? { skuId, num } : {}),
      couponRecordId: selectedCoupon.value ? selectedCoupon.value.recordId : undefined,
      buyerRemark: remark.value || undefined,
    })
    checkout.clear()
    const count = Number((res && res.orderCount) || 1)
    if (count > 1) {
      // 拆单后一笔支付只对应一个订单号：回订单列表逐笔支付
      showToast(`已拆成 ${count} 笔订单，请分别支付`)
      router.replace('/order/list')
      return
    }
    router.replace(`/pay/${res.orderNo}?payAmount=${res.payAmount}`)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const [data, addrList] = await Promise.all([
    confirmOrder(skuId ? { skuId, num } : {}),
    getAddressList(),
  ])
  preview.value = data
  if (multiShop.value) selectedRecordId.value = null
  // 优先用地址选择页回写的地址，其次默认/第一条
  address.value = checkout.selectedAddress || addrList.find(a => a.isDefault === 1) || addrList[0] || null
  // V12：地址确定后带 addressId 重算运费（按地区规则）
  if (address.value && address.value.id) {
    preview.value = await confirmOrder({ ...(skuId ? { skuId, num } : {}), addressId: address.value.id })
  }
  // V15：促销试算展示（不阻塞确认页主流程）
  loadPromoPreview()
})
</script>

<style scoped>
.block {
  margin-top: 8px;
}
.promo-discount {
  color: #ee0a24;
}
.promo-payable {
  margin-left: 8px;
  color: #969799;
  font-size: 11px;
}
.freight-tip {
  color: #ee0a24;
  font-size: 12px;
}

.addr-icon {
  font-size: 22px;
  color: #4a6cf7;
  margin-right: 10px;
  align-self: center;
}

.co-item {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
}

.co-shop-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  border-bottom: 1px solid #f2f3f5;
  font-size: 12px;
  color: #323233;
}

.co-shop-head .van-icon {
  color: #ee9a2b;
}

.co-shop-name {
  font-weight: 600;
  max-width: 55%;
}

.co-shop-tip {
  margin-left: auto;
  color: #969799;
  font-size: 11px;
}

.split-tip {
  margin: 8px 16px 0;
  padding: 9px 12px;
  border-radius: 8px;
  background: #fff8e6;
  color: #8a6d3b;
  font-size: 11px;
  line-height: 1.6;
}

.co-img {
  width: 64px;
  height: 64px;
  border-radius: 6px;
  overflow: hidden;
  flex: none;
}

.co-mid {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}

.co-spec {
  font-size: 12px;
  margin-top: 4px;
}

.co-right {
  text-align: right;
  font-size: 12px;
}

.co-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  max-width: 750px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-top: 1px solid #ebedf0;
  padding: 8px 12px;
}

.co-bar-mid {
  flex: 1;
  text-align: right;
  font-size: 13px;
}

.co-pay {
  font-size: 18px;
}

.coupon-sheet {
  padding: 16px 0 24px;
  max-height: 70vh;
  overflow-y: auto;
}

.coupon-sheet-title {
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 8px;
}

.coupon-name {
  font-size: 13px;
}

.coupon-sheet-btn {
  padding: 16px 16px 0;
}
</style>
