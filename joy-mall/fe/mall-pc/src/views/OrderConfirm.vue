<template>
  <div class="confirm-grid">
    <div>
      <!-- 收货地址 -->
      <div class="page-card" style="margin-bottom:16px">
        <div class="page-head">
          <span class="page-title">收货地址</span>
          <a class="muted" @click="openPicker">{{ address ? '更换地址' : '' }}</a>
        </div>
        <div v-if="address" class="addr-card">
          <i class="el-icon-location-outline" style="font-size:22px;color:#e1251b" />
          <div class="addr-card-main">
            <div class="addr-card-name">
              {{ address.receiverName }}<span class="phone">{{ address.receiverPhone }}</span>
              <el-tag v-if="Number(address.isDefault) === 1" size="mini" type="danger" effect="plain" style="margin-left:8px">默认</el-tag>
            </div>
            <div class="addr-card-text">{{ fullAddress(address) }}</div>
          </div>
          <el-button type="text" @click="openPicker">更换</el-button>
        </div>
        <div v-else class="addr-card empty" @click="onAddressEmptyClick">
          <i class="el-icon-plus" />
          <span>还没有收货地址，点击新建一个</span>
        </div>
      </div>

      <!-- 商品清单：V10 按店铺分组，多店铺会拆成多笔订单 -->
      <div class="page-card">
        <div class="page-head">
          <span class="page-title">商品清单</span>
          <span class="muted">共 {{ totalCount }} 件{{ multiShop ? ` · ${shopGroups.length} 个店铺` : '' }}</span>
        </div>
        <div v-for="grp in shopGroups" :key="grp.merchantId" class="co-shop-group">
          <div class="co-shop-head">
            <i class="el-icon-s-shop" />
            <span class="co-shop-name">{{ grp.merchantName || ('店铺 #' + grp.merchantId) }}</span>
            <span class="co-shop-tip">{{ grp.items.length }} 种 · 小计 ¥{{ $price(grp.amount) }}</span>
          </div>
          <div class="co-shop-body">
            <div v-for="it in grp.items" :key="it.skuId" class="co-row">
              <pc-image :src="it.skuImage" :img-style="{ width: '64px', height: '64px', objectFit: 'cover', borderRadius: '6px', display: 'block' }" />
              <div class="co-row-mid">
                <div class="co-row-name">{{ it.goodsName }}</div>
                <div class="co-row-spec">{{ specText(it.specJson) || '默认规格' }}</div>
                <el-tag v-if="!it.buyable" type="danger" size="mini" effect="plain" style="margin-top:6px">
                  {{ it.reason || '不可购买' }}
                </el-tag>
              </div>
              <div class="co-row-right">
                <div class="money">¥{{ $price(it.price) }}</div>
                <div style="margin-top:6px">x{{ it.num }}</div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="multiShop" class="split-tip">
          你选了 {{ shopGroups.length }} 个店铺的商品，提交后将按店铺拆成 {{ shopGroups.length }} 笔订单，需分别支付；
          同一笔订单只含一个店铺的商品，售后也按店铺分别处理{{ selectedCoupon ? '。跨店铺不能使用优惠券，请先取消已选优惠券' : '' }}。
        </div>
        <el-empty v-if="!preview.items.length" description="没有可结算的商品" :image-size="90" />
      </div>

      <!-- 优惠券与留言 -->
      <div class="page-card" style="margin-top:16px">
        <div class="page-head"><span class="page-title">优惠与留言</span></div>
        <el-form label-width="88px" size="small">
          <el-form-item label="优惠券">
            <el-select
              v-model="selectedRecordId"
              placeholder="选择优惠券"
              style="width:320px"
              clearable
              :disabled="multiShop"
              @change="onCouponChange"
            >
              <el-option
                v-for="c in preview.usableCoupons"
                :key="c.recordId"
                :label="`${c.name} · 减 ¥${$price(c.amount)}`"
                :value="c.recordId"
              />
            </el-select>
            <span class="muted" style="margin-left:12px">
              <template v-if="multiShop">跨店铺下单不支持优惠券</template>
              <template v-else>{{ preview.usableCoupons.length ? preview.usableCoupons.length + ' 张可用' : '暂无可用券' }}</template>
              <router-link v-if="!multiShop" to="/coupon/center" style="color:#e1251b;margin-left:6px">去领券</router-link>
            </span>
          </el-form-item>
          <el-form-item label="买家留言">
            <el-input v-model="remark" maxlength="100" show-word-limit placeholder="选填，例如：工作日送达" style="width:520px" />
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- 金额汇总 -->
    <div class="page-card confirm-side">
      <div class="page-head"><span class="page-title">结算</span></div>
      <div class="sum-row"><span>商品总额</span><span>¥{{ $price(preview.totalAmount) }}</span></div>
      <!-- 促销试算（V15）：仅展示活动优惠明细，实际优惠以下单结算为准 -->
      <div v-for="hit in promoHits" :key="hit.promotionId" class="sum-row promo-row">
        <span><el-tag type="danger" size="mini">{{ hit.typeText }}</el-tag> {{ hit.promotionName }}</span>
        <span class="money">-¥{{ $price(hit.amount) }}</span>
      </div>
      <div v-if="promoDiscount > 0" class="sum-row promo-row">
        <span>促销优惠合计</span>
        <span class="money">-¥{{ $price(promoDiscount) }}</span>
      </div>
      <div class="sum-row"><span>优惠券</span><span class="money">-¥{{ $price(discount) }}</span></div>
      <div class="sum-row">
        <span>运费</span>
        <span v-if="freightAmount > 0" class="money">+¥{{ $price(freightAmount) }}</span>
        <span v-else class="free">免运费</span>
      </div>
      <div v-if="preview.freightTip" class="freight-tip">{{ preview.freightTip }}</div>
      <div class="sum-row pay">
        <span>应付金额</span>
        <span class="money-lg">¥{{ $price(payAmount) }}</span>
      </div>
      <el-button type="danger" style="width:100%;height:44px;margin-top:18px" :loading="submitting" :disabled="!canSubmit" @click="onSubmit">
        提交订单
      </el-button>
      <div v-if="!canSubmit" class="muted" style="text-align:center;margin-top:10px">请先选择地址并确保商品可购买</div>
    </div>

    <!-- 地址选择 -->
    <el-dialog title="选择收货地址" v-model="pickerVisible" width="560px">
      <el-radio-group v-model="pickedId" style="width:100%">
        <div v-for="a in addresses" :key="a.id" class="addr-pick">
          <el-radio :label="a.id">
            <span class="addr-card-name">{{ a.receiverName }}<span class="phone">{{ a.receiverPhone }}</span></span>
            <el-tag v-if="Number(a.isDefault) === 1" size="mini" type="danger" effect="plain" style="margin-left:8px">默认</el-tag>
            <div class="addr-card-text">{{ fullAddress(a) }}</div>
          </el-radio>
        </div>
      </el-radio-group>
      <el-empty v-if="!addresses.length" description="还没有收货地址" :image-size="80" />
      <template #footer><div>
        <el-button size="small" @click="openForm()">新增地址</el-button>
        <el-button size="small" @click="pickerVisible = false">取消</el-button>
        <el-button size="small" type="danger" :disabled="!pickedId" @click="applyPicked">确定</el-button>
      </div></template>
    </el-dialog>

    <address-form-dialog v-model="formVisible" :address="editing" @saved="loadAddresses" />
  </div>
</template>

<script>
import { confirmOrder, submitOrder } from '@/api/order'
import { getAddressList } from '@/api/address'
import { calculatePromotion } from '@/api/promotion'
import { specText } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'
import AddressFormDialog from '@/components/AddressFormDialog.vue'

export default {
  name: 'OrderConfirm',
  components: { PcImage, AddressFormDialog },
  data() {
    return {
      // 立即购买：skuId 是雪花字符串，只做存在性判断，绝不 Number()
      skuId: this.$route.query.skuId || null,
      num: this.$route.query.num ? Number(this.$route.query.num) : null,
      preview: { items: [], totalAmount: 0, discountAmount: 0, payAmount: 0, usableCoupons: [] },
      addresses: [],
      address: null,
      pickedId: null,
      pickerVisible: false,
      formVisible: false,
      editing: null,
      remark: '',
      selectedRecordId: null,
      submitting: false,
      // V15 促销试算（仅展示，不改金额计算与下单逻辑）
      promoResult: null
    }
  },
  computed: {
    totalCount() {
      return this.preview.items.reduce((sum, i) => sum + i.num, 0)
    },
    /** V10：按店铺分组（保序，后端 items 已按购物车顺序返回店铺 ID） */
    shopGroups() {
      const groups = []
      const index = {}
      for (const it of this.preview.items) {
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
    },
    multiShop() {
      return this.shopGroups.length > 1
    },
    selectedCoupon() {
      return this.preview.usableCoupons.find(c => String(c.recordId) === String(this.selectedRecordId)) || null
    },
    discount() {
      return this.selectedCoupon ? Number(this.selectedCoupon.amount) : 0
    },
    payAmount() {
      // V12：应付 = 商品额 − 优惠 + 运费（运费不参与优惠）
      return Math.max(Number(this.preview.totalAmount) - this.discount, 0) + this.freightAmount
    },
    freightAmount() {
      return Number(this.preview.freightAmount || 0)
    },
    canSubmit() {
      return !!this.address && this.preview.items.length > 0 && this.preview.items.every(i => i.buyable)
    },
    promoHits() {
      return (this.promoResult && this.promoResult.hits) || []
    },
    promoDiscount() {
      return this.promoResult ? Number(this.promoResult.promotionDiscount || 0) : 0
    }
  },
  created() {
    this.loadPreview()
    this.loadAddresses().then(() => {})
  },
  methods: {
    specText,
    fullAddress(a) {
      return [a.province, a.city, a.district, a.detail].filter(Boolean).join('')
    },
    async loadPreview() {
      const payload = this.skuId ? { skuId: this.skuId, num: this.num } : {}
      // V12：带上收货地址，运费按地区规则匹配（未选地址时按模板默认规则）
      if (this.address && this.address.id) {
        payload.addressId = this.address.id
      }
      this.preview = await confirmOrder(payload)
      // 跨店铺下单后端会直接拒绝用券，前端先把已选券清掉，避免用户白填一遍
      if (this.multiShop) this.selectedRecordId = null
      // V15：促销试算展示（失败静默，不影响确认页主流程）
      const items = this.preview.items
        .filter(i => i.buyable)
        .map(i => ({
          skuId: i.skuId,
          goodsId: i.goodsId || undefined,
          merchantId: i.merchantId || undefined,
          price: Number(i.price),
          quantity: Number(i.num)
        }))
      if (items.length) {
        calculatePromotion({ items })
          .then(res => { this.promoResult = res })
          .catch(() => { this.promoResult = null })
      }
    },
    async loadAddresses() {
      this.addresses = (await getAddressList()) || []
      if (!this.addresses.length) {
        this.address = null
        return
      }
      // 保持当前选中；否则用默认地址，其次第一条
      const keep = this.address && this.addresses.find(a => a.id === this.address.id)
      this.address = keep || this.addresses.find(a => Number(a.isDefault) === 1) || this.addresses[0]
    },
    openPicker() {
      if (!this.addresses.length) {
        this.openForm()
        return
      }
      this.pickedId = this.address ? this.address.id : null
      this.pickerVisible = true
    },
    onAddressEmptyClick() {
      this.openForm()
    },
    openForm(address) {
      this.editing = address || null
      this.formVisible = true
      this.pickerVisible = false
    },
    applyPicked() {
      this.address = this.addresses.find(a => a.id === this.pickedId) || this.address
      this.pickerVisible = false
    },
    onCouponChange() {
      // 切换券后金额由 computed 重算；后端会在提交时重新校验门槛
    },
    async onSubmit() {
      if (!this.canSubmit) return
      this.submitting = true
      try {
        const payload = {
          addressId: this.address.id,
          couponRecordId: this.selectedCoupon ? this.selectedCoupon.recordId : undefined,
          buyerRemark: this.remark || undefined
        }
        if (this.skuId) {
          payload.skuId = this.skuId
          payload.num = this.num
        }
        const res = await submitOrder(payload)
        const count = Number((res && res.orderCount) || 1)
        if (count > 1) {
          // V10 拆单：一笔支付只能对应一个订单号，多店铺订单引导到列表逐笔支付
          this.$message.success(`下单成功，已按店铺拆成 ${count} 笔订单，请分别支付`)
          this.$router.replace('/order/list')
          return
        }
        this.$message.success('下单成功，请完成支付')
        this.$router.replace({ path: '/pay/' + res.orderNo, query: { payAmount: res.payAmount } })
      } finally {
        this.submitting = false
      }
    }
  },
  watch: {
    // V12：换地址后运费可能变化（地区规则），重新试算
    'address.id'() {
      this.loadPreview()
    }
  }
}
</script>

<style scoped>
.addr-pick { padding: 8px 0; border-bottom: 1px dashed #f0f0f0; }
.addr-pick .addr-card-text { padding-left: 24px; }
.freight-tip { font-size: 12px; color: #ff6b35; margin: -4px 0 8px; }
.free { color: #52c41a; }
.promo-row { color: #e1251b; }
.promo-row .el-tag { margin-right: 4px; }
</style>
