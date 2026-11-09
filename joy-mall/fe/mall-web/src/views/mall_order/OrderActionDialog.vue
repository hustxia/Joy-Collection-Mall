<template>
  <!-- 订单操作对话框：发货 / 关闭订单 / 管理员备注，三选一（mode 决定内容） -->
  <el-dialog
    :title="title"
    :visible="visible"
    width="520px"
    top="12vh"
    append-to-body
    @update:visible="v => $emit('update:visible', v)"
    @close="handleClose"
  >
    <div v-if="order" class="order-brief">
      <span class="brief-label">订单号</span>
      <span class="brief-value">{{ order.orderNo }}</span>
      <span class="brief-label">状态</span>
      <span class="brief-value">{{ statusText }}</span>
    </div>

    <!-- 发货 -->
    <el-form
      v-if="mode === 'ship'"
      ref="form"
      :model="form"
      :rules="rules"
      label-width="84px"
      size="small"
    >
      <el-form-item label="快递公司" prop="expressCompany">
        <el-input v-model="form.expressCompany" placeholder="如：顺丰速运" maxlength="50" />
      </el-form-item>
      <el-form-item label="快递单号" prop="expressNo">
        <el-input v-model="form.expressNo" placeholder="请输入快递单号" maxlength="50" />
      </el-form-item>
    </el-form>

    <!-- 关闭订单 -->
    <div v-else-if="mode === 'close'" class="close-tip">
      <p><i class="el-icon-warning-outline" /> 关闭后订单不可恢复，SKU 库存将自动回补。</p>
      <p class="tip-muted">若该单已付款，关单<b>不会自动退款</b>；需要退款请在「售后退款单」发起。</p>
    </div>

    <!-- 备注 -->
    <div v-else-if="mode === 'remark'">
      <el-input
        v-model="form.remark"
        type="textarea"
        :rows="4"
        maxlength="255"
        show-word-limit
        placeholder="仅在后台可见，最多 255 字"
      />
    </div>

    <template #footer><span>
      <el-button size="small" @click="handleClose">取消</el-button>
      <el-button
        size="small"
        :type="mode === 'close' ? 'danger' : 'primary'"
        :loading="submitting"
        @click="submit"
      >{{ confirmText }}</el-button>
    </span></template>
  </el-dialog>
</template>

<script>
import { deliverMallOrder, closeMallOrder, remarkMallOrder } from '@/api/mall_order'

const STATUS_MAP = {
  10: '待付款',
  20: '待发货',
  30: '已发货',
  40: '已完成',
  50: '已取消',
  60: '已关闭'
}

const TITLES = { ship: '订单发货', close: '关闭订单', remark: '管理员备注' }
const CONFIRMS = { ship: '确认发货', close: '确认关闭', remark: '保存备注' }

export default {
  name: 'OrderActionDialog',
  props: {
    visible: { type: Boolean, default: false },
    // 当前操作的订单行
    order: { type: Object, default: null },
    // ship | close | remark
    mode: { type: String, default: 'remark' }
  },
  data() {
    return {
      submitting: false,
      form: { expressCompany: '', expressNo: '', remark: '' },
      rules: {
        expressCompany: [{ required: true, message: '请输入快递公司', trigger: 'blur' }],
        expressNo: [{ required: true, message: '请输入快递单号', trigger: 'blur' }]
      }
    }
  },
  computed: {
    title() {
      return TITLES[this.mode] || '订单操作'
    },
    confirmText() {
      return CONFIRMS[this.mode] || '确定'
    },
    statusText() {
      const s = this.order && this.order.status
      return STATUS_MAP[s] || (s === undefined || s === null ? '-' : s)
    }
  },
  watch: {
    // 每次打开按当前订单重置表单：快递字段留空，备注回填既有值
    visible(v) {
      if (v) this.reset()
    }
  },
  methods: {
    reset() {
      this.form = {
        expressCompany: '',
        expressNo: '',
        remark: (this.order && this.order.remark) || ''
      }
      this.$nextTick(() => {
        if (this.$refs.form) this.$refs.form.clearValidate()
      })
    },
    handleClose() {
      this.$emit('update:visible', false)
    },
    async submit() {
      if (!this.order || !this.order.id) return
      if (this.mode === 'ship') {
        const valid = await this.$refs.form.validate().catch(() => false)
        if (!valid) return
      }
      this.submitting = true
      try {
        if (this.mode === 'ship') {
          await deliverMallOrder({
            id: this.order.id,
            expressCompany: this.form.expressCompany,
            expressNo: this.form.expressNo
          })
          this.$message.success('发货成功')
        } else if (this.mode === 'close') {
          await this.$confirm('确认关闭该订单？库存将自动回补，且不可恢复。', '关闭订单', {
            type: 'warning',
            confirmButtonText: '确认关闭',
            cancelButtonText: '再想想'
          })
          await closeMallOrder(this.order.id)
          this.$message.success('订单已关闭，库存已回补')
        } else {
          await remarkMallOrder(this.order.id, this.form.remark)
          this.$message.success('备注已保存')
        }
        this.$emit('success')
        this.handleClose()
      } catch (e) {
        // 用户取消二次确认（'cancel'/'close'）静默；接口错误由拦截器提示
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.order-brief {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 14px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
}
.brief-label {
  color: #909399;
}
.brief-value {
  color: #303133;
  font-weight: 600;
  margin-right: 12px;
}
.close-tip p {
  margin: 0 0 8px;
  font-size: 13px;
  color: #303133;
}
.close-tip .tip-muted {
  color: #909399;
}
.close-tip i {
  color: #e6a23c;
  margin-right: 4px;
}
</style>
