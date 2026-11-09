<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="标准问题 / 编码" clearable style="width:200px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width:140px" @change="search">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px" @change="search">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
          <el-button type="success" icon="el-icon-plus" @click="openEdit()">新增 FAQ</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt">
      <template #header><div>
        <span>FAQ 知识库</span>
        <span class="hint">机器人优先命中文条目；命中不佳时配置同义问法可提升召回</span>
      </div></template>
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无 FAQ">
        <el-table-column prop="code" label="编码" width="140" show-overflow-tooltip />
        <el-table-column prop="question" label="标准问题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip :content="row.answer" placement="top" :disabled="!row.answer">
              <span class="answer">{{ row.answer }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column prop="synonyms" label="同义问法" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.synonyms || '—' }}</template>
        </el-table-column>
        <el-table-column prop="hitCount" label="命中次数" width="90" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch :value="Number(row.status) === 1" @change="toggleStatus(row, $event)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="right">
          <template #default="{ row }">
            <el-button type="text" @click="openEdit(row)">编辑</el-button>
            <el-button type="text" class="danger-btn" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" v-model:current-page="query.pageNum" :page-size="query.pageSize" @current-change="load" />
    </el-card>

    <el-dialog :title="form.id ? '编辑 FAQ' : '新增 FAQ'" v-model="visible" width="600px">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="标准问题" required>
          <el-input v-model="form.question" placeholder="用户会怎么问，例如：我的订单到哪了" />
        </el-form-item>
        <el-form-item label="答案" required>
          <el-input v-model="form.answer" type="textarea" :rows="4" placeholder="机器人命中的标准回复" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" filterable allow-create default-first-option placeholder="选择已有分类或输入新分类" style="width:100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="同义问法">
          <el-input v-model="form.synonyms" type="textarea" :rows="2" placeholder="多个同义问法用英文逗号分隔，例如：物流到哪了,什么时候发货" />
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
import { pageFaq, addFaq, updateFaq, deleteFaq } from '@/api/mall_chat'

export default {
  name: 'MallChatFaq',
  data() {
    return {
      query: { keyword: '', category: null, status: null, pageNum: 1, pageSize: 10 },
      list: [],
      total: 0,
      categories: [],
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
    async load() {
      this.loading = true
      try {
        // 空筛选不传，避免后端按空串过滤
        const params = { pageNum: this.query.pageNum, pageSize: this.query.pageSize }
        if (this.query.keyword && this.query.keyword.trim()) params.keyword = this.query.keyword.trim()
        if (this.query.category) params.category = this.query.category
        if (this.query.status !== null && this.query.status !== '') params.status = this.query.status
        const res = await pageFaq(params)
        const d = res.data || {}
        // 后端分页统一返回 {records,total}
        this.list = d.records || []
        this.total = d.total || 0
        this.categories = d.categories || []
      } finally {
        this.loading = false
      }
    },
    search() {
      this.query.pageNum = 1
      this.load()
    },
    openEdit(row) {
      this.form = row
        ? { id: row.id, question: row.question, answer: row.answer, category: row.category, synonyms: row.synonyms || '', enabled: Number(row.status) === 1 }
        : { question: '', answer: '', category: '', synonyms: '', enabled: true }
      this.visible = true
    },
    async toggleStatus(row, on) {
      await updateFaq({ id: row.id, question: row.question, answer: row.answer, category: row.category, synonyms: row.synonyms || '', status: on ? 1 : 0 })
      this.$message.success(on ? '已启用' : '已停用')
      this.load()
    },
    async submit() {
      if (!this.form.question.trim() || !this.form.answer.trim()) {
        this.$message.warning('请填写标准问题和答案')
        return
      }
      this.saving = true
      try {
        const body = {
          question: this.form.question.trim(),
          answer: this.form.answer.trim(),
          category: (this.form.category || '').trim(),
          synonyms: (this.form.synonyms || '').trim(),
          status: this.form.enabled ? 1 : 0
        }
        if (this.form.id) {
          await updateFaq({ id: this.form.id, ...body })
        } else {
          await addFaq(body)
        }
        this.$message.success('已保存')
        this.visible = false
        this.load()
      } finally {
        this.saving = false
      }
    },
    async remove(row) {
      const ok = await this.$confirm(`确认删除 FAQ「${row.question}」？删除后机器人不再命中该条目`, '删除确认', { type: 'warning' }).catch(() => false)
      if (!ok) return
      await deleteFaq(row.id)
      this.$message.success('已删除')
      this.load()
    }
  }
}
</script>

<style scoped>
.mt { margin-top: 14px; }
.hint { color: #a8abb2; font-size: 12px; margin-left: 10px; }
.pager { margin-top: 12px; text-align: right; }
.answer { display: inline-block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: bottom; }
.danger-btn { color: #f56c6c; }
</style>
