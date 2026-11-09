<template>
  <div class="app-container">
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">运费模板</h2>
        <p class="page-desc">按件 / 按重量 / 满额包邮三种计费方式；地区规则优先匹配，未命中回落模板默认规则。商品与店铺均可绑定模板，未绑定走全局默认。</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 个模板</span>
        <el-button v-hasPermi="['mall_mall_freight_template:edit']" type="primary" size="small" icon="el-icon-plus" @click="openForm()">新增模板</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="small" @submit.prevent>
        <el-form-item label="模板名称">
          <el-input v-model="query.keyword" placeholder="输入名称模糊查询" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="handleSearch">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
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
          <span class="title">模板列表</span>
          <span class="subtitle">共 {{ total }} 个</span>
        </div>
        <span class="hint">改默认模板后，未绑定模板的商品立即按新默认计费</span>
      </div></template>

      <el-table v-loading="loading" :data="list" class="data-table" stripe empty-text="暂无运费模板">
        <el-table-column prop="name" label="模板名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="计费方式" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="mini" :type="chargeTagType(row.chargeType)">{{ chargeText(row.chargeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="Number(row.isDefault) === 1" size="mini" type="danger">默认</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="mini" :type="Number(row.status) === 1 ? 'success' : 'info'">
              {{ Number(row.status) === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="创建时间" width="160" align="center">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="text" @click="openForm(row)">编辑</el-button>
            <el-button v-hasPermi="['mall_mall_freight_template:edit']" type="text" :disabled="Number(row.isDefault) === 1" @click="makeDefault(row)">设为默认</el-button>
            <el-button v-hasPermi="['mall_mall_freight_template:edit']" type="text" class="danger-text" :disabled="Number(row.isDefault) === 1" @click="remove(row)">删除</el-button>
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

    <el-dialog :title="form.id ? '编辑运费模板' : '新增运费模板'" v-model="formVisible" width="860px" top="8vh">
      <el-form :model="form" label-width="96px" size="small">
        <el-form-item label="模板名称" required>
          <el-input v-model="form.name" placeholder="如：全国默认（按件）" style="width: 320px" />
        </el-form-item>
        <el-form-item label="计费方式">
          <el-radio-group v-model="form.chargeType">
            <el-radio :label="1">按件</el-radio>
            <el-radio :label="2">按重量（克）</el-radio>
            <el-radio :label="3">满额包邮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="默认模板">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
          <span class="form-tip">设为默认后，其他模板的默认标记会被取消</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" />
        </el-form-item>
      </el-form>

      <div class="rules-head">
        <span class="rules-title">地区规则</span>
        <span class="hint">地区留空 = 默认规则；命中精确地区时优先使用该行</span>
        <el-button size="mini" icon="el-icon-plus" @click="addRule">新增规则</el-button>
      </div>
      <el-table :data="form.rules" size="mini" class="data-table" empty-text="至少添加一条规则">
        <el-table-column label="适用地区" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.regionCodes" size="mini" placeholder="如：新疆,西藏（空=默认）" />
          </template>
        </el-table-column>
        <el-table-column label="首件/首重" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.firstUnit" :min="1" size="mini" controls-position="right" style="width: 90px" />
          </template>
        </el-table-column>
        <el-table-column label="首费(元)" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.firstFeeYuan" :min="0" :step="1" :precision="2" size="mini" controls-position="right" style="width: 90px" />
          </template>
        </el-table-column>
        <el-table-column label="续件/续重" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.extraUnit" :min="1" size="mini" controls-position="right" style="width: 90px" />
          </template>
        </el-table-column>
        <el-table-column label="续费(元)" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.extraFeeYuan" :min="0" :precision="2" size="mini" controls-position="right" style="width: 90px" />
          </template>
        </el-table-column>
        <el-table-column label="满额包邮(元)" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.freeThresholdYuan" :min="0" :precision="2" size="mini" controls-position="right" style="width: 100px" />
          </template>
        </el-table-column>
        <el-table-column label="" width="60" align="center">
          <template #default="{ $index }">
            <el-button type="text" class="danger-text" @click="form.rules.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="calc-tip">
        计费示例（按件）：首 2 件 8 元、续 1 件 3 元 → 买 5 件 = 8 + 3×3 = <b>17 元</b>；
        按重量：首重 1000g 10 元、续 500g 4 元 → 2.8kg = 10 + 4×4 = <b>26 元</b>。
      </div>

      <template #footer><div>
        <el-button size="small" @click="formVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="acting" @click="submit">保存</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import {
  getMallFreightTemplatePage,
  getMallFreightTemplateDetail,
  addMallFreightTemplate,
  updateMallFreightTemplate,
  setDefaultMallFreightTemplate,
  delMallFreightTemplate
} from '@/api/mall_freight_template'

function emptyRule() {
  return {
    regionCodes: '',
    firstUnit: 1,
    firstFeeYuan: 8,
    extraUnit: 1,
    extraFeeYuan: 3,
    freeThresholdYuan: 0
  }
}

export default {
  name: 'MallFreightTemplate',
  data() {
    return {
      loading: false,
      acting: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10, keyword: '', status: undefined },
      formVisible: false,
      form: { id: null, name: '', chargeType: 1, isDefault: 0, status: 1, remark: '', rules: [] }
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    chargeText(type) {
      const n = Number(type)
      if (n === 2) return '按重量'
      if (n === 3) return '满额包邮'
      return '按件'
    },
    chargeTagType(type) {
      const n = Number(type)
      if (n === 2) return 'warning'
      if (n === 3) return 'success'
      return ''
    },
    fmt(v) {
      if (!v) return '—'
      return String(v).replace('T', ' ').slice(0, 19)
    },
    yuanToFen(v) {
      const n = Number(v || 0)
      return Math.round(n * 100)
    },
    fenToYuan(v) {
      const n = Number(v || 0)
      return n / 100
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
        const res = await getMallFreightTemplatePage({
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
    async openForm(row) {
      if (!row) {
        this.form = { id: null, name: '', chargeType: 1, isDefault: 0, status: 1, remark: '', rules: [emptyRule()] }
        this.formVisible = true
        return
      }
      try {
        const res = await getMallFreightTemplateDetail(row.id)
        const data = res.data || {}
        const tpl = data.template || {}
        this.form = {
          id: tpl.id,
          name: tpl.name,
          chargeType: Number(tpl.chargeType || 1),
          isDefault: Number(tpl.isDefault || 0),
          status: Number(tpl.status === undefined || tpl.status === null ? 1 : tpl.status),
          remark: tpl.remark,
          rules: (data.rules || []).map(r => ({
            regionCodes: r.regionCodes || '',
            firstUnit: Number(r.firstUnit || 1),
            firstFeeYuan: this.fenToYuan(r.firstFee),
            extraUnit: Number(r.extraUnit || 1),
            extraFeeYuan: this.fenToYuan(r.extraFee),
            freeThresholdYuan: this.fenToYuan(r.freeThreshold)
          }))
        }
        if (!this.form.rules.length) this.form.rules.push(emptyRule())
        this.formVisible = true
      } catch (e) {
        /* 错误提示已由 request 拦截器统一处理 */
      }
    },
    addRule() {
      this.form.rules.push(emptyRule())
    },
    async submit() {
      if (!this.form.name) {
        this.$message.warning('请填写模板名称')
        return
      }
      if (!this.form.rules.length) {
        this.$message.warning('至少添加一条地区规则')
        return
      }
      const payload = {
        id: this.form.id,
        name: this.form.name,
        chargeType: this.form.chargeType,
        isDefault: this.form.isDefault,
        status: this.form.status,
        remark: this.form.remark,
        rules: this.form.rules.map((r, i) => ({
          regionCodes: r.regionCodes || null,
          firstUnit: r.firstUnit,
          firstFee: this.yuanToFen(r.firstFeeYuan),
          extraUnit: r.extraUnit,
          extraFee: this.yuanToFen(r.extraFeeYuan),
          freeThreshold: this.yuanToFen(r.freeThresholdYuan),
          sort: i
        }))
      }
      this.acting = true
      try {
        if (this.form.id) {
          await updateMallFreightTemplate(payload)
        } else {
          await addMallFreightTemplate(payload)
        }
        this.$message.success('已保存')
        this.formVisible = false
        this.fetchList()
      } finally {
        this.acting = false
      }
    },
    async makeDefault(row) {
      await this.$confirm(`将「${row.name}」设为全局默认运费模板？`, '提示', { type: 'warning' })
      await setDefaultMallFreightTemplate(row.id)
      this.$message.success('已设为默认')
      this.fetchList()
    },
    async remove(row) {
      await this.$confirm(`删除模板「${row.name}」？其地区规则将一并删除`, '提示', { type: 'warning' })
      await delMallFreightTemplate(row.id)
      this.$message.success('已删除')
      this.fetchList()
    }
  }
}
</script>

<style scoped>
.form-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.rules-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 6px 0 10px;
}
.rules-title {
  font-weight: 600;
  color: #303133;
}
.rules-head .hint {
  flex: 1;
  color: #909399;
  font-size: 12px;
}
.calc-tip {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f4f8ff;
  border-radius: 4px;
  color: #606266;
  font-size: 12px;
  line-height: 1.7;
}
.danger-text {
  color: #f56c6c;
}
.muted {
  color: #c0c4cc;
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
