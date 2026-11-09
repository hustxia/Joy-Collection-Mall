<template>
  <div class="page-wrap">
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">分销中心</span>
      </div>

      <div v-loading="loading">
        <!-- 未开通：申请 / 绑定邀请码 -->
        <div v-if="!info" class="ds-apply">
          <el-empty description="您还不是分销员，申请后可获得专属邀请码，邀请好友下单赚佣金" :image-size="100">
            <el-button type="danger" :loading="applying" @click="apply">立即申请成为分销员</el-button>
          </el-empty>
          <div class="ds-bind">
            <span class="ds-bind-label">有上级邀请码？绑定后自动归入对方团队：</span>
            <el-input v-model="inviteCode" size="small" placeholder="请输入邀请码" style="width:200px" />
            <el-button size="small" :loading="binding" @click="bind">绑定</el-button>
          </div>
        </div>

        <!-- 已开通 -->
        <template v-else>
          <div class="ds-summary">
            <div class="ds-item">
              <div class="ds-label">分销状态</div>
              <div class="ds-value">
                <el-tag size="small" :type="statusTag(info.status)">{{ statusText(info.status) }}</el-tag>
                <el-tag v-if="info.rejectReason" size="mini" type="danger" effect="plain" style="margin-left:8px">
                  {{ info.rejectReason }}
                </el-tag>
              </div>
            </div>
            <div class="ds-item">
              <div class="ds-label">专属邀请码</div>
              <div class="ds-value ds-code">
                {{ info.inviteCode }}
                <el-button type="text" size="mini" @click="copyCode">复制</el-button>
              </div>
            </div>
            <div class="ds-item">
              <div class="ds-label">累计佣金</div>
              <div class="ds-value">¥{{ $price(info.totalCommission) }}</div>
            </div>
            <div class="ds-item">
              <div class="ds-label">已结算</div>
              <div class="ds-value">¥{{ $price(info.settled) }}</div>
            </div>
            <div class="ds-item">
              <div class="ds-label">可提现</div>
              <div class="ds-value ds-withdrawable">¥{{ $price(info.withdrawable) }}</div>
            </div>
            <el-button v-if="Number(info.status) === 1" type="danger" size="small" round @click="openWithdraw">
              申请提现
            </el-button>
          </div>

          <el-tabs v-model="tab" @tab-click="onTabChange">
            <el-tab-pane label="我的团队" name="team">
              <el-table :data="team" size="small">
                <el-table-column label="成员" min-width="120">
                  <template #default="{ row }">会员 #{{ row.inviteeId }}</template>
                </el-table-column>
                <el-table-column label="层级" width="90">
                  <template #default="{ row }">{{ Number(row.level) === 1 ? '一级' : '二级' }}</template>
                </el-table-column>
                <el-table-column prop="channel" label="来源" width="90" />
                <el-table-column label="贡献订单" width="100">
                  <template #default="{ row }">{{ row.orderCount || 0 }}</template>
                </el-table-column>
                <el-table-column label="贡献成交额" width="120">
                  <template #default="{ row }">¥{{ $price(row.contribution) }}</template>
                </el-table-column>
                <el-table-column label="绑定时间" width="160">
                  <template #default="{ row }">{{ shortTime(row.bindTime) }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!team.length && loadedTabs.team" description="还没有邀请成员，把邀请码分享给好友吧" :image-size="80" />
            </el-tab-pane>

            <el-tab-pane label="佣金明细" name="commission">
              <el-table :data="commission" size="small">
                <el-table-column label="订单" min-width="150">
                  <template #default="{ row }">订单 #{{ row.orderId }}</template>
                </el-table-column>
                <el-table-column label="佣金" width="110">
                  <template #default="{ row }">
                    <span class="ds-amount">¥{{ $price(row.amount) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="比例" width="90">
                  <template #default="{ row }">{{ row.rate }}‰</template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="mini" :type="commissionTag(row.status)">{{ commissionText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="产生时间" width="160">
                  <template #default="{ row }">{{ shortTime(row.createTime) }}</template>
                </el-table-column>
                <el-table-column label="结算时间" width="160">
                  <template #default="{ row }">{{ row.settleTime ? shortTime(row.settleTime) : '—' }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!commission.length && loadedTabs.commission" description="暂无佣金记录" :image-size="80" />
              <div v-if="commissionTotal > query.pageSize" style="text-align:right;margin-top:12px">
                <el-pagination
                  layout="prev, pager, next"
                  :total="commissionTotal"
                  :page-size="query.pageSize"
                  v-model:current-page="query.pageNum"
                  @current-change="loadCommission"
                />
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </div>

    <!-- 提现弹窗 -->
    <el-dialog title="佣金提现" v-model="withdrawVisible" width="420px">
      <el-form label-width="90px">
        <el-form-item label="可提现金额">
          <span class="ds-withdrawable">¥{{ $price(info && info.withdrawable) }}</span>
        </el-form-item>
        <el-form-item label="提现金额" required>
          <el-input-number v-model="withdrawAmount" :min="0.01" :precision="2" :step="1" style="width:200px" />
          <span style="margin-left:8px;color:#909399;font-size:12px">元</span>
        </el-form-item>
        <el-form-item label="提现方式" required>
          <el-radio-group v-model="withdrawMethod">
            <el-radio :label="0">余额</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button size="small" @click="withdrawVisible = false">取消</el-button>
        <el-button type="danger" size="small" :loading="withdrawing" @click="doWithdraw">提交申请</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import {
  getDistributorInfo, applyDistributor, bindDistributor,
  getDistributorTeam, getCommissionPage, applyWithdraw
} from '@/api/marketing'
import { shortTime } from '@/utils/format'

// 分销状态：0待审 1启用 2停用 3驳回
const STATUS = { 0: { text: '待审核', tag: 'warning' }, 1: { text: '已启用', tag: 'success' }, 2: { text: '已停用', tag: 'info' }, 3: { text: '已驳回', tag: 'danger' } }
// 佣金状态：0待结算 1已结算 2失效
const COMMISSION_STATUS = { 0: { text: '待结算', tag: 'warning' }, 1: { text: '已结算', tag: 'success' }, 2: { text: '已失效', tag: 'info' } }

export default {
  name: 'Distribution',
  data() {
    return {
      loading: false,
      info: null,
      applying: false,
      binding: false,
      inviteCode: '',
      tab: 'team',
      team: [],
      commission: [],
      commissionTotal: 0,
      loadedTabs: { team: false, commission: false },
      query: { pageNum: 1, pageSize: 10 },
      withdrawVisible: false,
      withdrawing: false,
      withdrawAmount: 1,
      withdrawMethod: 0
    }
  },
  created() {
    this.load()
  },
  methods: {
    shortTime,
    statusText(s) {
      const t = STATUS[s]
      return t ? t.text : '未知'
    },
    statusTag(s) {
      const t = STATUS[s]
      return t ? t.tag : 'info'
    },
    commissionText(s) {
      const t = COMMISSION_STATUS[s]
      return t ? t.text : '未知'
    },
    commissionTag(s) {
      const t = COMMISSION_STATUS[s]
      return t ? t.tag : 'info'
    },
    async load() {
      this.loading = true
      try {
        this.info = await getDistributorInfo()
        if (this.info) this.loadTeam()
      } catch (e) {
        this.info = null
      } finally {
        this.loading = false
      }
    },
    async apply() {
      this.applying = true
      try {
        const data = await applyDistributor()
        this.$message.success(`申请已提交，您的专属邀请码：${data.inviteCode}`)
        this.load()
      } catch (e) {
        // request 封装已提示
      } finally {
        this.applying = false
      }
    },
    async bind() {
      if (!this.inviteCode) {
        this.$message.warning('请输入邀请码')
        return
      }
      this.binding = true
      try {
        await bindDistributor({ inviteCode: this.inviteCode, channel: 'code' })
        this.$message.success('绑定成功')
        this.inviteCode = ''
      } catch (e) {
        // 已提示
      } finally {
        this.binding = false
      }
    },
    copyCode() {
      const code = this.info && this.info.inviteCode
      if (!code) return
      if (navigator.clipboard) {
        navigator.clipboard.writeText(code)
        this.$message.success('邀请码已复制')
      } else {
        this.$message.info(`邀请码：${code}`)
      }
    },
    onTabChange(tab) {
      if (tab.name === 'team' && !this.loadedTabs.team) this.loadTeam()
      if (tab.name === 'commission' && !this.loadedTabs.commission) this.loadCommission()
    },
    async loadTeam() {
      try {
        this.team = (await getDistributorTeam()) || []
      } catch (e) {
        this.team = []
      } finally {
        this.loadedTabs.team = true
      }
    },
    async loadCommission() {
      try {
        const page = await getCommissionPage({ pageNum: this.query.pageNum, pageSize: this.query.pageSize })
        this.commission = (page && page.records) || []
        this.commissionTotal = (page && Number(page.total)) || 0
      } catch (e) {
        this.commission = []
        this.commissionTotal = 0
      } finally {
        this.loadedTabs.commission = true
      }
    },
    openWithdraw() {
      this.withdrawAmount = 1
      this.withdrawMethod = 0
      this.withdrawVisible = true
    },
    async doWithdraw() {
      if (!this.withdrawAmount || this.withdrawAmount <= 0) {
        this.$message.warning('请输入提现金额')
        return
      }
      this.withdrawing = true
      try {
        await applyWithdraw({
          amount: Math.round(this.withdrawAmount * 100), // 元 → 分
          method: this.withdrawMethod
        })
        this.$message.success('提现申请已提交')
        this.withdrawVisible = false
        this.load()
      } catch (e) {
        // 已提示
      } finally {
        this.withdrawing = false
      }
    }
  }
}
</script>

<style scoped>
.ds-apply {
  padding: 20px 0 8px;
}
.ds-bind {
  margin-top: 12px;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.ds-bind-label {
  font-size: 13px;
  color: #909399;
}
.ds-summary {
  display: flex;
  align-items: center;
  gap: 36px;
  padding: 16px;
  background: #fff7f6;
  border-radius: 8px;
  flex-wrap: wrap;
}
.ds-label {
  font-size: 12px;
  color: #909399;
}
.ds-value {
  margin-top: 6px;
  font-size: 16px;
  color: #303133;
  font-weight: 600;
}
.ds-code {
  font-family: monospace;
  letter-spacing: 1px;
}
.ds-withdrawable {
  color: #e1251b;
  font-size: 20px;
}
.ds-amount {
  color: #e1251b;
  font-weight: 600;
}
</style>
