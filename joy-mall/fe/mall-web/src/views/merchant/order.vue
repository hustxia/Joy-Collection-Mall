<template>
  <div>
    <el-card shadow="never">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="订单号 / 收货人" clearable style="width:220px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:130px" @change="search">
            <el-option label="待发货" :value="20" />
            <el-option label="已发货" :value="30" />
            <el-option label="已完成" :value="40" />
            <el-option label="已取消" :value="50" />
            <el-option label="已关闭" :value="60" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt">
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无订单">
        <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="实付" width="110">
          <template #default="{ row }"><span class="price">¥{{ fenToYuan(row.payAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="收货人" min-width="150">
          <template #default="{ row }">{{ row.receiverName }}<span class="muted"> {{ row.receiverPhone }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="mini" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="170" />
        <el-table-column label="操作" width="170" align="right">
          <template #default="{ row }">
            <el-button type="text" @click="openDetail(row)">明细</el-button>
            <el-button v-if="canShip(row)" type="text" style="color:#e6a23c" @click="openShip(row)">发货</el-button>
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

    <!-- 发货 -->
    <el-dialog title="订单发货" v-model="shipVisible" width="680px">
      <p class="dialog-tip">
        可只发一部分：改动「本次发货」数量即可，剩余数量可下次再发；全部发完订单才会转为「已发货」。
      </p>
      <el-table :data="shipItems" size="mini">
        <el-table-column prop="goodsName" label="商品" min-width="200" show-overflow-tooltip />
        <el-table-column prop="specJson" label="规格" width="130" />
        <el-table-column label="已发/总数" width="100">
          <template #default="{ row }">{{ row.shippedQuantity || 0 }} / {{ row.quantity }}</template>
        </el-table-column>
        <el-table-column label="本次发货" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row._ship" :min="0" :max="remain(row)" size="mini" controls-position="right" style="width:120px" />
          </template>
        </el-table-column>
      </el-table>
      <el-form :inline="true" size="small" class="mt">
        <el-form-item label="快递公司">
          <el-input v-model="shipForm.expressCompany" placeholder="如 顺丰速运" style="width:160px" />
        </el-form-item>
        <el-form-item label="快递单号">
          <el-input v-model="shipForm.expressNo" placeholder="全局唯一" style="width:200px" />
        </el-form-item>
      </el-form>
      <template #footer><span>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" :loading="shipping" @click="submitShip">确认发货</el-button>
      </span></template>
    </el-dialog>

    <!-- 明细 -->
    <el-dialog title="订单明细" v-model="detailVisible" width="680px">
      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="实付">¥{{ fenToYuan(detail.payAmount) }}</el-descriptions-item>
        <el-descriptions-item label="运费">¥{{ fenToYuan(detail.freightAmount) }}</el-descriptions-item>
        <el-descriptions-item label="收货人">{{ detail.receiverName }} {{ detail.receiverPhone }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ detail.receiverAddress }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.items || []" size="mini" class="mt">
        <el-table-column prop="goodsName" label="商品" min-width="200" />
        <el-table-column prop="specJson" label="规格" width="130" />
        <el-table-column label="单价" width="100">
          <template #default="{ row }">¥{{ fenToYuan(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="70" />
        <el-table-column label="已发" width="70">
          <template #default="{ row }">{{ row.shippedQuantity || 0 }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import { orderPage, orderDetail, deliver } from '@/api/merchant'
import { fenToYuan } from '@/utils/format'

export default {
  name: 'MerchantOrder',
  data() {
    return {
      query: { keyword: '', status: 20, pageNum: 1, pageSize: 10 },
      list: [],
      total: 0,
      loading: false,
      detailVisible: false,
      detail: {},
      shipVisible: false,
      shipItems: [],
      shipping: false,
      shipForm: { expressCompany: '', expressNo: '' },
      current: {}
    }
  },
  created() {
    this.load()
  },
  methods: {
    fenToYuan,
    statusText(s) {
      return { 10: '待付款', 20: '待发货', 30: '已发货', 40: '已完成', 50: '已取消', 60: '已关闭' }[Number(s)] || '未知'
    },
    statusTag(s) {
      return { 20: 'warning', 30: 'primary', 40: 'success', 50: 'info', 60: 'info' }[Number(s)] || ''
    },
    canShip(row) {
      return Number(row.status) === 20
    },
    remain(row) {
      return Math.max(Number(row.quantity || 0) - Number(row.shippedQuantity || 0), 0)
    },
    async load() {
      this.loading = true
      try {
        const res = await orderPage(this.query)
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
    async openDetail(row) {
      const res = await orderDetail(row.id)
      this.detail = res.data || {}
      this.detailVisible = true
    },
    async openShip(row) {
      this.current = row
      const res = await orderDetail(row.id)
      const d = res.data || {}
      // 默认「全部剩余发出」，运营只想发一部分时再改数字
      this.shipItems = (d.items || []).map(i => ({ ...i, _ship: Math.max(Number(i.quantity || 0) - Number(i.shippedQuantity || 0), 0) }))
      this.shipForm = { expressCompany: '', expressNo: '' }
      this.shipVisible = true
    },
    async submitShip() {
      if (!this.shipForm.expressCompany || !this.shipForm.expressNo) {
        this.$message.warning('请填写快递公司与单号')
        return
      }
      // 全部发完时不传 items：交给服务端「发剩余」语义，避免前端算错数量导致超发
      const allFull = this.shipItems.every(i => Number(i._ship) === this.remain(i))
      const items = allFull
        ? undefined
        : this.shipItems.filter(i => Number(i._ship) > 0).map(i => ({ orderItemId: i.id, quantity: Number(i._ship) }))
      if (!allFull && (!items || items.length === 0)) {
        this.$message.warning('请至少填写一件发货数量')
        return
      }
      this.shipping = true
      try {
        const res = await deliver(this.current.id, {
          expressCompany: this.shipForm.expressCompany,
          expressNo: this.shipForm.expressNo,
          items
        })
        const d = res.data || {}
        this.$message.success(d.allShipped === false ? '已发部分商品，订单仍为待发货' : '发货完成，订单已转已发货')
        this.shipVisible = false
        this.load()
      } finally {
        this.shipping = false
      }
    }
  }
}
</script>

<style scoped>
.mt { margin-top: 14px; }
.price { color: #e1251b; font-weight: 600; }
.muted { color: #a8abb2; }
.pager { margin-top: 12px; text-align: right; }
.dialog-tip { margin: 0 0 10px; color: #909399; font-size: 12px; }
</style>
