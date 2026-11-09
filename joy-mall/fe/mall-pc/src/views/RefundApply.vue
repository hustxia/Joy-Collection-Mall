<template>
  <div v-loading="loading" class="page-wrap">
    <template v-if="loaded && order.id">
      <!-- 状态横幅 -->
      <div class="rf-banner">
        <div class="rf-banner-t">{{ $options.filters ? TYPE_NAME[type] : TYPE_NAME[type] }}</div>
        <div class="rf-banner-s">{{ type === 3 ? '换货需商家审核，通过后请寄回商品' : '退款金额将原路退回（mock 渠道），提交后等待商家审核' }}</div>
      </div>

      <!-- 商品与可退金额 -->
      <div class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">售后商品</span></div>
        <el-table :data="items" size="small" border highlight-current-row @current-change="onItemPick">
          <el-table-column v-if="needItem" width="46" align="center">
            <template #default="{ row }">
              <el-radio :value="selectedItemId" :label="row.id"><span /></el-radio>
            </template>
          </el-table-column>
          <el-table-column label="商品" min-width="320">
            <template #default="{ row }">
              <div class="cart-prod">
                <pc-image :src="row.skuImage" :img-style="{ width: '48px', height: '48px', objectFit: 'cover', borderRadius: '6px', display: 'block' }" />
                <div>
                  <div class="cart-prod-name">{{ row.goodsName }}</div>
                  <div class="cart-prod-spec">{{ specText(row.specJson) || '默认规格' }} x{{ row.quantity }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120" align="right">
            <template #default="{ row }">¥{{ $price(row.price) }}</template>
          </el-table-column>
          <el-table-column label="小计" width="120" align="right">
            <template #default="{ row }">¥{{ $price(row.totalAmount || row.price * row.quantity) }}</template>
          </el-table-column>
        </el-table>
        <div v-if="type !== 3" class="sum-row pay" style="margin-top:8px">
          <span>可退金额（实付）</span>
          <span class="money-lg" style="font-size:20px">¥{{ $price(order.payAmount) }}</span>
        </div>
      </div>

      <!-- 售后类型 -->
      <div class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">售后类型</span></div>
        <el-radio-group v-model="type">
          <el-radio :label="1" border>仅退款<span class="rf-radio-sub">未收到货或不需退回</span></el-radio>
          <el-radio :label="2" border>退货退款<span class="rf-radio-sub">已收到货，退回后退款</span></el-radio>
          <el-radio :label="3" border>换货<span class="rf-radio-sub">同商品换规格/型号</span></el-radio>
          <el-radio :label="4" border>部分退款<span class="rf-radio-sub">按订单项选金额</span></el-radio>
        </el-radio-group>
      </div>

      <!-- V15 换货：选换入 SKU -->
      <div v-if="type === 3" class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">换入商品规格</span><span class="muted">（必选）</span></div>
        <template v-if="selectedItem">
          <el-table :data="skus" size="small" border highlight-current-row @current-change="onSkuPick">
            <el-table-column width="46" align="center">
              <template #default="{ row }">
                <el-radio :value="exchangeSkuId" :label="row.id"><span /></el-radio>
              </template>
            </el-table-column>
            <el-table-column label="规格" min-width="200">
              <template #default="{ row }">{{ skuSpecText(row.specJson) }}</template>
            </el-table-column>
            <el-table-column label="价格" width="110" align="right">
              <template #default="{ row }">¥{{ $price(row.price) }}</template>
            </el-table-column>
            <el-table-column label="库存" width="90" align="center" prop="stock" />
          </el-table>
          <div style="margin-top:10px;display:flex;align-items:center;gap:10px">
            <span class="muted">换货数量（≤ {{ selectedItem.quantity }}）</span>
            <el-input-number v-model="quantity" :min="1" :max="selectedItem.quantity" size="small" />
          </div>
        </template>
        <el-empty v-else description="请先在上方选择要换货的订单项" :image-size="60" />
      </div>

      <!-- V15 部分退款：按订单项填金额 -->
      <div v-if="type === 4" class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">退款金额</span></div>
        <template v-if="selectedItem">
          <el-input-number
            v-model="amountYuan"
            :min="0.01"
            :max="(selectedItem.totalAmount || selectedItem.price * selectedItem.quantity) / 100"
            :precision="2"
            :step="1"
            size="small"
          />
          <span class="muted" style="margin-left:10px">订单项可退上限 ¥{{ $price(selectedItem.totalAmount || selectedItem.price * selectedItem.quantity) }}；一单可多次部分退款，累计不超过实付 ¥{{ $price(order.payAmount) }}</span>
        </template>
        <el-empty v-else description="请先在上方选择部分退款的订单项" :image-size="60" />
      </div>

      <!-- 原因与凭证 -->
      <div class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">申请原因与凭证</span></div>
        <el-input
          v-model="reason"
          type="textarea"
          :rows="4"
          maxlength="200"
          show-word-limit
          placeholder="请详细描述您遇到的问题（必填）"
        />
        <div style="margin-top:14px">
          <div class="muted" style="margin-bottom:8px">上传凭证（选填，最多 6 张，单张 ≤5MB）</div>
          <el-upload
            action="#"
            :file-list="fileList"
            :http-request="doUpload"
            :on-remove="onRemove"
            :before-upload="beforeUpload"
            list-type="picture-card"
            accept="image/jpeg,image/png,image/webp"
            :limit="6"
          >
            <i class="el-icon-plus" />
          </el-upload>
        </div>
      </div>

      <!-- 说明与提交 -->
      <div class="page-card rf-note-card">
        <div class="muted rf-note">
          · 换货/部分退款需选择具体订单项；<br>
          · 换货审核通过后请寄回商品，商家收货确认后发出换货；<br>
          · 部分退款一单可多次申请，累计不超过实付金额。
        </div>
        <div style="display:flex;align-items:center;justify-content:space-between;margin-top:16px">
          <span v-if="type === 4 && amountYuan">退款金额 <span class="money-lg">¥{{ amountYuan.toFixed(2) }}</span></span>
          <span v-else-if="type === 3">换货数量 ×{{ quantity }}</span>
          <span v-else>可退金额 <span class="money-lg">¥{{ $price(order.payAmount) }}</span></span>
          <el-button type="danger" :loading="submitting" @click="onSubmit">提交申请</el-button>
        </div>
      </div>
    </template>

    <div v-else-if="loaded" class="page-card">
      <el-empty description="订单不存在或无权查看" />
    </div>
  </div>
</template>

<script>
import { getOrderDetail } from '@/api/order'
import { getGoodsDetail } from '@/api/shop'
import { applyRefund, applyExchange, applyPartialRefund, uploadEvidence } from '@/api/refund'
import { specText } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'

const TYPE_NAME = { 1: '仅退款', 2: '退货退款', 3: '换货', 4: '部分退款' }

export default {
  name: 'RefundApply',
  components: { PcImage },
  data() {
    return {
      TYPE_NAME,
      order: {},
      items: [],
      loaded: false,
      loading: false,
      type: Number(this.$route.query.mode) === 3 ? 3 : Number(this.$route.query.mode) === 4 ? 4 : 1,
      reason: '',
      fileList: [], // { name, url }
      submitting: false,
      // V15：换货/部分退款的订单项选择
      selectedItemId: this.$route.query.orderItemId ? Number(this.$route.query.orderItemId) : null,
      // V15 换货：换入 SKU
      skus: [],
      exchangeSkuId: null,
      quantity: 1,
      // V15 部分退款：金额（元）
      amountYuan: 1
    }
  },
  computed: {
    needItem() {
      return this.type === 3 || this.type === 4
    },
    selectedItem() {
      return this.items.find(i => i.id === this.selectedItemId) || null
    }
  },
  watch: {
    type(t) {
      if (t === 3 && this.selectedItemId) this.loadSkus()
    }
  },
  created() {
    const orderId = this.$route.query.orderId
    if (!orderId) {
      this.$message.warning('缺少订单参数')
      this.$router.replace('/order/list')
      return
    }
    this.orderId = orderId
    this.load()
  },
  methods: {
    specText,
    async load() {
      this.loading = true
      try {
        const res = await getOrderDetail(this.orderId)
        this.order = (res && res.order) || {}
        this.items = (res && res.items) || []
        if (this.selectedItemId && !this.items.some(i => i.id === this.selectedItemId)) {
          this.selectedItemId = null
        }
        if (this.type === 3 && this.selectedItemId) this.loadSkus()
      } finally {
        this.loading = false
        this.loaded = true
      }
    },
    onItemPick(row) {
      if (!this.needItem || !row) return
      this.selectedItemId = row.id
      this.exchangeSkuId = null
      this.quantity = 1
    },
    onSkuPick(row) {
      if (row) this.exchangeSkuId = row.id
    },
    skuSpecText(specJson) {
      try {
        const spec = JSON.parse(specJson || '[]')
        return spec.map(s => `${s.name}:${s.value}`).join(' ') || '默认规格'
      } catch (e) {
        return '默认规格'
      }
    },
    async loadSkus() {
      if (!this.selectedItem) return
      try {
        const goods = await getGoodsDetail(this.selectedItem.goodsId)
        this.skus = (goods && (goods.skus || goods.skuList)) || []
      } catch (e) {
        this.skus = []
      }
    },
    beforeUpload(file) {
      const ok = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
      if (!ok) {
        this.$message.error('仅支持 jpg/png/webp')
        return false
      }
      if (file.size > 5 * 1024 * 1024) {
        this.$message.error('单张不超过 5MB')
        return false
      }
      return true
    },
    async doUpload({ file, onSuccess, onError }) {
      try {
        const fd = new FormData()
        fd.append('file', file)
        const res = await uploadEvidence(fd)
        this.fileList.push({ name: file.name, url: res.url })
        onSuccess(res)
      } catch (e) {
        onError(e)
      }
    },
    onRemove(file) {
      const idx = this.fileList.findIndex(f => f.url === file.url)
      if (idx >= 0) this.fileList.splice(idx, 1)
    },
    async onSubmit() {
      if (!this.reason.trim()) {
        this.$message.warning('请填写售后原因')
        return
      }
      if (this.needItem && !this.selectedItemId) {
        this.$message.warning('请选择订单项')
        return
      }
      const urls = this.fileList.map(f => f.url).filter(Boolean)
      const common = {
        orderId: this.orderId,
        orderItemId: this.selectedItemId,
        reason: this.reason.trim(),
        evidenceImages: urls
      }
      this.submitting = true
      try {
        if (this.type === 3) {
          if (!this.exchangeSkuId) {
            this.$message.warning('请选择换入商品规格')
            return
          }
          await applyExchange({ ...common, exchangeSkuId: this.exchangeSkuId, quantity: this.quantity })
        } else if (this.type === 4) {
          const fen = Math.round(Number(this.amountYuan) * 100)
          if (!fen || fen <= 0) {
            this.$message.warning('请填写退款金额')
            return
          }
          await applyPartialRefund({ ...common, amount: fen })
        } else {
          await applyRefund({
            orderId: this.orderId,
            type: this.type,
            reason: this.reason.trim(),
            evidenceImages: urls
          })
        }
        this.$message.success('已提交申请')
        this.$router.replace('/refund/list')
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.rf-banner { background: linear-gradient(135deg, #e1251b, #ff6f5e); color: #fff; border-radius: 10px; padding: 18px 24px; margin-bottom: 16px; }
.rf-banner-t { font-size: 20px; font-weight: 700; }
.rf-banner-s { font-size: 13px; opacity: 0.9; margin-top: 6px; }
.rf-radio-sub { font-size: 12px; color: #909399; margin-left: 6px; }
.rf-note-card { background: #fff8f5; }
.rf-note { line-height: 1.9; }
</style>
