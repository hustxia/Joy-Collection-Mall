<template>
  <div class="app-container dashboard">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">经营看板</h2>
        <p class="page-desc">商城核心经营指标概览（金额单位：元，原始数据以「分」存储）</p>
      </div>
      <div class="page-head__meta">
        <el-radio-group v-model="range" size="small" @change="load">
          <el-radio-button label="today">今日</el-radio-button>
          <el-radio-button label="7d">近7天</el-radio-button>
          <el-radio-button label="30d">近30天</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 核心指标 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--gmv">
          <div class="stat-label">成交金额 GMV</div>
          <div class="stat-value">¥{{ yuan(data.gmv) }}</div>
          <div class="stat-sub">已支付订单实付合计</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--order">
          <div class="stat-label">已支付订单数</div>
          <div class="stat-value">{{ data.orderCount }}</div>
          <div class="stat-sub">统计区间内</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--avg">
          <div class="stat-label">客单价</div>
          <div class="stat-value">¥{{ yuan(data.avgOrderAmount) }}</div>
          <div class="stat-sub">GMV / 订单数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--refund">
          <div class="stat-label">退款金额</div>
          <div class="stat-value">¥{{ yuan(data.refundAmount) }}</div>
          <div class="stat-sub">已退款售后单合计</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 次要指标 + 售后率 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--todo">
          <div class="stat-label">待发货订单</div>
          <div class="stat-value">{{ data.pendingShipment }}</div>
          <div class="stat-sub">status=20 全量待办</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--todo">
          <div class="stat-label">待审核售后</div>
          <div class="stat-value">{{ data.pendingRefundAudit }}</div>
          <div class="stat-sub">待审核全量待办</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--member">
          <div class="stat-label">新增会员</div>
          <div class="stat-value">{{ data.newMembers }}</div>
          <div class="stat-sub">统计区间内注册</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--rate">
          <div class="stat-label">售后率</div>
          <div class="stat-value">{{ (data.refundRate * 100).toFixed(2) }}%</div>
          <div class="stat-sub">已退款单数 / 已支付订单数</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Top5 商品 -->
    <el-card shadow="never" class="table-card">
      <template #header><div class="card-header">
        <div class="card-title">
          <span class="title">Top5 商品（按成交量）</span>
          <span class="subtitle">统计区间内已支付订单</span>
        </div>
      </div></template>
      <el-table
        v-loading="loading"
        :data="data.topGoods || []"
        class="data-table"
        stripe
        header-cell-class-name="table-head-cell"
        empty-text="暂无数据"
      >
        <el-table-column label="排名" width="70" align="center">
          <template #default="{ $index }">
            <span class="rank-badge" :class="{ 'rank-badge--top': $index < 3 }">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="goodsName" label="商品名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="成交量" width="120" align="center">
          <template #default="{ row }">{{ row.quantity }}</template>
        </el-table-column>
        <el-table-column label="成交金额" width="160" align="right">
          <template #default="{ row }">¥{{ yuan(row.amount) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { getDashboardOverview } from '@/api/mall_dashboard'

export default {
  name: 'MallDashboard',
  data() {
    return {
      range: '30d',
      loading: false,
      data: {
        gmv: 0,
        orderCount: 0,
        avgOrderAmount: 0,
        refundAmount: 0,
        refundRate: 0,
        pendingShipment: 0,
        pendingRefundAudit: 0,
        newMembers: 0,
        topGoods: []
      }
    }
  },
  created() {
    this.load()
  },
  methods: {
    yuan(fen) {
      const v = Number(fen) || 0
      return (v / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    },
    load() {
      this.loading = true
      getDashboardOverview(this.range)
        .then(res => {
          this.data = Object.assign({
            gmv: 0, orderCount: 0, avgOrderAmount: 0, refundAmount: 0,
            refundRate: 0, pendingShipment: 0, pendingRefundAudit: 0,
            newMembers: 0, topGoods: []
          }, res.data || {})
        })
        .catch(() => {})
        .finally(() => { this.loading = false })
    }
  }
}
</script>

<style scoped>
.dashboard { padding-bottom: 24px; }
.stat-row { margin-bottom: 16px; }
.stat-card {
  border: none;
  border-radius: 10px;
  overflow: hidden;
}
.stat-card .stat-label { color: #8a94a6; font-size: 13px; }
.stat-card .stat-value {
  font-size: 28px; font-weight: 700; margin: 8px 0 4px;
  color: #1f2733; letter-spacing: .5px;
}
.stat-card .stat-sub { color: #aab2c0; font-size: 12px; }
.stat-card--gmv .stat-value { color: #e1251b; }
.stat-card--order .stat-value { color: #2f7ed8; }
.stat-card--avg .stat-value { color: #16a34a; }
.stat-card--refund .stat-value { color: #b9810a; }
.stat-card--todo .stat-value { color: #d9690f; }
.stat-card--member .stat-value { color: #7c3aed; }
.stat-card--rate .stat-value { color: #0e7490; }
.rank-badge {
  display: inline-flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border-radius: 50%;
  background: #eef1f6; color: #8a94a6; font-weight: 600; font-size: 13px;
}
.rank-badge--top { background: #e1251b; color: #fff; }
</style>
