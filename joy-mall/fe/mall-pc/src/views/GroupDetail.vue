<template>
  <div class="page-wrap">
    <div class="page-card" v-loading="loading">
      <div v-if="activity" class="gr-detail">
        <pc-image
          :src="thumb(activity.mainImage, 600)"
          :img-style="{ width: '380px', height: '380px', objectFit: 'cover', display: 'block', borderRadius: '8px' }"
        />
        <div class="gr-info">
          <h2 class="gr-title">{{ activity.goodsName }}</h2>
          <div class="gr-price-panel">
            <span class="gr-price"><span class="yen">¥</span>{{ $price(activity.groupPrice) }}</span>
            <span class="gr-origin">¥{{ $price(activity.originPrice) }}</span>
            <el-tag size="small" type="danger" effect="plain">{{ activity.groupSize }}人成团</el-tag>
          </div>
          <div class="gr-meta">拼团有效期：开团后 {{ activity.validHours }} 小时内满员</div>
          <div class="gr-meta">活动截止：{{ shortTime(activity.endTime) }}</div>
          <div class="gr-actions">
            <el-button type="danger" size="medium" @click="openDialog(null)">发起拼团</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else-if="loaded" description="活动不存在或已结束" />

      <!-- 可参团的进行中团 -->
      <div v-if="activity && teams.length" class="team-section">
        <div class="team-title">他们正在拼团（还差 1 人即可参团）</div>
        <div v-for="t in teams" :key="t.teamId" class="team-row">
          <span class="team-leader">团长 #{{ t.leaderId }}</span>
          <span class="team-count">
            已拼 {{ t.currentSize }} / {{ t.groupSize }} 人
            <i>· 剩 {{ shortTime(t.expireTime) }} 前有效</i>
          </span>
          <el-button type="danger" size="mini" round @click="openDialog(t)">参与拼团</el-button>
        </div>
      </div>
    </div>

    <!-- 开团/参团弹窗 -->
    <el-dialog :title="joinTeam ? '参与拼团' : '发起拼团'" v-model="dialogVisible" width="560px">
      <el-form label-width="80px">
        <el-form-item label="收货地址" required>
          <el-radio-group v-model="form.addressId" style="width:100%">
            <div v-for="a in addresses" :key="a.id" class="addr-pick">
              <el-radio :label="a.id">
                <span class="addr-name">{{ a.receiverName }}<span class="phone">{{ a.receiverPhone }}</span></span>
                <div class="addr-text">{{ fullAddress(a) }}</div>
              </el-radio>
            </div>
          </el-radio-group>
          <el-empty v-if="!addresses.length" description="还没有收货地址" :image-size="70" />
          <el-button size="mini" @click="formVisible = true">新增地址</el-button>
        </el-form-item>
        <el-form-item label="买家备注">
          <el-input v-model="form.buyerRemark" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button type="danger" size="small" :loading="submitting" :disabled="!form.addressId" @click="submit">
          {{ joinTeam ? '确认参团' : '确认开团' }}
        </el-button>
      </div></template>
    </el-dialog>

    <address-form-dialog v-model="formVisible" :address="null" @saved="loadAddresses" />
  </div>
</template>

<script>
import { getGroupDetail, openGroup, joinGroupTeam } from '@/api/marketing'
import { getAddressList } from '@/api/address'
import { shortTime, thumb } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'
import AddressFormDialog from '@/components/AddressFormDialog.vue'

export default {
  name: 'GroupDetail',
  components: { PcImage, AddressFormDialog },
  data() {
    return {
      activity: null,
      teams: [],
      loading: false,
      loaded: false,
      dialogVisible: false,
      formVisible: false,
      submitting: false,
      joinTeam: null, // null=开团，否则为参团的 team 对象
      addresses: [],
      form: { addressId: null, buyerRemark: '' }
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
        const data = await getGroupDetail(this.$route.params.id)
        this.activity = data
        this.teams = (data && data.teams) || []
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
        if (def) this.form.addressId = def.id
      } catch (e) {
        this.addresses = []
      }
    },
    openDialog(team) {
      this.joinTeam = team || null
      this.form.addressId = null
      this.form.buyerRemark = ''
      this.loadAddresses()
      this.dialogVisible = true
    },
    async submit() {
      this.submitting = true
      try {
        const payload = {
          addressId: this.form.addressId,
          buyerRemark: this.form.buyerRemark || undefined
        }
        const data = this.joinTeam
          ? await joinGroupTeam(this.joinTeam.teamId, payload)
          : await openGroup(this.$route.params.id, payload)
        this.dialogVisible = false
        const stateText = data.teamStatus === 1 ? '已成团' : `拼团中（${data.currentSize}/${data.groupSize} 人）`
        this.$confirm(`下单成功（${stateText}），订单金额 ¥${this.$price(data.payAmount)}，是否立即去支付？`, '拼团下单成功', {
          confirmButtonText: '去支付',
          cancelButtonText: '稍后处理',
          type: 'success'
        }).then(() => {
          this.$router.push(`/pay/${data.orderNo}`)
        }).catch(() => {})
        this.load()
      } catch (e) {
        // request 封装已提示
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.gr-detail {
  display: flex;
  gap: 32px;
}
.gr-info {
  flex: 1;
  min-width: 0;
}
.gr-title {
  margin: 0 0 16px;
  font-size: 20px;
  color: #303133;
}
.gr-price-panel {
  background: #fff3f2;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.gr-price {
  color: #e1251b;
  font-size: 28px;
  font-weight: 700;
}
.gr-price .yen {
  font-size: 15px;
}
.gr-origin {
  color: #c0c4cc;
  text-decoration: line-through;
}
.gr-meta {
  margin-top: 14px;
  font-size: 13px;
  color: #606266;
}
.gr-actions {
  margin-top: 24px;
}
.team-section {
  margin-top: 32px;
  border-top: 1px solid #f0f0f0;
  padding-top: 20px;
}
.team-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}
.team-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f0f0;
}
.team-row:last-child {
  border-bottom: none;
}
.team-leader {
  font-size: 13px;
  color: #303133;
  font-weight: 600;
  width: 90px;
}
.team-count {
  flex: 1;
  font-size: 13px;
  color: #606266;
}
.team-count i {
  font-style: normal;
  color: #909399;
  font-size: 12px;
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
