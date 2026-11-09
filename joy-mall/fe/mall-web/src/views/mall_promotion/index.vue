<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="活动名称">
          <el-input v-model="query.keyword" placeholder="模糊搜索" clearable style="width:180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width:130px" @change="search">
            <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:110px" @change="search">
            <el-option label="进行中" :value="1" />
            <el-option label="已停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
          <el-button type="success" icon="el-icon-plus" @click="openCreate">新建活动</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt">
      <template #header><div>
        <span>促销活动</span>
        <span class="hint">活动先算、券后算、积分最后抵，顺序固定；叠加策略由系统配置（默认互斥取最优）</span>
      </div></template>
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无活动，点「新建活动」配置满减/折扣">
        <el-table-column prop="name" label="活动名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag size="mini">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规则" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ ruleText(row) }}</template>
        </el-table-column>
        <el-table-column label="时间窗" min-width="200">
          <template #default="{ row }">{{ short(row.startTime) }} ~ {{ short(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="mini" :type="Number(row.status) === 1 ? 'success' : 'info'">
              {{ Number(row.status) === 1 ? '进行中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="right">
          <template #default="{ row }">
            <el-button type="text" @click="openEdit(row)">编辑</el-button>
            <el-button type="text" @click="toggle(row)">{{ Number(row.status) === 1 ? '停用' : '启用' }}</el-button>
            <el-button type="text" style="color:#f56c6c" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" v-model:current-page="query.pageNum" :page-size="query.pageSize" @current-change="load" />
    </el-card>

    <el-dialog :title="form.id ? '编辑活动' : '新建活动'" v-model="visible" width="600px">
      <el-form ref="form" :model="form" label-width="100px" size="small">
        <el-form-item label="活动名称" required>
          <el-input v-model="form.name" placeholder="如 双十一满 300 减 50" />
        </el-form-item>
        <el-form-item label="活动类型" required>
          <el-select v-model="form.type" style="width:200px">
            <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="作用范围">
          <el-select v-model="form.scopeType" style="width:160px" @change="onScopeTypeChange">
            <el-option label="全场" :value="1" />
            <el-option label="类目" :value="2" />
            <el-option label="商品" :value="3" />
            <el-option label="店铺" :value="4" />
          </el-select>
          <el-select
            v-if="form.scopeType === 2"
            v-model="form.refIds"
            multiple filterable clearable
            placeholder="选择类目（可多选）"
            style="width:360px;margin-left:8px"
          >
            <el-option v-for="c in categoryOptions" :key="c.id" :label="c.label" :value="c.id" />
          </el-select>
          <el-select
            v-else-if="form.scopeType === 3"
            v-model="form.refIds"
            multiple filterable clearable
            placeholder="选择商品（可多选）"
            style="width:360px;margin-left:8px"
          >
            <el-option v-for="g in goodsOptions" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
          <el-select
            v-else-if="form.scopeType === 4"
            v-model="form.refIds"
            multiple filterable clearable
            placeholder="选择店铺（可多选）"
            style="width:360px;margin-left:8px"
          >
            <el-option v-for="m in merchantOptions" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
          <span v-else class="hint">全场生效，无需选择</span>
        </el-form-item>

        <template v-if="form.type === 1">
          <el-form-item label="门槛金额"><el-input v-model="rule.thresholdYuan" style="width:140px"><template #append>元</template></el-input></el-form-item>
          <el-form-item label="减免金额"><el-input v-model="rule.discountYuan" style="width:140px"><template #append>元</template></el-input></el-form-item>
        </template>
        <template v-else-if="form.type === 2">
          <el-form-item label="门槛金额"><el-input v-model="rule.thresholdYuan" style="width:140px"><template #append>元</template></el-input></el-form-item>
          <el-form-item label="折扣"><el-input v-model="rule.discountRate" placeholder="如 90 表示 9 折" style="width:140px"><template #append>%</template></el-input></el-form-item>
        </template>
        <template v-else-if="form.type === 3">
          <el-form-item label="折扣"><el-input v-model="rule.discountRate" placeholder="如 85 表示 8.5 折" style="width:140px"><template #append>%</template></el-input></el-form-item>
        </template>
        <template v-else-if="form.type === 5">
          <el-form-item label="件数"><el-input v-model="rule.bundleQty" style="width:120px" /></el-form-item>
          <el-form-item label="打包价"><el-input v-model="rule.bundlePriceYuan" style="width:140px"><template #append>元</template></el-input></el-form-item>
        </template>
        <template v-else-if="form.type === 6">
          <el-form-item label="加价购价"><el-input v-model="rule.addOnPriceYuan" style="width:140px"><template #append>元</template></el-input></el-form-item>
        </template>

        <el-form-item label="时间窗">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="开始" value-format="yyyy-MM-dd HH:mm:ss" style="width:200px" />
          <span class="dash">~</span>
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="结束" value-format="yyyy-MM-dd HH:mm:ss" style="width:200px" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" :max="999" size="small" />
          <span class="hint">数字越小越优先（互斥取最优时按优先级顺序比较）</span>
        </el-form-item>
        <el-form-item label="可叠加">
          <el-switch v-model="form.stackableBool" />
          <span class="hint">关闭后本次活动与其他活动互斥，只取最优</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer><span>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </span></template>
    </el-dialog>
  </div>
</template>

<script>
import { pagePromotion, savePromotion, promotionStatus, removePromotion, getPromotion } from '@/api/mall_promotion'
import { getMallCategoryTree } from '@/api/mall_category'
import { getMallMerchantOptions } from '@/api/mall_merchant'
import { getMallGoodsList } from '@/api/mall_goods'
import { fenToYuan, yuanToFen } from '@/utils/format'

export default {
  name: 'MallPromotion',
  data() {
    return {
      types: [
        { value: 1, label: '满减' },
        { value: 2, label: '满折' },
        { value: 3, label: '限时折扣' },
        { value: 4, label: '第二件半价' },
        { value: 5, label: '套装' },
        { value: 6, label: '加价购' }
      ],
      query: { keyword: '', type: null, status: null, pageNum: 1, pageSize: 10 },
      list: [],
      total: 0,
      loading: false,
      visible: false,
      saving: false,
      form: {},
      rule: { thresholdYuan: '', discountYuan: '', discountRate: 90, bundleQty: 2, bundlePriceYuan: '', addOnPriceYuan: '' },
      categoryOptions: [],
      goodsOptions: [],
      merchantOptions: []
    }
  },
  created() {
    this.load()
    this.loadOptions()
  },
  methods: {
    async loadOptions() {
      const [cats, goods, merchants] = await Promise.all([
        getMallCategoryTree(),
        getMallGoodsList({ page: 1, size: 200 }),
        getMallMerchantOptions()
      ])
      this.categoryOptions = this.flattenCats((cats.data || []).map(c => ({ ...c, level: 1 })))
      this.goodsOptions = (goods.data && goods.data.list) || []
      this.merchantOptions = (merchants.data || []).map(m => ({ id: m.id, name: m.name || m.merchantName }))
    },
    flattenCats(list, out = []) {
      (list || []).forEach(c => {
        out.push({ id: c.id, label: (c.level > 1 ? '　'.repeat(c.level - 1) + '└ ' : '') + c.name })
        if (c.children && c.children.length) {
          this.flattenCats(c.children.map(x => ({ ...x, level: (c.level || 1) + 1 })), out)
        }
      })
      return out
    },
    onScopeTypeChange() {
      this.form.refIds = []
    },
    short(t) {
      return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
    },
    typeText(t) {
      return (this.types.find(x => x.value === Number(t)) || {}).label || '活动'
    },
    ruleText(row) {
      // 列表可能只回 ruleJson（不解析），这里兜底解析一次，避免规则列全是 0
      let r = row.rule
      if (!r) {
        try { r = JSON.parse(row.ruleJson || '{}') } catch (e) { r = {} }
      }
      switch (Number(row.type)) {
        case 1: return `满${fenToYuan(r.threshold)}减${fenToYuan(r.discount)}`
        case 2: return `满${fenToYuan(r.threshold)}享${(r.discountRate || 100) / 10}折`
        case 3: return `限时${(r.discountRate || 100) / 10}折`
        case 4: return '第二件半价'
        case 5: return `${r.bundleQty || 0} 件 ${fenToYuan(r.bundlePrice)} 元`
        case 6: return `加价购 ${fenToYuan(r.addOnPrice)} 元`
        default: return '—'
      }
    },
    async load() {
      this.loading = true
      try {
        const res = await pagePromotion(this.query)
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
      this.form = { type: 1, scopeType: 1, refIds: [], priority: 100, stackableBool: false, startTime: '', endTime: '' }
      this.rule = { thresholdYuan: '', discountYuan: '', discountRate: 90, bundleQty: 2, bundlePriceYuan: '', addOnPriceYuan: '' }
      this.visible = true
    },
    async openEdit(row) {
      const res = await getPromotion(row.id)
      const d = res.data || {}
      this.form = { ...d, stackableBool: Number(d.stackable) === 1, refIds: (d.refIds || []).slice() }
      const r = d.rule || {}
      this.rule = {
        thresholdYuan: r.threshold ? fenToYuan(r.threshold) : '',
        discountYuan: r.discount ? fenToYuan(r.discount) : '',
        discountRate: r.discountRate || 90,
        bundleQty: r.bundleQty || 2,
        bundlePriceYuan: r.bundlePrice ? fenToYuan(r.bundlePrice) : '',
        addOnPriceYuan: r.addOnPrice ? fenToYuan(r.addOnPrice) : ''
      }
      this.visible = true
    },
    async submit() {
      if (!this.form.name) {
        this.$message.warning('请填写活动名称')
        return
      }
      const payload = {
        id: this.form.id,
        name: this.form.name,
        type: this.form.type,
        scopeType: this.form.scopeType,
        threshold: yuanToFen(this.rule.thresholdYuan),
        discount: yuanToFen(this.rule.discountYuan),
        discountRate: Number(this.rule.discountRate) || 100,
        bundleQty: Number(this.rule.bundleQty) || 0,
        bundlePrice: yuanToFen(this.rule.bundlePriceYuan),
        addOnPrice: yuanToFen(this.rule.addOnPriceYuan),
        startTime: this.form.startTime,
        endTime: this.form.endTime,
        priority: this.form.priority,
        stackable: this.form.stackableBool ? 1 : 0,
        status: 1,
        remark: this.form.remark,
        refIds: this.form.refIds || []
      }
      this.saving = true
      try {
        await savePromotion(payload)
        this.$message.success('已保存')
        this.visible = false
        this.load()
      } finally {
        this.saving = false
      }
    },
    async toggle(row) {
      await promotionStatus(row.id, Number(row.status) === 1 ? 0 : 1)
      this.$message.success('状态已更新')
      this.load()
    },
    async remove(row) {
      await this.$confirm(`删除活动「${row.name}」？已产生的命中记录会保留。`, '删除确认', { type: 'warning' })
      await removePromotion(row.id)
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
