<template>
  <div v-loading="loading" class="page-wrap">
    <template v-if="refund.id">
      <!-- 状态条 -->
      <div class="page-card" style="margin-bottom:16px;display:flex;align-items:center;gap:18px">
        <div style="flex:1">
          <div style="font-size:20px;font-weight:700">{{ refundStatusText(refund.status) }}</div>
          <div class="muted" style="margin-top:6px">{{ statusHint }}</div>
        </div>
        <!-- V15：换货已同意 → 买家寄回 -->
        <el-button v-if="Number(refund.status) === 2 && Number(refund.type) === 3" type="warning" size="small" @click="onShipBack">我已寄回</el-button>
        <!-- V15：待审核且未介入 → 申请平台介入（商家超 48 小时未处理） -->
        <el-button v-if="Number(refund.status) === 1 && Number(refund.platformStatus) === 0" type="warning" plain size="small" @click="onIntervene">申请平台介入</el-button>
        <el-button v-if="Number(refund.status) === 1 || Number(refund.status) === 2" size="small" @click="onClose">撤销申请</el-button>
        <el-button size="small" @click="$router.push('/order/detail/' + refund.orderId)">查看订单</el-button>
      </div>

      <!-- 售后信息 -->
      <div class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">售后信息</span></div>
        <div class="kv-grid">
          <div class="kv"><b>售后单号</b><span>{{ refund.refundNo }}</span></div>
          <div class="kv"><b>售后类型</b><span>{{ refundTypeText(refund.type) }}</span></div>
          <div class="kv"><b>{{ Number(refund.type) === 3 ? '换货信息' : '退款金额' }}</b>
            <span v-if="Number(refund.type) === 3">换入SKU #{{ refund.exchangeSkuId }} × {{ refund.quantity || 1 }}</span>
            <span v-else class="money">¥{{ $price(refund.amount) }}</span>
          </div>
          <!-- V15：换货/平台介入进度 -->
          <div v-if="refund.shipTime" class="kv"><b>寄回时间</b><span>{{ shortTime(refund.shipTime) }}</span></div>
          <div v-if="Number(refund.platformStatus) > 0" class="kv"><b>平台介入</b><span>{{ platText(refund.platformStatus) }}{{ refund.platformRemark ? ' · ' + refund.platformRemark : '' }}</span></div>
          <div class="kv"><b>申请时间</b><span>{{ shortTime(refund.createTime) }}</span></div>
          <div class="kv"><b>审核意见</b><span>{{ refund.auditRemark || '—' }}</span></div>
          <div class="kv"><b>退款时间</b><span>{{ shortTime(refund.refundTime) }}</span></div>
          <div class="kv" style="grid-column:1 / -1"><b>申请原因</b><span>{{ refund.reason }}</span></div>
        </div>

        <!-- 凭证图 -->
        <template v-if="images.length">
          <div style="margin-top:14px" class="muted">凭证图</div>
          <div class="rf-imgs">
            <pc-image v-for="(img, i) in images" :key="i" :src="img" :img-style="{ width: '88px', height: '88px', objectFit: 'cover', borderRadius: '8px', display: 'block', cursor: 'zoom-in' }" @click="preview(i)" />
          </div>
        </template>
      </div>

      <!-- 商品 -->
      <div class="page-card" style="margin-bottom:16px">
        <div class="page-head"><span class="page-title">售后商品</span></div>
        <el-table :data="items" size="small" border>
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
          <el-table-column label="数量" width="80" align="center" prop="quantity" />
        </el-table>
      </div>

      <!-- 时间线 -->
      <div class="page-card">
        <div class="page-head"><span class="page-title">进度时间线</span></div>
        <el-timeline class="timeline-box">
          <el-timeline-item
            v-for="(log, i) in timeline"
            :key="i"
            :timestamp="shortTime(log.createTime)"
            placement="top"
            :type="i === timeline.length - 1 ? 'primary' : ''"
          >
            <div style="font-size:14px">{{ log.content }}</div>
            <div class="muted" style="margin-top:2px">{{ log.operator }} · {{ operatorTypeText(log.operatorType) }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="!timeline.length" description="暂无进度记录" :image-size="80" />
      </div>
    </template>

    <div v-else-if="loaded" class="page-card">
      <el-empty description="售后单不存在或无权查看" />
    </div>
  </div>
</template>

<script>
import { getRefundDetail, closeRefund, shipRefundBack, interveneRefund } from '@/api/refund'
import { refundStatusText, refundTypeText, specText, shortTime } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'

const HINT = {
  1: '商家会尽快审核您的申请，超 48 小时未处理可申请平台介入',
  2: '商家已同意，退款处理中',
  3: '售后被拒绝，可重新发起',
  4: '退款已原路退回（mock 渠道）',
  5: '售后已关闭',
  6: '已寄回，等待商家收货确认',
  7: '商家已收货，等待发出换货',
  8: '换货完成，商家已发出换货商品'
}
const PLAT = { 1: '仲裁中', 2: '已强制退款', 3: '已驳回' }

export default {
  name: 'RefundDetail',
  components: { PcImage },
  data() {
    return {
      refund: {},
      items: [],
      timeline: [],
      loading: false,
      loaded: false
    }
  },
  computed: {
    images() {
      try {
        const parsed = JSON.parse(this.refund.evidenceImages || '[]')
        return Array.isArray(parsed) ? parsed : []
      } catch (e) {
        return []
      }
    },
    statusHint() {
      return HINT[Number(this.refund.status)] || ''
    }
  },
  created() {
    this.load()
  },
  methods: {
    refundStatusText,
    refundTypeText,
    specText,
    shortTime,
    operatorTypeText(type) {
      return { 1: '会员', 2: '商家', 3: '系统' }[Number(type)] || '系统'
    },
    platText(s) {
      return PLAT[Number(s)] || '—'
    },
    /** V15：换货买家寄回 */
    async onShipBack() {
      await this.$confirm('确认已将商品寄出吗？确认后等待商家收货。', '填写寄回', { type: 'warning' })
      await shipRefundBack(this.refund.id)
      this.$message.success('已确认寄回')
      this.load()
    },
    /** V15：申请平台介入（后端校验 48 小时超时） */
    async onIntervene() {
      await this.$confirm('商家超 48 小时未处理时可申请平台仲裁，确定申请吗？', '申请平台介入', { type: 'warning' })
      await interveneRefund(this.refund.id)
      this.$message.success('已提交平台介入申请')
      this.load()
    },
    async load() {
      this.loading = true
      try {
        const res = await getRefundDetail(this.$route.params.id)
        this.refund = (res && res.refund) || {}
        this.items = (res && res.items) || []
        this.timeline = (res && res.timeline) || []
      } finally {
        this.loading = false
        this.loaded = true
      }
    },
    preview(i) {
      this.$alert(`<img src="${this.images[i]}" style="max-width:100%">`, '凭证图', { dangerouslyUseHTMLString: true })
    },
    async onClose() {
      await this.$confirm('撤销后可重新发起，确定撤销该售后申请？', '撤销售后', { type: 'warning' })
      await closeRefund(this.refund.id)
      this.$message.success('已撤销')
      this.load()
    }
  }
}
</script>

<style scoped>
.rf-imgs { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 8px; }
</style>
