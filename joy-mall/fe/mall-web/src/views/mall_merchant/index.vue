<template>
  <div class="app-container">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">商家入驻</h2>
        <p class="page-desc">入驻申请审核、店铺启停与抽成费率维护；通过后店铺才可在 C 端展示、上架商品</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 家店铺</span>
        <el-button type="primary" size="small" icon="el-icon-plus" @click="openApply">新增入驻申请</el-button>
      </div>
    </div>

    <!-- 筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="small" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model.trim="query.keyword"
            placeholder="店铺名 / 编号 / 联系人 / 执照主体"
            clearable
            style="width: 260px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(s, k) in STATUS" :key="k" :label="s.text" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="经营类目">
          <el-input v-model.trim="query.category" placeholder="类目" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <template #header><div class="card-header">
        <div class="card-title">
          <span class="title">商家台账</span>
          <span class="subtitle">共 {{ total }} 家</span>
        </div>
        <span class="hint">id=1 为平台自营店铺（历史商品/订单归属）</span>
      </div></template>

      <el-table
        v-loading="loading"
        :data="list"
        class="data-table"
        stripe
        header-cell-class-name="table-head-cell"
        empty-text="暂无数据"
      >
        <el-table-column prop="merchantNo" label="商家编号" width="140" show-overflow-tooltip />
        <el-table-column label="店铺" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="shop-cell">
              <el-image v-if="row.logo" :src="thumb(row.logo, 60)" fit="cover" class="shop-logo" />
              <span v-else class="shop-logo shop-logo--empty">{{ (row.name || '?').slice(0, 1) }}</span>
              <div class="shop-cell__body">
                <div class="shop-name">{{ row.name }}</div>
                <div class="muted-cell">{{ row.category || '—' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="执照主体" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.legalName || '—' }}</div>
            <div class="muted-cell">{{ row.licenseNo || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="联系人" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.contactName || '—' }}</div>
            <div class="muted-cell">{{ row.contactPhone || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="抽成费率" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="mini" type="info">{{ rateText(row.settleRate) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可提现余额" width="120" align="right">
          <template #default="{ row }"><span class="money-cell">¥{{ yuan(row.accountBalance) }}</span></template>
        </el-table-column>
        <el-table-column label="累计结算" width="120" align="right">
          <template #default="{ row }">¥{{ yuan(row.settledTotal) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag size="mini" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="入驻时间" width="160" align="center" />
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="text" @click="openDetail(row)">详情</el-button>
            <el-button v-if="Number(row.status) === 0" v-hasPermi="['mall_mall_merchant:audit']" type="text" @click="openAudit(row)">审核</el-button>
            <el-button v-if="Number(row.status) === 1" v-hasPermi="['mall_mall_merchant:status']" type="text" class="danger-btn" @click="onToggleStatus(row)">停用</el-button>
            <el-button v-if="Number(row.status) === 2" v-hasPermi="['mall_mall_merchant:status']" type="text" @click="onToggleStatus(row)">启用</el-button>
            <el-button v-hasPermi="['mall_mall_merchant:edit']" type="text" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :page-size="query.size"
        v-model:current-page="query.page"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
    </el-card>

    <!-- 申请 / 编辑共用弹窗 -->
    <el-dialog :title="form.id ? '编辑店铺资料' : '新增入驻申请'" v-model="formVisible" width="620px" top="8vh">
      <el-form ref="form" :model="form" :rules="rules" label-width="110px" size="small">
        <el-form-item label="店铺名称" prop="name">
          <el-input v-model.trim="form.name" maxlength="60" placeholder="C 端展示的店铺名" />
        </el-form-item>
        <el-form-item label="执照主体">
          <el-input v-model.trim="form.legalName" maxlength="100" placeholder="营业执照上的企业/个体户名称" />
        </el-form-item>
        <el-form-item label="信用代码">
          <el-input v-model.trim="form.licenseNo" maxlength="32" placeholder="统一社会信用代码；同执照未驳回前不可重复申请" />
        </el-form-item>
        <el-form-item label="执照图片">
          <image-upload v-model="form.licenseImg" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model.trim="form.contactName" maxlength="30" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model.trim="form.contactPhone" maxlength="20" />
        </el-form-item>
        <el-form-item label="经营类目">
          <el-input v-model.trim="form.category" maxlength="30" placeholder="如：服饰 / 数码 / 食品" />
        </el-form-item>
        <el-form-item label="店铺 LOGO">
          <image-upload v-model="form.logo" />
        </el-form-item>
        <el-form-item label="店铺简介">
          <el-input v-model="form.intro" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="抽成费率">
          <el-input-number v-model="form.settleRate" :min="0" :max="999" :step="5" controls-position="right" style="width: 160px" />
          <span class="form-tip">‰（千分比），{{ rateText(form.settleRate) }}；留空取全局默认</span>
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button size="small" @click="formVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submitForm">{{ form.id ? '保存' : '提交申请' }}</el-button>
      </div></template>
    </el-dialog>

    <!-- 审核弹窗 -->
    <el-dialog :title="`入驻审核 · ${auditForm.name || ''}`" v-model="auditVisible" width="480px" top="12vh">
      <div class="kv">
        <div class="kv-row"><span class="k">商家编号</span><span>{{ auditForm.merchantNo }}</span></div>
        <div class="kv-row"><span class="k">执照主体</span><span>{{ auditForm.legalName || '—' }}</span></div>
        <div class="kv-row"><span class="k">信用代码</span><span>{{ auditForm.licenseNo || '—' }}</span></div>
        <div class="kv-row"><span class="k">抽成费率</span><span>{{ rateText(auditForm.settleRate) }}</span></div>
      </div>
      <el-form label-width="80px" size="small" style="margin-top:12px">
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditForm.pass">
            <el-radio :label="true">通过</el-radio>
            <el-radio :label="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditForm.pass === false" label="驳回原因">
          <el-input v-model="auditForm.rejectReason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="驳回原因必填，商家可见" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button size="small" @click="auditVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submitAudit">提交</el-button>
      </div></template>
    </el-dialog>

    <!-- 详情弹窗：资料 + 审核流水时间线 -->
    <el-dialog title="商家详情" v-model="detailVisible" width="720px" top="8vh">
      <div class="detail-grid">
        <div class="kv-row"><span class="k">商家编号</span><span>{{ detail.merchantNo }}</span></div>
        <div class="kv-row"><span class="k">店铺名称</span><span>{{ detail.name }}</span></div>
        <div class="kv-row"><span class="k">执照主体</span><span>{{ detail.legalName || '—' }}</span></div>
        <div class="kv-row"><span class="k">信用代码</span><span>{{ detail.licenseNo || '—' }}</span></div>
        <div class="kv-row"><span class="k">联系人</span><span>{{ detail.contactName || '—' }} {{ detail.contactPhone || '' }}</span></div>
        <div class="kv-row"><span class="k">经营类目</span><span>{{ detail.category || '—' }}</span></div>
        <div class="kv-row"><span class="k">抽成费率</span><span>{{ rateText(detail.settleRate) }}</span></div>
        <div class="kv-row"><span class="k">状态</span><span>{{ statusText(detail.status) }}</span></div>
        <div class="kv-row"><span class="k">可提现余额</span><span class="money-cell">¥{{ yuan(detail.accountBalance) }}</span></div>
        <div class="kv-row"><span class="k">累计结算</span><span>¥{{ yuan(detail.settledTotal) }}</span></div>
        <div class="kv-row kv-row--full">
          <span class="k">执照图片</span>
          <span>
            <el-image v-if="detail.licenseImg" :src="thumb(detail.licenseImg, 120)" :preview-src-list="[detail.licenseImg]" fit="cover" style="width:120px;height:120px;border-radius:6px;display:block" />
            <span v-else class="muted-cell">—</span>
          </span>
        </div>
        <div class="kv-row kv-row--full">
          <span class="k">店铺 LOGO</span>
          <span>
            <el-image v-if="detail.logo" :src="thumb(detail.logo, 120)" :preview-src-list="[detail.logo]" fit="cover" style="width:120px;height:120px;border-radius:6px;display:block" />
            <span v-else class="muted-cell">—</span>
          </span>
        </div>
        <div class="kv-row kv-row--full"><span class="k">店铺简介</span><span>{{ detail.intro || '—' }}</span></div>
        <div v-if="detail.rejectReason" class="kv-row kv-row--full"><span class="k">驳回原因</span><span>{{ detail.rejectReason }}</span></div>
      </div>

      <div class="timeline-title">审核与状态流水</div>
      <el-timeline v-if="logs.length" class="timeline">
        <el-timeline-item
          v-for="(l, i) in logs"
          :key="i"
          :timestamp="l.createTime"
          placement="top"
          :type="logType(l.action)"
        >
          <div class="log-line">
            <span class="log-action">{{ l.actionText || l.action }}</span>
            <span class="log-operator">操作人：{{ l.operator || 'system' }}</span>
          </div>
          <div v-if="l.remark" class="log-remark">{{ l.remark }}</div>
        </el-timeline-item>
      </el-timeline>
      <div v-else class="empty-tip">暂无流水</div>

      <template #footer><div>
        <el-button size="small" @click="detailVisible = false">关闭</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import {
  getMallMerchantPage,
  getMallMerchantDetail,
  applyMallMerchant,
  auditMallMerchant,
  changeMallMerchantStatus,
  updateMallMerchant
} from '@/api/mall_merchant'
import ImageUpload from '@/components/ImageUpload.vue'

// 状态：0 待审 / 1 启用 / 2 停用 / 3 驳回（与 MallMerchantServiceImpl 常量一致）
const STATUS = {
  0: { text: '待审核', tag: 'warning' },
  1: { text: '启用中', tag: 'success' },
  2: { text: '已停用', tag: 'info' },
  3: { text: '已驳回', tag: 'danger' }
}

const LOG_TYPE = { apply: 'primary', pass: 'success', reject: 'danger', disable: 'info', enable: 'success' }

function emptyForm() {
  return {
    id: undefined,
    name: '',
    legalName: '',
    licenseNo: '',
    licenseImg: '',
    contactName: '',
    contactPhone: '',
    category: '',
    logo: '',
    intro: '',
    settleRate: undefined
  }
}

export default {
  name: 'MallMerchant',
  components: { ImageUpload },
  data() {
    return {
      loading: false,
      acting: false,
      list: [],
      total: 0,
      STATUS,
      query: { page: 1, size: 10, keyword: '', status: undefined, category: '' },
      formVisible: false,
      form: emptyForm(),
      rules: {
        name: [{ required: true, message: '店铺名称必填', trigger: 'blur' }]
      },
      auditVisible: false,
      auditForm: { id: undefined, name: '', merchantNo: '', legalName: '', licenseNo: '', settleRate: undefined, pass: true, rejectReason: '' },
      detailVisible: false,
      detail: {},
      logs: []
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    statusText: s => (STATUS[Number(s)] || {}).text || '未知',
    statusTag: s => (STATUS[Number(s)] || {}).tag || 'info',
    logType: a => LOG_TYPE[a] || 'primary',
    /** 费率单位 ‰ → 百分比展示 */
    rateText(rate) {
      const n = Number(rate)
      if (!Number.isFinite(n)) return '默认'
      return `${(n / 10).toFixed(1)}%（${n}‰）`
    },
    yuan(fen) {
      const n = Number(fen)
      return Number.isFinite(n) ? (n / 100).toFixed(2) : '0.00'
    },
    handleSearch() {
      this.query.page = 1
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: this.query.size, keyword: '', status: undefined, category: '' }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.query.page = 1
      this.fetchList()
    },
    async fetchList() {
      this.loading = true
      try {
        const res = await getMallMerchantPage({
          page: this.query.page,
          size: this.query.size,
          keyword: this.query.keyword || undefined,
          status: this.query.status,
          category: this.query.category || undefined
        })
        this.list = (res.data && res.data.list) || []
        this.total = Number((res.data && res.data.total) || 0)
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    openApply() {
      this.form = emptyForm()
      this.formVisible = true
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    openEdit(row) {
      this.form = {
        id: row.id,
        name: row.name,
        legalName: row.legalName,
        licenseNo: row.licenseNo,
        licenseImg: row.licenseImg,
        contactName: row.contactName,
        contactPhone: row.contactPhone,
        category: row.category,
        logo: row.logo,
        intro: row.intro,
        settleRate: row.settleRate
      }
      this.formVisible = true
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    submitForm() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.acting = true
        try {
          const payload = { ...this.form }
          if (payload.id) {
            await updateMallMerchant(payload)
            this.$message.success('店铺资料已保存')
          } else {
            delete payload.id
            const res = await applyMallMerchant(payload)
            this.$message.success(`入驻申请已提交，商家编号 ${(res.data && res.data.merchantNo) || ''}`)
          }
          this.formVisible = false
          this.fetchList()
        } finally {
          this.acting = false
        }
      })
    },
    openAudit(row) {
      this.auditForm = {
        id: row.id,
        name: row.name,
        merchantNo: row.merchantNo,
        legalName: row.legalName,
        licenseNo: row.licenseNo,
        settleRate: row.settleRate,
        pass: true,
        rejectReason: ''
      }
      this.auditVisible = true
    },
    async submitAudit() {
      const f = this.auditForm
      if (f.pass === false && !(f.rejectReason || '').trim()) {
        this.$message.warning('驳回时请填写驳回原因')
        return
      }
      this.acting = true
      try {
        await auditMallMerchant({
          id: f.id,
          pass: f.pass,
          rejectReason: f.pass ? undefined : f.rejectReason.trim()
        })
        this.$message.success(f.pass ? '审核通过，店铺已启用' : '已驳回')
        this.auditVisible = false
        this.fetchList()
      } finally {
        this.acting = false
      }
    },
    async onToggleStatus(row) {
      const enable = Number(row.status) === 2
      const actionText = enable ? '启用' : '停用'
      await this.$confirm(
        enable
          ? `确认启用店铺「${row.name}」？启用后 C 端可展示并售卖其商品。`
          : `确认停用店铺「${row.name}」？停用后 C 端店铺不可访问，商品列表也不再展示其店铺信息。`,
        `${actionText}店铺`,
        { type: 'warning', confirmButtonText: actionText, cancelButtonText: '取消' }
      )
      await changeMallMerchantStatus({ id: row.id, enable })
      this.$message.success(`已${actionText}`)
      this.fetchList()
    },
    async openDetail(row) {
      this.detail = row
      this.logs = []
      this.detailVisible = true
      const res = await getMallMerchantDetail(row.id)
      const data = (res && res.data) || {}
      this.detail = data.merchant || row
      this.logs = data.logs || []
    }
  }
}
</script>

<style scoped>
.app-container {
  padding: 0;
  background: transparent;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
  letter-spacing: 0.2px;
}

.page-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: #8a94a6;
}

.page-head__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 4px;
}

.meta-pill {
  padding: 4px 12px;
  border-radius: 20px;
  background: #eef2ff;
  color: #4d7cfe;
  font-size: 12px;
  font-weight: 500;
}

.filter-card,
.table-card {
  border: none;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04), 0 6px 20px rgba(16, 24, 40, 0.05);
}

.filter-card {
  margin-bottom: 16px;
}

.filter-card :deep(.el-card__body){
  padding: 18px 18px 4px;
}

.filter-card :deep(.el-form-item){
  margin-right: 16px;
  margin-bottom: 14px;
}

.table-card :deep(.el-card__header){
  padding: 16px 18px;
  border-bottom: 1px solid #eef1f7;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  display: flex;
  align-items: baseline;
}

.card-title .title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.card-title .subtitle {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.hint {
  font-size: 12px;
  color: #a0a6b3;
}

.data-table :deep(.table-head-cell){
  background: #f7f9fc !important;
  color: #5c6270;
  font-weight: 600;
}

.data-table :deep(td){
  padding: 11px 0;
}

.data-table :deep(.el-table__row:hover > td){
  background: #f5f9ff;
}

.muted-cell {
  color: #909399;
  font-size: 12px;
}

.money-cell {
  color: #e1251b;
  font-weight: 600;
}

.shop-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.shop-logo {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  flex: none;
}

.shop-logo--empty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #eef2ff;
  color: #4d7cfe;
  font-weight: 600;
}

.shop-name {
  color: #111827;
  font-weight: 500;
}

.pagination {
  margin-top: 18px;
  text-align: right;
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.kv {
  font-size: 14px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 24px;
  font-size: 14px;
}

.kv-row {
  display: flex;
  padding: 6px 0;
  gap: 8px;
}

.kv-row--full {
  grid-column: 1 / -1;
}

.kv-row .k {
  width: 90px;
  flex: none;
  color: #909399;
}

.timeline-title {
  margin: 18px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.timeline {
  padding-left: 4px;
}

.log-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.log-action {
  font-weight: 600;
  color: #111827;
}

.log-operator {
  font-size: 12px;
  color: #909399;
}

.log-remark {
  margin-top: 4px;
  font-size: 12px;
  color: #5a6478;
}

.empty-tip {
  padding: 12px 0;
  font-size: 13px;
  color: #a0a6b3;
}

.danger-btn {
  color: #e1251b;
}
</style>
