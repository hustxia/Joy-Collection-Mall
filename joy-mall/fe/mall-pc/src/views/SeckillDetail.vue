<template>
  <div class="page-wrap">
    <div class="page-card" v-loading="loading">
      <div v-if="activity" class="sk-detail">
        <pc-image
          :src="thumb(activity.mainImage, 600)"
          :img-style="{ width: '380px', height: '380px', objectFit: 'cover', display: 'block', borderRadius: '8px' }"
        />
        <div class="sk-info">
          <h2 class="sk-title">{{ activity.goodsName }}</h2>
          <div class="sk-price-panel">
            <span class="sk-price"><span class="yen">¥</span>{{ $price(activity.seckillPrice) }}</span>
            <span class="sk-origin">¥{{ $price(activity.originPrice) }}</span>
            <span class="sk-limit">限购 {{ activity.perLimit }} 件 / 人</span>
          </div>
          <div class="sk-meta">
            <span>活动时间：{{ shortTime(activity.startTime) }} ~ {{ shortTime(activity.endTime) }}</span>
            <span :class="['sk-state', activity.onSale ? 'on' : 'off']">{{ activity.onSale ? '抢购中' : '未开始/已结束' }}</span>
          </div>
          <div class="sk-meta">剩余库存：{{ activity.availableStock }} / {{ activity.totalStock }}</div>
          <div class="sk-meta">我已抢：{{ activity.myCount }} 件</div>
          <div class="sk-meta">距结束：<b class="sk-cd">{{ countdownText }}</b></div>
          <div class="sk-actions">
            <el-button type="danger" size="medium" :disabled="!activity.onSale || soldOut" @click="openBuy">
              {{ soldOut ? '已抢光' : '立即抢购' }}
            </el-button>
          </div>
        </div>
      </div>
      <el-empty v-else-if="loaded" description="活动不存在或已结束" />
    </div>

    <!-- 抢购弹窗：选地址 + 备注 -->
    <el-dialog title="确认抢购" v-model="buyVisible" width="560px">
      <el-form label-width="80px">
        <el-form-item label="收货地址" required>
          <el-radio-group v-model="buy.addressId" style="width:100%">
            <div v-for="a in addresses" :key="a.id" class="addr-pick">
              <el-radio :label="a.id">
                <span class="addr-name">{{ a.receiverName }}<span class="phone">{{ a.receiverPhone }}</span></span>
                <div class="addr-text">{{ fullAddress(a) }}</div>
              </el-radio>
            </div>
          </el-radio-group>
          <el-empty v-if="!addresses.length" description="还没有收货地址" :image-size="70" />
          <el-button size="mini" @click="openAddressForm">新增地址</el-button>
        </el-form-item>
        <el-form-item label="买家备注">
          <el-input v-model="buy.buyerRemark" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button size="small" @click="buyVisible = false">取消</el-button>
        <el-button type="danger" size="small" :loading="buying" :disabled="!buy.addressId" @click="doBuy">确认抢购</el-button>
      </div></template>
    </el-dialog>

    <address-form-dialog v-model="formVisible" :address="null" @saved="loadAddresses" />
  </div>
</template>

<script>
import { getSeckillDetail, seckillBuy } from '@/api/marketing'
import { getAddressList } from '@/api/address'
import { shortTime, thumb } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'
import AddressFormDialog from '@/components/AddressFormDialog.vue'
import countdown from '@/utils/countdown'

export default {
  name: 'SeckillDetail',
  components: { PcImage, AddressFormDialog },
  mixins: [countdown],
  data() {
    return {
      activity: null,
      loading: false,
      loaded: false,
      buyVisible: false,
      buying: false,
      addresses: [],
      formVisible: false,
      buy: { addressId: null, buyerRemark: '' }
    }
  },
  computed: {
    soldOut() {
      const a = this.activity
      return a && Number(a.availableStock) <= 0
    }
  },
  created() {
    this.load()
  },
  methods: {
    thumb,
    shortTime,
    fullAddress(a) {
      return [a.province, a.city, a.district, a.detail].filter(Boolean).join('')
    },
    async load() {
      this.loading = true
      try {
        this.activity = await getSeckillDetail(this.$route.params.id)
      } catch (e) {
        this.activity = null
      } finally {
        this.loading = false
        this.loaded = true
      }
    },
    async loadAddresses() {
      try {
        this.addresses = (await getAddressList()) || []
        const def = this.addresses.find(a => Number(a.isDefault) === 1)
        if (def) this.buy.addressId = def.id
      } catch (e) {
        this.addresses = []
      }
    },
    openBuy() {
      this.buy.addressId = null
      this.buy.buyerRemark = ''
      this.loadAddresses()
      this.buyVisible = true
    },
    openAddressForm() {
      this.formVisible = true
    },
    async doBuy() {
      this.buying = true
      try {
        const data = await seckillBuy(this.$route.params.id, {
          addressId: this.buy.addressId,
          buyerRemark: this.buy.buyerRemark || undefined
        })
        this.buyVisible = false
        this.load()
        this.$confirm(`抢购成功！订单金额 ¥${this.$price(data.payAmount)}，是否立即去支付？`, '抢购成功', {
          confirmButtonText: '去支付',
          cancelButtonText: '稍后处理',
          type: 'success'
        }).then(() => {
          this.$router.push(`/pay/${data.orderNo}`)
        }).catch(() => {})
      } catch (e) {
        // 业务错误 request 封装已 $message.error
      } finally {
        this.buying = false
      }
    }
  }
}
</script>

<style scoped>
.sk-detail {
  display: flex;
  gap: 32px;
}
.sk-info {
  flex: 1;
  min-width: 0;
}
.sk-title {
  margin: 0 0 16px;
  font-size: 20px;
  color: #303133;
}
.sk-price-panel {
  background: #fff3f2;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.sk-price {
  color: #e1251b;
  font-size: 28px;
  font-weight: 700;
}
.sk-price .yen {
  font-size: 15px;
}
.sk-origin {
  color: #c0c4cc;
  text-decoration: line-through;
}
.sk-limit {
  font-size: 12px;
  color: #909399;
}
.sk-meta {
  margin-top: 14px;
  font-size: 13px;
  color: #606266;
  display: flex;
  gap: 12px;
  align-items: center;
}
.sk-state.on {
  color: #e1251b;
  font-weight: 600;
}
.sk-state.off {
  color: #909399;
}
.sk-cd {
  color: #e1251b;
  font-family: monospace;
  font-size: 15px;
}
.sk-actions {
  margin-top: 24px;
}
.addr-pick {
  padding: 6px 0;
  border-bottom: 1px dashed #f0f0f0;
}
.addr-name {
  font-weight: 600;
}
.addr-name .phone {
  margin-left: 10px;
  color: #909399;
  font-weight: 400;
}
.addr-text {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>
