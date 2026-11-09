<template>
  <div class="app-container">
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">发票管理</h2>
        <p class="page-desc">订单完成后会员可申请开票，一单一票、金额取实付不可改；上传电子发票后置「已开票」，已开票订单退款会自动红冲。</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 条申请</span>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="small" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="订单号 / 申请单号 / 抬头" clearable style="width: 220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option label="待开票" :value="0" />
            <el-option label="已开票" :value="1" />
            <el-option label="已红冲" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header><div class="card-header">
        <div class="card-title">
          <span class="title">开票队列</span>
          <span class="subtitle">共 {{ total }} 条</span>
        </div>
        <span class="hint">金额恒等于订单实付；红冲后原票作废，需重新申请</span>
      </div></template>

      <el-table v-loading="loading" :data="list" class="data-table" stripe empty-text="暂无开票申请">
        <el-table-column prop="invoiceNo" label="申请单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="抬头" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="mini" :type="Number(row.titleType) === 2 ? 'warning' : ''">
              {{ Number(row.titleType) === 2 ? '企业' : '个人' }}
            </el-tag>
            <span class="title-text">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="taxNo" label="税号" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taxNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }"><span class="money-cell">¥{{ yuan(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag size="mini" :type="statusTag(row.status)">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="160" align="center">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="Number(row.status) === 0" v-hasPermi="['mall_mall_invoice:issue']" type="text" @click="openIssue(row)">开票</el-button>
            <el-button v-if="Number(row.status) === 1" v-hasPermi="['mall_mall_invoice:red']" type="text" class="danger-text" @click="redFlush(row)">红冲</el-button>
            <el-button v-if="row.fileUrl" type="text" @click="preview(row)">查看发票</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :page-size="query.size"
        v-model:current-page="query.page"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
    </el-card>

    <el-dialog title="开票" v-model="issueVisible" width="520px">
      <el-form label-width="96px" size="small">
        <el-form-item label="订单号">
          <span>{{ current.orderNo }}</span>
        </el-form-item>
        <el-form-item label="抬头">
          <span>{{ current.title }}</span>
        </el-form-item>
        <el-form-item label="开票金额">
          <span class="money-cell">¥{{ yuan(current.amount) }}</span>
        </el-form-item>
        <el-form-item label="电子发票">
          <el-input v-model="fileUrl" placeholder="电子发票文件地址，如 /uploads/invoice/xxx.pdf" />
        </el-form-item>
      </el-form>
      <div class="dialog-tip">上传后该地址会直接下发给会员，请确保可访问（HTTP 200）。</div>
      <template #footer><div>
        <el-button size="small" @click="issueVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submitIssue">确认开票</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { getMallInvoicePage, issueMallInvoice, redFlushMallInvoice } from '@/api/mall_invoice'

export default {
  name: 'MallInvoice',
  data() {
    return {
      loading: false,
      acting: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10, keyword: '', status: undefined },
      issueVisible: false,
      current: {},
      fileUrl: ''
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    yuan(fen) {
      const n = Number(fen)
      return Number.isFinite(n) ? (n / 100).toFixed(2) : '0.00'
    },
    fmt(v) {
      if (!v) return '—'
      return String(v).replace('T', ' ').slice(0, 19)
    },
    statusTag(status) {
      const n = Number(status)
      if (n === 1) return 'success'
      if (n === 2) return 'info'
      return 'warning'
    },
    handleSearch() {
      this.query.page = 1
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: this.query.size, keyword: '', status: undefined }
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
        const res = await getMallInvoicePage({
          page: this.query.page,
          size: this.query.size,
          keyword: this.query.keyword || undefined,
          status: this.query.status
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
    openIssue(row) {
      this.current = row
      this.fileUrl = ''
      this.issueVisible = true
    },
    async submitIssue() {
      if (!this.fileUrl) {
        this.$message.warning('请填写电子发票文件地址')
        return
      }
      this.acting = true
      try {
        await issueMallInvoice(this.current.id, this.fileUrl)
        this.$message.success('已开票')
        this.issueVisible = false
        this.fetchList()
      } finally {
        this.acting = false
      }
    },
    async redFlush(row) {
      const { value } = await this.$prompt('填写红冲原因（如：订单退款）', '红冲发票', {
        inputValue: '订单退款自动红冲',
        inputValidator: v => (v && v.trim() ? true : '请填写原因')
      })
      await redFlushMallInvoice(row.id, value)
      this.$message.success('已红冲')
      this.fetchList()
    },
    preview(row) {
      window.open(row.fileUrl, '_blank')
    }
  }
}
</script>

<style scoped>
.title-text {
  margin-left: 8px;
}
.dialog-tip {
  color: #909399;
  font-size: 12px;
  padding-left: 96px;
}
.danger-text {
  color: #f56c6c;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title .title {
  font-weight: 600;
}
.card-title .subtitle {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.card-header .hint {
  color: #909399;
  font-size: 12px;
}
</style>
