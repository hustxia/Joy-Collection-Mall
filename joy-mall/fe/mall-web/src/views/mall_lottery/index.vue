<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="活动名称">
          <el-input v-model="query.name" placeholder="模糊搜索" clearable style="width:180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px" @change="search">
            <el-option label="未开始" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已结束" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
          <el-button type="success" icon="el-icon-plus" @click="openCreate">新建抽奖</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt">
      <template #header><div>
        <span>抽奖活动</span>
        <span class="hint">每日限次 + 消耗积分（0=免费）；奖品池权重随机，库存 -1 不限量</span>
      </div></template>
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无抽奖活动">
        <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
        <el-table-column prop="name" label="活动名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="dailyLimit" label="每日限次" width="90" align="center" />
        <el-table-column prop="consumePoints" label="消耗积分/次" width="110" align="center" />
        <el-table-column prop="prizeCount" label="奖品数" width="80" align="center" />
        <el-table-column label="时间窗" min-width="200">
          <template #default="{ row }">{{ short(row.startTime) }} ~ {{ short(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="mini" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="right">
          <template #default="{ row }">
            <el-button type="text" @click="openEdit(row)">编辑</el-button>
            <el-button type="text" @click="openPrizes(row)">奖品池</el-button>
            <el-button type="text" style="color:#f56c6c" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" v-model:current-page="query.pageNum" :page-size="query.pageSize" @current-change="load" />
    </el-card>

    <!-- 新建/编辑抽奖活动 -->
    <el-dialog :title="form.id ? '编辑抽奖活动' : '新建抽奖活动'" v-model="visible" width="600px">
      <el-form ref="form" :model="form" label-width="110px" size="small">
        <el-form-item label="活动名称" required>
          <el-input v-model="form.name" placeholder="如 五一幸运大转盘" />
        </el-form-item>
        <el-form-item label="每日限次" required>
          <el-input-number v-model="form.dailyLimit" :min="1" size="small" />
        </el-form-item>
        <el-form-item label="消耗积分/次">
          <el-input-number v-model="form.consumePoints" :min="0" size="small" />
          <span class="hint">0 = 免费抽</span>
        </el-form-item>
        <el-form-item label="时间窗" required>
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="开始" value-format="yyyy-MM-dd HH:mm:ss" style="width:200px" />
          <span class="dash">~</span>
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="结束" value-format="yyyy-MM-dd HH:mm:ss" style="width:200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:160px">
            <el-option label="未开始" :value="0" />
            <el-option label="进行中" :value="1" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><span>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </span></template>
    </el-dialog>

    <!-- 奖品池整池编辑 -->
    <el-dialog :title="`奖品池 — ${prizeActivityName}`" v-model="prizeVisible" width="900px">
      <el-button type="primary" size="small" icon="el-icon-plus" class="mb" @click="addPrize">添加奖品</el-button>
      <el-table :data="prizes" stripe size="small" empty-text="暂无奖品，点「添加奖品」配置">
        <el-table-column label="奖品类型" width="130">
          <template #default="{ row }">
            <el-select v-model="row.prizeType" size="mini" @change="row.refId = ''">
              <el-option v-for="t in prizeTypes" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="奖品名称" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.name" size="mini" placeholder="名称" />
          </template>
        </el-table-column>
        <el-table-column label="关联" width="190">
          <template #default="{ row }">
            <el-select v-if="row.prizeType === 1" v-model="row.refId" size="mini" filterable placeholder="选择优惠券" style="width:100%">
              <el-option v-for="c in couponOptions" :key="c.id" :label="c.name" :value="String(c.id)" />
            </el-select>
            <el-select v-else-if="row.prizeType === 2" v-model="row.refId" size="mini" filterable placeholder="选择商品" style="width:100%">
              <el-option v-for="g in goodsOptions" :key="g.id" :label="g.name" :value="String(g.id)" />
            </el-select>
            <el-input v-else-if="row.prizeType === 0" v-model="row.refId" size="mini" placeholder="积分分值" />
            <span v-else class="hint">—</span>
          </template>
        </el-table-column>
        <el-table-column label="权重" width="90">
          <template #default="{ row }">
            <el-input-number v-model="row.weight" :min="0" size="mini" controls-position="right" style="width:80px" />
          </template>
        </el-table-column>
        <el-table-column label="库存" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.stock" :min="-1" size="mini" controls-position="right" style="width:110px" />
            <div class="hint">-1 不限量</div>
          </template>
        </el-table-column>
        <el-table-column label="顺序" width="90">
          <template #default="{ row }">
            <el-input-number v-model="row.sort" :min="0" size="mini" controls-position="right" style="width:80px" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row, $index }">
            <el-button type="text" style="color:#f56c6c" @click="prizes.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><span>
        <el-button @click="prizeVisible = false">取消</el-button>
        <el-button type="primary" :loading="prizeSaving" @click="submitPrizes">整池保存</el-button>
      </span></template>
    </el-dialog>
  </div>
</template>

<script>
import { pageLottery, getLottery, saveLottery, removeLottery, saveLotteryPrizes } from '@/api/mall_lottery'
import { getMallCouponList } from '@/api/mall_coupon'
import { getMallGoodsList } from '@/api/mall_goods'

export default {
  name: 'MallLottery',
  data() {
    return {
      prizeTypes: [
        { value: 0, label: '积分' },
        { value: 1, label: '优惠券' },
        { value: 2, label: '实物' },
        { value: 3, label: '谢谢参与' }
      ],
      query: { name: '', status: null, pageNum: 1, pageSize: 10 },
      list: [],
      total: 0,
      loading: false,
      visible: false,
      saving: false,
      form: {},
      prizeVisible: false,
      prizeSaving: false,
      prizeActivityId: '',
      prizeActivityName: '',
      prizes: [],
      couponOptions: [],
      goodsOptions: []
    }
  },
  created() {
    this.load()
    this.loadOptions()
  },
  methods: {
    async loadOptions() {
      const [coupons, goods] = await Promise.all([
        getMallCouponList({ page: 1, size: 200 }),
        getMallGoodsList({ page: 1, size: 200 })
      ])
      this.couponOptions = (coupons.data && coupons.data.list) || []
      this.goodsOptions = (goods.data && goods.data.list) || []
    },
    short(t) {
      return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
    },
    statusText(s) {
      return { 0: '未开始', 1: '进行中', 2: '已结束' }[Number(s)] || '未知'
    },
    statusTag(s) {
      return { 0: 'info', 1: 'success', 2: 'danger' }[Number(s)] || 'info'
    },
    async load() {
      this.loading = true
      try {
        const res = await pageLottery({
          pageNum: this.query.pageNum,
          pageSize: this.query.pageSize,
          name: this.query.name || null,
          status: this.query.status === '' ? null : this.query.status
        })
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
    openCreate() {
      this.form = { name: '', dailyLimit: 3, consumePoints: 0, startTime: '', endTime: '', status: 0 }
      this.visible = true
    },
    async openEdit(row) {
      const res = await getLottery(row.id)
      const d = res.data || {}
      const a = d.activity || {}
      this.form = {
        id: a.id,
        name: a.name,
        dailyLimit: a.dailyLimit || 1,
        consumePoints: a.consumePoints || 0,
        startTime: a.startTime,
        endTime: a.endTime,
        status: Number(a.status) === 2 ? 1 : Number(a.status)
      }
      this.visible = true
    },
    async submit() {
      if (!this.form.name) {
        this.$message.warning('请填写活动名称')
        return
      }
      if (!this.form.startTime || !this.form.endTime) {
        this.$message.warning('请选择活动时间窗')
        return
      }
      this.saving = true
      try {
        await saveLottery({
          id: this.form.id || undefined,
          name: this.form.name,
          dailyLimit: this.form.dailyLimit,
          consumePoints: this.form.consumePoints,
          startTime: this.form.startTime,
          endTime: this.form.endTime,
          status: this.form.status
        })
        this.$message.success('已保存')
        this.visible = false
        this.load()
      } finally {
        this.saving = false
      }
    },
    async openPrizes(row) {
      this.prizeActivityId = row.id
      this.prizeActivityName = row.name || ('活动 ' + row.id)
      const res = await getLottery(row.id)
      const d = res.data || {}
      this.prizes = (d.prizes || []).map(p => ({
        prizeType: Number(p.prizeType),
        name: p.name,
        refId: p.refId == null ? '' : String(p.refId),
        weight: p.weight || 0,
        stock: p.stock == null ? -1 : Number(p.stock),
        sort: p.sort || 0
      }))
      this.prizeVisible = true
    },
    addPrize() {
      this.prizes.push({ prizeType: 0, name: '', refId: '', weight: 10, stock: -1, sort: this.prizes.length })
    },
    async submitPrizes() {
      if (!this.prizes.length) {
        this.$message.warning('奖品列表不能为空')
        return
      }
      for (const p of this.prizes) {
        if (!p.name) {
          this.$message.warning('请填写所有奖品名称')
          return
        }
        if (p.prizeType !== 3 && (p.refId === '' || p.refId == null)) {
          this.$message.warning(`「${p.name}」需配置关联ID（积分=分值/优惠券=券ID/实物=商品ID）`)
          return
        }
      }
      const payload = this.prizes.map(p => ({
        prizeType: p.prizeType,
        name: p.name,
        refId: p.prizeType === 3 ? null : p.refId,
        weight: p.weight,
        stock: p.stock,
        sort: p.sort
      }))
      this.prizeSaving = true
      try {
        await saveLotteryPrizes(this.prizeActivityId, payload)
        this.$message.success('奖品池已保存')
        this.prizeVisible = false
        this.load()
      } finally {
        this.prizeSaving = false
      }
    },
    async remove(row) {
      await this.$confirm(`删除抽奖活动「${row.name}」？奖品池会一并删除。`, '删除确认', { type: 'warning' })
      await removeLottery(row.id)
      this.$message.success('已删除')
      this.load()
    }
  }
}
</script>

<style scoped>
.mt { margin-top: 14px; }
.mb { margin-bottom: 10px; }
.hint { color: #a8abb2; font-size: 12px; margin-left: 8px; }
.dash { margin: 0 6px; color: #c0c4cc; }
.pager { margin-top: 12px; text-align: right; }
</style>
