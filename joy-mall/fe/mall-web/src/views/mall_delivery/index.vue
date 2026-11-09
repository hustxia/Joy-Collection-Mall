<template>
  <div class="app-container">
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">发货单</h2>
        <p class="page-desc">一个订单可拆多个包裹：只发部分商品时订单仍是「待发货」，全部发完才转「已发货」。单号全局唯一，重复发货运单会被拦下。</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 张发货单</span>
        <el-button v-hasPermi="['mall_mall_delivery:batch']" size="small" icon="el-icon-upload2" @click="openBatch">批量导入</el-button>
        <el-button v-hasPermi="['mall_mall_delivery:ship']" type="primary" size="small" icon="el-icon-send" @click="openShip">发货</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="small" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="订单号 / 快递单号" clearable style="width: 220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="handleSearch">
            <el-option label="已发货" :value="1" />
            <el-option label="已签收" :value="2" />
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
          <span class="title">发货单台账</span>
          <span class="subtitle">共 {{ total }} 张</span>
        </div>
        <span class="hint">同一订单多包裹时，订单详情里的「查看物流」会按包裹分组展示</span>
      </div></template>

      <el-table v-loading="loading" :data="list" class="data-table" stripe empty-text="暂无发货单">
        <el-table-column prop="deliveryNo" label="发货单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="快递" min-width="160">
          <template #default="{ row }">
            <span>{{ row.expressCompany }}</span>
            <span class="express-no">{{ row.expressNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="itemCount" label="明细行" width="90" align="center" />
        <el-table-column label="收货人" min-width="130">
          <template #default="{ row }">{{ row.receiverName || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag size="mini" :type="Number(row.status) === 2 ? 'success' : ''">
              {{ Number(row.status) === 2 ? '已签收' : '已发货' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发货时间" width="160" align="center">
          <template #default="{ row }">{{ fmt(row.deliveryTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="text" @click="openDetail(row)">明细</el-button>
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

    <!-- 发货 -->
    <el-dialog title="订单发货" v-model="shipVisible" width="760px" top="8vh">
      <el-form label-width="84px" size="small">
        <el-form-item label="订单号">
          <el-input v-model="shipForm.orderNo" placeholder="输入订单号后点「载入明细」" style="width: 320px" @keyup.enter="loadPending">
<template #append>            <el-button icon="el-icon-search" @click="loadPending">载入明细</el-button></template>
          </el-input>
        </el-form-item>
        <el-form-item label="快递公司">
          <el-input v-model="shipForm.expressCompany" placeholder="如：顺丰速运" style="width: 220px" />
        </el-form-item>
        <el-form-item label="快递单号">
          <el-input v-model="shipForm.expressNo" placeholder="全局唯一，重复会被拦截" style="width: 320px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="shipForm.remark" placeholder="选填" />
        </el-form-item>
      </el-form>

      <div v-if="pending" class="pending-box">
        <div class="pending-head">
          <span>收货人：{{ pending.receiverName }}　{{ pending.receiverPhone }}</span>
          <span class="addr">{{ pending.receiverAddress }}</span>
        </div>
        <el-table :data="pending.items" size="mini" class="data-table" empty-text="该订单没有待发商品">
          <el-table-column prop="goodsName" label="商品" min-width="200" show-overflow-tooltip />
          <el-table-column prop="quantity" label="下单数" width="80" align="center" />
          <el-table-column prop="shippedQuantity" label="已发" width="80" align="center" />
          <el-table-column prop="remain" label="待发" width="80" align="center" />
          <el-table-column label="本次发货" width="140" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.shipNow" :min="0" :max="row.remain" size="mini" controls-position="right" style="width: 110px" />
            </template>
          </el-table-column>
        </el-table>
        <div class="pending-tip">
          把不想发的行设为 0 即可只发一部分；全部行都发完后订单才转「已发货」。
        </div>
      </div>

      <template #footer><div>
        <el-button size="small" @click="shipVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submitShip">确认发货</el-button>
      </div></template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog title="批量发货导入" v-model="batchVisible" width="720px" top="8vh">
      <div class="batch-tip">
        每行一条：<code>订单号,快递公司,快递单号</code>。逐行校验，失败行会在下方列出原因，成功的行照常发货。
      </div>
      <el-input
        v-model="batchText"
        type="textarea"
        :rows="8"
        placeholder="M20261101123000000001,顺丰速运,SF1234567890"
      />
      <div v-if="batchResult" class="batch-result">
        <div class="batch-summary">
          共 {{ batchResult.total }} 行，成功 <b class="ok">{{ batchResult.success }}</b> 行，失败
          <b class="bad">{{ batchResult.failed }}</b> 行
        </div>
        <el-table v-if="batchResult.errors && batchResult.errors.length" :data="batchResult.errors" size="mini" class="data-table">
          <el-table-column prop="row" label="行号" width="70" align="center" />
          <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
          <el-table-column prop="expressNo" label="快递单号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="reason" label="失败原因" min-width="180" show-overflow-tooltip />
        </el-table>
      </div>
      <template #footer><div>
        <el-button size="small" @click="batchVisible = false">关闭</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submitBatch">导入并发货</el-button>
      </div></template>
    </el-dialog>

    <!-- 明细 -->
    <el-dialog :title="`发货单明细 · ${detail.deliveryNo || ''}`" v-model="detailVisible" width="520px">
      <div class="detail-head">
        <div>{{ detail.expressCompany }}　{{ detail.expressNo }}</div>
        <div class="muted">{{ fmt(detail.deliveryTime) }}</div>
      </div>
      <el-table :data="detailItems" size="mini" class="data-table" empty-text="暂无明细">
        <el-table-column prop="goodsName" label="商品" min-width="200" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="80" align="center" />
      </el-table>
      <template #footer><div>
        <el-button size="small" @click="detailVisible = false">关闭</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { getMallDeliveryPage, getMallDeliveryDetail, shipMallDelivery, batchMallDelivery } from '@/api/mall_delivery'
import request from '@/utils/request'

export default {
  name: 'MallDelivery',
  data() {
    return {
      loading: false,
      acting: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10, keyword: '', status: undefined },
      shipVisible: false,
      shipForm: { orderNo: '', expressCompany: '顺丰速运', expressNo: '', remark: '' },
      pending: null,
      batchVisible: false,
      batchText: '',
      batchResult: null,
      detailVisible: false,
      detail: {},
      detailItems: []
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    fmt(v) {
      if (!v) return '—'
      return String(v).replace('T', ' ').slice(0, 19)
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
        const res = await getMallDeliveryPage({
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
    openShip() {
      this.shipForm = { orderNo: '', expressCompany: '顺丰速运', expressNo: '', remark: '' }
      this.pending = null
      this.shipVisible = true
    },
    async loadPending() {
      if (!this.shipForm.orderNo) {
        this.$message.warning('请输入订单号')
        return
      }
      const res = await request({
        url: '/api/mall/mall_delivery/pending',
        method: 'get',
        params: { orderNo: this.shipForm.orderNo.trim() }
      })
      const data = res.data || {}
      data.items = (data.items || []).map(i => ({ ...i, shipNow: i.remain }))
      this.pending = data
      if (!data.deliverable) {
        this.$message.warning(data.status !== 20 ? '该订单当前状态不是待发货' : '该订单没有待发商品')
      }
    },
    async submitShip() {
      if (!this.pending || !this.pending.orderId) {
        this.$message.warning('请先载入订单明细')
        return
      }
      if (!this.shipForm.expressCompany || !this.shipForm.expressNo) {
        this.$message.warning('快递公司与单号必填')
        return
      }
      const items = this.pending.items.filter(i => Number(i.shipNow) > 0)
      if (!items.length) {
        this.$message.warning('本次发货数量都是 0')
        return
      }
      this.acting = true
      try {
        const res = await shipMallDelivery({
          orderId: this.pending.orderId,
          expressCompany: this.shipForm.expressCompany,
          expressNo: this.shipForm.expressNo,
          remark: this.shipForm.remark,
          items: items.map(i => ({ orderItemId: i.orderItemId, quantity: Number(i.shipNow) }))
        })
        const all = res.data && res.data.allShipped
        this.$message.success(all ? '已全部发货，订单转已发货' : '已部分发货，订单仍为待发货')
        this.shipVisible = false
        this.fetchList()
      } finally {
        this.acting = false
      }
    },
    openBatch() {
      this.batchText = ''
      this.batchResult = null
      this.batchVisible = true
    },
    async submitBatch() {
      const rows = this.batchText
        .split('\n')
        .map(l => l.trim())
        .filter(Boolean)
        .map(line => {
          const [orderNo, expressCompany, expressNo] = line.split(',').map(s => (s || '').trim())
          return { orderNo, expressCompany, expressNo }
        })
      if (!rows.length) {
        this.$message.warning('请粘贴导入内容')
        return
      }
      this.acting = true
      try {
        const res = await batchMallDelivery(rows)
        this.batchResult = res.data
        this.fetchList()
      } finally {
        this.acting = false
      }
    },
    async openDetail(row) {
      const res = await getMallDeliveryDetail(row.id)
      this.detail = (res.data && res.data.delivery) || {}
      this.detailItems = (res.data && res.data.items) || []
      this.detailVisible = true
    }
  }
}
</script>

<style scoped>
.express-no {
  margin-left: 8px;
  color: #909399;
  font-family: Consolas, monospace;
}
.pending-box {
  margin-top: 6px;
  padding: 10px 12px;
  background: #fafbfc;
  border-radius: 4px;
}
.pending-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #606266;
}
.pending-head .addr {
  color: #909399;
}
.pending-tip,
.batch-tip {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
  line-height: 1.7;
}
.batch-result {
  margin-top: 12px;
}
.batch-summary {
  margin-bottom: 8px;
  font-size: 13px;
}
.batch-summary .ok {
  color: #67c23a;
}
.batch-summary .bad {
  color: #f56c6c;
}
.detail-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
}
.detail-head .muted {
  color: #909399;
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
