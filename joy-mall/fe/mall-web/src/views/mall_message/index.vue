<template>
  <div class="app-container">
    <el-tabs v-model="tab">
      <el-tab-pane label="发送台账" name="log">
        <el-card shadow="never">
          <el-form :inline="true" size="small" @submit.prevent>
            <el-form-item label="场景">
              <el-select v-model="logQuery.sceneCode" placeholder="全部" clearable style="width:170px" @change="loadLog">
                <el-option label="支付成功" value="PAY_SUCCESS" />
                <el-option label="订单发货" value="ORDER_SHIPPED" />
                <el-option label="退款到账" value="REFUND_ARRIVED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="el-icon-search" @click="loadLog">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="loadingLog" :data="logs" size="small" stripe empty-text="暂无发送记录">
            <el-table-column prop="sceneCode" label="场景" width="150" />
            <el-table-column prop="receiver" label="接收人" width="150" show-overflow-tooltip />
            <el-table-column label="通道" width="100">
              <template #default="{ row }">{{ row.channel === 'sms' ? '短信' : '站内信' }}</template>
            </el-table-column>
            <el-table-column label="结果" width="150">
              <template #default="{ row }">
                <el-tag size="mini" :type="Number(row.status) === 1 ? 'success' : 'danger'">
                  {{ Number(row.status) === 1 ? '已发送' : '失败' }}
                </el-tag>
                <el-tag v-if="Number(row.degraded) === 1" size="mini" type="warning" style="margin-left:4px">已降级</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="retryCount" label="重试" width="80" />
            <el-table-column prop="errorMsg" label="错误" min-width="180" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="170" />
          </el-table>
          <el-pagination class="pager" background layout="total, prev, pager, next" :total="logTotal" v-model:current-page="logQuery.pageNum" :page-size="logQuery.pageSize" @current-change="loadLog" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="消息模板" name="template">
        <el-card shadow="never">
          <template #header><div>
            <span>模板管理</span>
            <span class="hint">变量用 ${'$'}{var} 占位；短信通道未配置时自动降级站内信，台账会标「已降级」</span>
          </div></template>
          <el-table v-loading="loadingTpl" :data="templates" size="small" stripe empty-text="暂无模板">
            <el-table-column prop="sceneCode" label="场景码" width="170" />
            <el-table-column prop="sceneName" label="场景名" width="130" />
            <el-table-column prop="titleTemplate" label="标题模板" min-width="160" show-overflow-tooltip />
            <el-table-column prop="contentTemplate" label="内容模板" min-width="260" show-overflow-tooltip />
            <el-table-column label="通道" width="100">
              <template #default="{ row }">{{ row.channel === 'sms' ? '短信' : '站内信' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="mini" :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="right">
              <template #default="{ row }">
                <el-button type="text" @click="openEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog title="编辑模板" v-model="visible" width="560px">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="场景码">
          <el-input v-model="form.sceneCode" disabled />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.titleTemplate" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.contentTemplate" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="通道">
          <el-select v-model="form.channel" style="width:140px">
            <el-option label="短信" value="sms" />
            <el-option label="站内信" value="site" />
          </el-select>
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
import { pageMessageLog, pageTemplate, saveTemplate } from '@/api/mall_message'

export default {
  name: 'MallMessage',
  data() {
    return {
      tab: 'log',
      logQuery: { sceneCode: null, pageNum: 1, pageSize: 10 },
      logs: [],
      logTotal: 0,
      loadingLog: false,
      templates: [],
      loadingTpl: false,
      visible: false,
      saving: false,
      form: {}
    }
  },
  created() {
    this.loadLog()
    this.loadTemplates()
  },
  methods: {
    async loadLog() {
      this.loadingLog = true
      try {
        const res = await pageMessageLog(this.logQuery)
        const d = res.data || {}
        this.logs = d.records || []
        this.logTotal = d.total || 0
      } finally {
        this.loadingLog = false
      }
    },
    async loadTemplates() {
      this.loadingTpl = true
      try {
        const res = await pageTemplate({ pageNum: 1, pageSize: 50 })
        const d = res.data || {}
        // 后端分页统一返回 {records,total}；兼容直接返回数组的老口径，绝不让对象进 el-table
        this.templates = Array.isArray(d) ? d : (d.records || [])
      } finally {
        this.loadingTpl = false
      }
    },
    openEdit(row) {
      this.form = { ...row, enabled: Number(row.status) === 1 }
      this.visible = true
    },
    async submit() {
      this.saving = true
      try {
        await saveTemplate({
          id: this.form.id,
          sceneCode: this.form.sceneCode,
          sceneName: this.form.sceneName,
          titleTemplate: this.form.titleTemplate,
          contentTemplate: this.form.contentTemplate,
          channel: this.form.channel,
          status: this.form.enabled ? 1 : 0
        })
        this.$message.success('已保存')
        this.visible = false
        this.loadTemplates()
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.hint { color: #a8abb2; font-size: 12px; margin-left: 10px; }
.pager { margin-top: 12px; text-align: right; }
</style>
