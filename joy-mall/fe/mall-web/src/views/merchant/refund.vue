<template>
  <div>
    <el-card shadow="never">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="售后单号 / 订单号" clearable style="width:220px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:140px" @change="search">
            <el-option label="待审核" :value="1" />
            <el-option label="已同意" :value="2" />
            <el-option label="已拒绝" :value="3" />
            <el-option label="已完成" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt">
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无售后申请">
        <el-table-column prop="refundNo" label="售后单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="订单号" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="mini" :type="Number(row.type) === 2 ? 'warning' : (Number(row.type) === 3 ? 'success' : '')">
              {{ typeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }"><span class="price">¥{{ fenToYuan(row.refundAmount) }}</span></template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="mini" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" width="170" />
        <el-table-column label="操作" width="150" align="right">
          <template #default="{ row }">
            <template v-if="Number(row.status) === 1">
              <el-button type="text" @click="audit(row, true)">同意</el-button>
              <el-button type="text" style="color:#f56c6c" @click="audit(row, false)">拒绝</el-button>
            </template>
            <span v-else class="muted">{{ row.auditRemark || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        v-model:current-page="query.pageNum"
        :page-size="query.pageSize"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<script>
import { refundPage, refundAudit } from '@/api/merchant'
import { fenToYuan } from '@/utils/format'

export default {
  name: 'MerchantRefund',
  data() {
    return {
      query: { keyword: '', status: null, pageNum: 1, pageSize: 10 },
      list: [],
      total: 0,
      loading: false
    }
  },
  created() {
    this.load()
  },
  methods: {
    fenToYuan,
    typeText(t) {
      return { 1: '仅退款', 2: '退货退款', 3: '换货' }[Number(t)] || '售后'
    },
    statusText(s) {
      return { 1: '待审核', 2: '已同意', 3: '已拒绝', 4: '已完成' }[Number(s)] || '未知'
    },
    statusTag(s) {
      return { 1: 'warning', 2: 'success', 3: 'danger', 4: 'info' }[Number(s)] || ''
    },
    async load() {
      this.loading = true
      try {
        const res = await refundPage(this.query)
        const d = res.data || {}
        this.list = d.records || []
        this.total = d.total || 0
      } finally {
        this.loading = false
      }
    },
    search() {
      this.query.pageNum = 1
      this.load()
    },
    async audit(row, agree) {
      const tip = agree ? '同意后将按售后类型进入退款/退货流程，确定？' : '拒绝后会员可再次申请或申请平台介入，确定？'
      const { value } = await this.$prompt(tip, agree ? '同意售后' : '拒绝售后', {
        inputPlaceholder: agree ? '处理说明（选填）' : '请说明拒绝理由',
        inputValidator: v => (agree || (v && v.trim())) ? true : '拒绝必须填写理由',
        inputType: 'textarea'
      })
      await refundAudit({ id: row.id, agree, auditRemark: value || '' })
      this.$message.success(agree ? '已同意' : '已拒绝')
      this.load()
    }
  }
}
</script>

<style scoped>
.mt { margin-top: 14px; }
.price { color: #e1251b; font-weight: 600; }
.muted { color: #a8abb2; font-size: 12px; }
.pager { margin-top: 12px; text-align: right; }
</style>
