<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>
        <span>会员等级与权益</span>
        <el-button size="mini" type="primary" icon="el-icon-plus" style="float:right" @click="openCreate">新增等级</el-button>
      </div></template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="成长值达阈值自动升级；折扣率在下单试算时生效（活动之后、券之前），包邮次数用完回落到运费模板。"
      />
      <el-table v-loading="loading" :data="list" stripe class="mt" empty-text="暂无等级">
        <el-table-column prop="levelNo" label="等级序号" width="90" />
        <el-table-column prop="name" label="等级名称" min-width="130" />
        <el-table-column label="成长值门槛" width="130">
          <template #default="{ row }">{{ row.growthThreshold }}</template>
        </el-table-column>
        <el-table-column label="会员折扣" width="120">
          <template #default="{ row }">{{ (row.discountRate || 100) / 10 }} 折</template>
        </el-table-column>
        <el-table-column label="每月包邮" width="110">
          <template #default="{ row }">{{ row.freeShippingCount || 0 }} 次</template>
        </el-table-column>
        <el-table-column label="权益" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ benefitsText(row.benefitsJson) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="mini" :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="right">
          <template #default="{ row }">
            <el-button type="text" @click="openEdit(row)">编辑</el-button>
            <el-button type="text" style="color:#f56c6c" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="form.id ? '编辑等级' : '新增等级'" v-model="visible" width="520px">
      <el-form :model="form" label-width="110px" size="small">
        <el-form-item label="等级名称" required>
          <el-input v-model="form.name" placeholder="如 黄金会员" />
        </el-form-item>
        <el-form-item label="等级序号" required>
          <el-input-number v-model="form.levelNo" :min="1" :max="20" size="small" />
          <span class="hint">数字越大等级越高</span>
        </el-form-item>
        <el-form-item label="成长值门槛" required>
          <el-input-number v-model="form.growthThreshold" :min="0" :step="100" size="small" />
        </el-form-item>
        <el-form-item label="会员折扣">
          <el-input v-model="form.discountRate" style="width:120px"><template #append>%</template></el-input>
          <span class="hint">填 95 表示 9.5 折，100 表示不打折</span>
        </el-form-item>
        <el-form-item label="每月包邮次数">
          <el-input-number v-model="form.freeShippingCount" :min="0" :max="99" size="small" />
        </el-form-item>
        <el-form-item label="权益说明">
          <el-input v-model="form.benefitsText" type="textarea" :rows="2" placeholder="多个权益用逗号分隔，如 9.5折,每月3次包邮,专属客服" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
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
import { pageLevel, saveLevel, removeLevel } from '@/api/mall_member_level'

export default {
  name: 'MallMemberLevel',
  data() {
    return {
      list: [],
      loading: false,
      visible: false,
      saving: false,
      form: {}
    }
  },
  created() {
    this.load()
  },
  methods: {
    benefitsText(json) {
      if (!json) return '—'
      try {
        const arr = JSON.parse(json)
        return Array.isArray(arr) ? arr.join('、') : String(json)
      } catch (e) {
        return json
      }
    },
    async load() {
      this.loading = true
      try {
        const res = await pageLevel({ pageNum: 1, pageSize: 50 })
        const d = res.data || {}
        this.list = d.records || []
      } finally {
        this.loading = false
      }
    },
    openCreate() {
      this.form = { levelNo: this.list.length + 1, growthThreshold: 0, discountRate: 100, freeShippingCount: 0, enabled: true, benefitsText: '' }
      this.visible = true
    },
    openEdit(row) {
      this.form = {
        id: row.id,
        name: row.name,
        levelNo: row.levelNo,
        growthThreshold: row.growthThreshold,
        discountRate: row.discountRate || 100,
        freeShippingCount: row.freeShippingCount || 0,
        enabled: Number(row.status) === 1,
        benefitsText: this.benefitsText(row.benefitsJson) === '—' ? '' : this.benefitsText(row.benefitsJson)
      }
      this.visible = true
    },
    async submit() {
      if (!this.form.name) {
        this.$message.warning('请填写等级名称')
        return
      }
      const benefits = this.form.benefitsText
        ? JSON.stringify(String(this.form.benefitsText).split(/[,，]/).map(s => s.trim()).filter(Boolean))
        : null
      this.saving = true
      try {
        await saveLevel({
          id: this.form.id,
          name: this.form.name,
          levelNo: this.form.levelNo,
          growthThreshold: this.form.growthThreshold,
          discountRate: Number(this.form.discountRate) || 100,
          freeShippingCount: this.form.freeShippingCount,
          benefitsJson: benefits,
          status: this.form.enabled ? 1 : 0
        })
        this.$message.success('已保存')
        this.visible = false
        this.load()
      } finally {
        this.saving = false
      }
    },
    async remove(row) {
      await this.$confirm(`删除等级「${row.name}」？已有会员会回落到低等级。`, '删除确认', { type: 'warning' })
      await removeLevel(row.id)
      this.$message.success('已删除')
      this.load()
    }
  }
}
</script>

<style scoped>
.mt { margin-top: 12px; }
.hint { color: #a8abb2; font-size: 12px; margin-left: 8px; }
</style>
