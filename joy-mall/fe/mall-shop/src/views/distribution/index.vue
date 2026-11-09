<template>
  <div class="page">
    <van-nav-bar title="分销中心" left-arrow fixed placeholder @click-left="router.back()" />

    <van-loading v-if="loading" class="dt-loading" vertical>加载中...</van-loading>

    <template v-else>
      <!-- 未成为分销员：申请入口 -->
      <div v-if="!info" class="dt-apply">
        <van-icon name="medal-o" size="48" color="#4a6cf7" />
        <div class="dt-apply-title">成为分销员</div>
        <div class="dt-apply-desc">分享商品给好友，好友下单后即可获得佣金奖励；申请通过后系统将分配专属邀请码。</div>
        <van-button round block type="primary" color="#4a6cf7" :loading="applying" @click="onApply">
          立即申请成为分销员
        </van-button>
      </div>

      <!-- 已申请 -->
      <template v-else>
        <!-- 状态卡 -->
        <div class="dt-head">
          <div class="dt-row1">
            <span class="dt-code">邀请码：{{ info.inviteCode || '-' }}</span>
            <van-tag :type="statusTag(info.status)">{{ statusText(info.status) }}</van-tag>
          </div>
          <div v-if="info.status === 3 && info.rejectReason" class="dt-reject">驳回原因：{{ info.rejectReason }}</div>
          <div v-if="info.status === 1" class="dt-stats">
            <div class="dt-stat">
              <div class="dt-stat-num">￥{{ fenToYuan(info.totalCommission) }}</div>
              <div class="dt-stat-label">累计佣金</div>
            </div>
            <div class="dt-stat">
              <div class="dt-stat-num">￥{{ fenToYuan(info.settled) }}</div>
              <div class="dt-stat-label">已结算</div>
            </div>
            <div class="dt-stat">
              <div class="dt-stat-num dt-withdrawable">￥{{ fenToYuan(info.withdrawable) }}</div>
              <div class="dt-stat-label">可提现</div>
            </div>
          </div>
        </div>

        <van-tabs v-if="info.status === 1" v-model:active="tab" class="dt-tabs">
          <!-- 团队列表 -->
          <van-tab title="我的团队">
            <div class="dt-team" v-for="t in team" :key="t.id">
              <van-icon name="friends-o" size="24" color="#4a6cf7" />
              <div class="dt-team-info">
                <div>会员 #{{ t.inviteeId }} <van-tag plain>{{ t.level }}级</van-tag> <van-tag plain type="primary">{{ t.channel }}</van-tag></div>
                <div class="dt-team-meta">绑定于 {{ t.bindTime }} · 贡献 {{ t.orderCount || 0 }} 单 ￥{{ fenToYuan(t.contribution) }}</div>
              </div>
            </div>
            <van-empty v-if="!team.length" image-size="80" description="暂无团队成员，快去分享邀请码吧" />
          </van-tab>

          <!-- 佣金明细 -->
          <van-tab title="佣金明细">
            <van-list v-model:loading="commissionLoading" :finished="commissionFinished" finished-text="没有更多了" @load="onLoadCommission">
              <div v-for="c in commission" :key="c.id" class="dt-com-row">
                <div>
                  <div>订单 #{{ c.orderId }} <van-tag :type="commissionTag(c.status)">{{ commissionText(c.status) }}</van-tag></div>
                  <div class="dt-com-meta">比例 {{ c.rate }}‰{{ c.settleTime ? ` · 结算于 ${c.settleTime}` : '' }}</div>
                </div>
                <span class="dt-com-amount">+￥{{ fenToYuan(c.amount) }}</span>
              </div>
            </van-list>
            <van-empty v-if="commissionFinished && !commission.length" image-size="80" description="暂无佣金记录" />
          </van-tab>

          <!-- 提现 -->
          <van-tab title="佣金提现">
            <van-cell-group inset class="dt-form">
              <van-field
                v-model="withdrawAmount"
                type="number"
                label="提现金额"
                placeholder="单位：元"
                right-icon="minus"
              />
              <van-cell title="提现方式" :value="methodText(method)" is-link @click="showMethod = true" />
            </van-cell-group>
            <div class="dt-withdraw-tip">可提现佣金 ￥{{ fenToYuan(info.withdrawable) }}，提交后进入人工审核。</div>
            <div class="dt-withdraw-btn">
              <van-button round block type="primary" color="#4a6cf7" :loading="withdrawing" @click="onWithdraw">
                提交提现申请
              </van-button>
            </div>
            <div class="dt-record-tip">
              <van-icon name="info-o" />
              提现记录查询接口暂未开放，提交后请等待审核通知。
            </div>
          </van-tab>
        </van-tabs>

        <!-- 绑定上级邀请码（非分销员也可绑定归属关系） -->
        <van-cell-group inset class="dt-bind">
          <van-field v-model="bindCode" label="上级邀请码" placeholder="填写上级邀请码，绑定归属关系" clearable />
          <van-button size="small" round plain type="primary" color="#4a6cf7" class="dt-bind-btn" :loading="binding" @click="onBind">
            绑定
          </van-button>
        </van-cell-group>
      </template>
    </template>

    <!-- 提现方式选择 -->
    <van-popup v-model:show="showMethod" position="bottom" round>
      <van-picker
        :columns="methodColumns"
        @confirm="({ selectedValues }) => { method = selectedValues[0]; showMethod = false }"
        @cancel="showMethod = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import {
  getDistributorInfo,
  applyDistributor,
  bindDistributor,
  getDistributorTeam,
  getCommissionPage,
  withdrawCommission,
} from '@/api/distributor'
import { fenToYuan } from '@/utils/format'

const loading = ref(true)
const info = ref(null)
const team = ref([])
const tab = ref(0)

// 申请
const applying = ref(false)

// 绑定上级
const bindCode = ref('')
const binding = ref(false)

// 佣金明细分页（Page 契约：records）
const commission = ref([])
const commissionLoading = ref(false)
const commissionFinished = ref(false)
const commissionPageNum = ref(1)
const commissionPageSize = 20

// 提现
const withdrawAmount = ref('')
const method = ref(0)
const showMethod = ref(false)
const withdrawing = ref(false)

const METHOD_TEXT = { 0: '余额', 1: '微信', 2: '银行卡' }
const methodColumns = [
  { text: '余额', value: 0 },
  { text: '微信', value: 1 },
  { text: '银行卡', value: 2 },
]
const methodText = m => METHOD_TEXT[m] || '余额'
const STATUS_TEXT = { 0: '待审核', 1: '已启用', 2: '已停用', 3: '已驳回' }
const statusText = s => STATUS_TEXT[s] || '未知'
const statusTag = s => (s === 1 ? 'success' : s === 3 ? 'danger' : 'warning')
const COMMISSION_TEXT = { 0: '待结算', 1: '已结算', 2: '已失效' }
const commissionText = s => COMMISSION_TEXT[s] || '未知'
const commissionTag = s => (s === 1 ? 'success' : s === 2 ? 'default' : 'warning')

const loadAll = async () => {
  info.value = await getDistributorInfo()
  if (info.value && info.value.status === 1) {
    team.value = (await getDistributorTeam()) || []
  }
}

const onApply = async () => {
  await showConfirmDialog({
    title: '申请成为分销员',
    message: '提交后将进入审核，审核通过自动分配专属邀请码。',
    confirmButtonColor: '#4a6cf7',
  })
  applying.value = true
  try {
    // 后端 POST /apply 无请求体，审核通过后返回专属邀请码
    const data = await applyDistributor()
    await loadAll()
    showSuccessToast(data && data.inviteCode ? `申请成功，邀请码 ${data.inviteCode}` : '申请成功')
  } finally {
    applying.value = false
  }
}

const onBind = async () => {
  if (!bindCode.value) return showToast('请输入上级邀请码')
  binding.value = true
  try {
    await bindDistributor({ inviteCode: bindCode.value, channel: 'code' })
    showSuccessToast('绑定成功')
    bindCode.value = ''
  } finally {
    binding.value = false
  }
}

const onLoadCommission = async () => {
  try {
    const page = await getCommissionPage({ pageNum: commissionPageNum.value, pageSize: commissionPageSize })
    const records = page.records || []
    commission.value.push(...records)
    commissionPageNum.value += 1
    if (!records.length || commission.value.length >= (page.total || 0)) {
      commissionFinished.value = true
    }
  } catch (e) {
    commissionFinished.value = true
  } finally {
    commissionLoading.value = false
  }
}

const onWithdraw = async () => {
  const yuan = Number(withdrawAmount.value)
  if (!Number.isFinite(yuan) || yuan <= 0) return showToast('请输入正确的提现金额')
  const amountFen = Math.round(yuan * 100)
  withdrawing.value = true
  try {
    await withdrawCommission({ amount: amountFen, method: method.value })
    showSuccessToast('提现申请已提交')
    withdrawAmount.value = ''
    await loadAll()
  } finally {
    withdrawing.value = false
  }
}

onMounted(async () => {
  try {
    await loadAll()
  } catch (e) {
    // 拦截器已 toast；未登录会被跳转登录页
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}
.dt-loading {
  margin-top: 60px;
}
.dt-apply {
  background: #fff;
  margin: 12px;
  border-radius: 10px;
  padding: 28px 20px;
  text-align: center;
}
.dt-apply-title {
  font-size: 17px;
  font-weight: 600;
  margin: 10px 0 6px;
}
.dt-apply-desc {
  font-size: 12px;
  color: #969799;
  line-height: 1.6;
  margin-bottom: 18px;
}
.dt-head {
  background: linear-gradient(135deg, #4a6cf7, #7a9bff);
  margin: 12px;
  border-radius: 10px;
  padding: 16px;
  color: #fff;
}
.dt-row1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.dt-code {
  font-size: 14px;
  font-weight: 600;
}
.dt-reject {
  font-size: 12px;
  margin-top: 8px;
  opacity: 0.9;
}
.dt-stats {
  display: flex;
  margin-top: 16px;
}
.dt-stat {
  flex: 1;
  text-align: center;
}
.dt-stat-num {
  font-size: 17px;
  font-weight: 700;
}
.dt-stat-label {
  font-size: 11px;
  opacity: 0.85;
  margin-top: 2px;
}
.dt-tabs {
  margin: 0 4px;
}
.dt-team {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  margin: 10px;
  border-radius: 8px;
  padding: 12px;
}
.dt-team-info {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}
.dt-team-meta {
  font-size: 11px;
  color: #969799;
  margin-top: 4px;
}
.dt-com-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  margin: 10px;
  border-radius: 8px;
  padding: 12px;
  font-size: 13px;
}
.dt-com-meta {
  font-size: 11px;
  color: #969799;
  margin-top: 4px;
}
.dt-com-amount {
  color: #e1251b;
  font-weight: 600;
}
.dt-form {
  margin-top: 12px;
}
.dt-withdraw-tip {
  font-size: 12px;
  color: #969799;
  padding: 10px 20px;
}
.dt-withdraw-btn {
  padding: 0 12px;
}
.dt-record-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #ffa940;
  padding: 12px 20px;
}
.dt-bind {
  margin: 12px;
  display: flex;
  align-items: center;
}
.dt-bind-btn {
  margin-right: 10px;
  flex-shrink: 0;
}
</style>
