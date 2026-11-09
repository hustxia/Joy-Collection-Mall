<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="商品">
          <el-select v-model="query.goodsId" placeholder="全部" filterable clearable style="width:220px" @change="search">
            <el-option v-for="g in goodsOptions" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
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
          <el-button type="success" icon="el-icon-plus" @click="openCreate">新建拼团</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt">
      <template #header><div>
        <span>拼团活动</span>
        <span class="hint">开团/参团即按拼团价建单（order_type=2），团状态机：拼团中 → 已成团/已失败</span>
      </div></template>
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无拼团活动">
        <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
        <el-table-column prop="goodsName" label="商品" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.goodsName || ('商品 ' + row.goodsId) }}</template>
        </el-table-column>
        <el-table-column label="原价" width="100" align="right">
          <template #default="{ row }">¥{{ fenToYuan(row.originPrice) }}</template>
        </el-table-column>
        <el-table-column label="拼团价" width="100" align="right">
          <template #default="{ row }"><span style="color:#f56c6c">¥{{ fenToYuan(row.groupPrice) }}</span></template>
        </el-table-column>
        <el-table-column prop="groupSize" label="成团人数" width="90" align="center" />
        <el-table-column label="成团时限" width="90" align="center">
          <template #default="{ row }">{{ row.validHours }}h</template>
        </el-table-column>
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
            <el-button type="text" @click="openTeams(row)">团明细</el-button>
            <el-button type="text" style="color:#f56c6c" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" v-model:current-page="query.pageNum" :page-size="query.pageSize" @current-change="load" />
    </el-card>

    <!-- 新建/编辑拼团活动 -->
    <el-dialog :title="form.id ? '编辑拼团活动' : '新建拼团活动'" v-model="visible" width="600px">
      <el-form ref="form" :model="form" label-width="100px" size="small">
        <el-form-item label="商品" required>
          <el-select v-model="form.goodsId" filterable clearable placeholder="选择商品" style="width:100%" @change="onGoodsChange">
            <el-option v-for="g in goodsOptions" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="SKU" required>
          <el-select v-model="form.skuId" filterable clearable placeholder="先选商品，再选规格" style="width:100%" @change="onSkuChange">
            <el-option v-for="s in skuOptions" :key="s.id" :label="skuLabel(s)" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="原价" required>
          <el-input v-model="form.originPriceYuan" style="width:160px"><template #append>元</template></el-input>
        </el-form-item>
        <el-form-item label="拼团价" required>
          <el-input v-model="form.groupPriceYuan" style="width:160px"><template #append>元</template></el-input>
        </el-form-item>
        <el-form-item label="成团人数" required>
          <el-input-number v-model="form.groupSize" :min="2" size="small" />
        </el-form-item>
        <el-form-item label="成团时限" required>
          <el-input-number v-model="form.validHours" :min="1" size="small" />
          <span class="hint">小时；超时未成团自动失败退款</span>
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

    <!-- 团台账明细 -->
    <el-dialog :title="`团台账 — ${teamActivityName}`" v-model="teamVisible" width="820px">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="团状态">
          <el-select v-model="teamQuery.status" placeholder="全部" clearable style="width:120px" @change="loadTeams">
            <el-option label="拼团中" :value="0" />
            <el-option label="已成团" :value="1" />
            <el-option label="已失败" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="loadTeams">查询</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="teamLoading" :data="teamList" stripe empty-text="暂无团记录" size="small">
        <el-table-column prop="id" label="团ID" width="180" show-overflow-tooltip />
        <el-table-column prop="leaderId" label="团长会员ID" width="180" show-overflow-tooltip />
        <el-table-column label="人数(当前/成团)" width="130" align="center">
          <template #default="{ row }">{{ row.currentSize }} / {{ row.groupSize }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="mini" :type="teamTag(row.status)">{{ teamText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成团截止" min-width="160">
          <template #default="{ row }">{{ short(row.expireTime) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">{{ short(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="teamTotal" v-model:current-page="teamQuery.pageNum" :page-size="teamQuery.pageSize" @current-change="loadTeams" />
    </el-dialog>
  </div>
</template>

<script>
import { pageGroup, getGroup, saveGroup, removeGroup, pageGroupTeam } from '@/api/mall_group'
import { getMallGoodsList } from '@/api/mall_goods'
import { getMallSkuList } from '@/api/mall_sku'
import { fenToYuan, yuanToFen } from '@/utils/format'

export default {
  name: 'MallGroup',
  data() {
    return {
      query: { goodsId: '', status: null, pageNum: 1, pageSize: 10 },
      list: [],
      total: 0,
      loading: false,
      visible: false,
      saving: false,
      form: {},
      teamVisible: false,
      teamLoading: false,
      teamActivityId: '',
      teamActivityName: '',
      teamQuery: { pageNum: 1, pageSize: 10, status: null },
      teamList: [],
      teamTotal: 0,
      goodsOptions: [],
      allSkus: [],
      skuOptions: []
    }
  },
  created() {
    this.load()
    this.loadOptions()
  },
  methods: {
    fenToYuan,
    skuLabel(s) {
      let spec = ''
      try {
        const arr = JSON.parse(s.specJson || '[]')
        spec = (Array.isArray(arr) ? arr : []).map(x => (typeof x === 'string' ? x : [x.k, x.v].filter(Boolean).join(': '))).join(' / ')
      } catch (e) { spec = '' }
      return [s.skuCode || 'SKU', spec, s.price != null ? '¥' + fenToYuan(s.price) : '', s.stock != null ? '库存 ' + s.stock : ''].filter(Boolean).join(' | ')
    },
    async loadOptions() {
      const [g, s] = await Promise.all([
        getMallGoodsList({ page: 1, size: 200 }),
        getMallSkuList({ page: 1, size: 500 })
      ])
      this.goodsOptions = (g.data && g.data.list) || []
      this.allSkus = (s.data && s.data.list) || []
    },
    onGoodsChange() {
      this.form.skuId = ''
      this.skuOptions = this.allSkus.filter(s => String(s.goodsId) === String(this.form.goodsId))
    },
    onSkuChange() {
      const sku = this.allSkus.find(s => String(s.id) === String(this.form.skuId))
      if (sku && sku.price != null) {
        this.form.originPriceYuan = fenToYuan(sku.price)
      }
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
    teamText(s) {
      return { 0: '拼团中', 1: '已成团', 2: '已失败' }[Number(s)] || '未知'
    },
    teamTag(s) {
      return { 0: 'warning', 1: 'success', 2: 'danger' }[Number(s)] || 'info'
    },
    async load() {
      this.loading = true
      try {
        const res = await pageGroup({
          pageNum: this.query.pageNum,
          pageSize: this.query.pageSize,
          status: this.query.status === '' ? null : this.query.status,
          goodsId: this.query.goodsId || null
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
      this.form = { goodsId: '', skuId: '', originPriceYuan: '', groupPriceYuan: '', groupSize: 2, validHours: 24, startTime: '', endTime: '', status: 0 }
      this.skuOptions = []
      this.visible = true
    },
    async openEdit(row) {
      const res = await getGroup(row.id)
      const d = res.data || {}
      this.form = {
        id: d.id,
        goodsId: d.goodsId,
        skuId: d.skuId,
        originPriceYuan: d.originPrice != null ? fenToYuan(d.originPrice) : '',
        groupPriceYuan: d.groupPrice != null ? fenToYuan(d.groupPrice) : '',
        groupSize: d.groupSize || 2,
        validHours: d.validHours || 24,
        startTime: d.startTime,
        endTime: d.endTime,
        status: Number(d.status) === 2 ? 1 : Number(d.status)
      }
      this.skuOptions = this.allSkus.filter(s => String(s.goodsId) === String(d.goodsId))
      this.visible = true
    },
    async submit() {
      if (!this.form.goodsId || !this.form.skuId) {
        this.$message.warning('请选择商品与SKU')
        return
      }
      if (!this.form.startTime || !this.form.endTime) {
        this.$message.warning('请选择活动时间窗')
        return
      }
      const payload = {
        id: this.form.id || undefined,
        goodsId: this.form.goodsId,
        skuId: this.form.skuId,
        originPrice: yuanToFen(this.form.originPriceYuan),
        groupPrice: yuanToFen(this.form.groupPriceYuan),
        groupSize: this.form.groupSize,
        validHours: this.form.validHours,
        startTime: this.form.startTime,
        endTime: this.form.endTime,
        status: this.form.status
      }
      if (payload.groupPrice > payload.originPrice) {
        this.$message.warning('拼团价不能高于原价')
        return
      }
      this.saving = true
      try {
        await saveGroup(payload)
        this.$message.success('已保存')
        this.visible = false
        this.load()
      } finally {
        this.saving = false
      }
    },
    openTeams(row) {
      this.teamActivityId = row.id
      this.teamActivityName = row.goodsName || ('活动 ' + row.id)
      this.teamQuery = { pageNum: 1, pageSize: 10, status: null }
      this.teamVisible = true
      this.loadTeams()
    },
    async loadTeams() {
      this.teamLoading = true
      try {
        const res = await pageGroupTeam({
          pageNum: this.teamQuery.pageNum,
          pageSize: this.teamQuery.pageSize,
          activityId: this.teamActivityId,
          status: this.teamQuery.status === '' ? null : this.teamQuery.status
        })
        const d = res.data || {}
        this.teamList = d.records || []
        this.teamTotal = d.total || 0
      } finally {
        this.teamLoading = false
      }
    },
    async remove(row) {
      await this.$confirm(`删除拼团活动「${row.goodsName || row.goodsId}」？已开的团记录保留。`, '删除确认', { type: 'warning' })
      await removeGroup(row.id)
      this.$message.success('已删除')
      this.load()
    }
  }
}
</script>

<style scoped>
.mt { margin-top: 14px; }
.hint { color: #a8abb2; font-size: 12px; margin-left: 8px; }
.dash { margin: 0 6px; color: #c0c4cc; }
.pager { margin-top: 12px; text-align: right; }
</style>
