<template>
  <el-dialog
    :title="form.id ? '编辑收货地址' : '新增收货地址'"
    :visible="visible"
    width="520px"
    :close-on-click-modal="false"
    @update:visible="val => $emit('update:visible', val)"
  >
    <el-form ref="form" :model="form" :rules="rules" label-width="88px" size="small">
      <el-form-item label="收货人" prop="receiverName">
        <el-input v-model.trim="form.receiverName" maxlength="20" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="手机号" prop="receiverPhone">
        <el-input v-model.trim="form.receiverPhone" maxlength="11" placeholder="11 位手机号" />
      </el-form-item>
      <el-form-item label="省 / 市 / 区" required>
        <div style="display:flex;gap:8px">
          <el-form-item prop="province" style="margin-bottom:0;flex:1">
            <el-input v-model.trim="form.province" placeholder="省" />
          </el-form-item>
          <el-form-item prop="city" style="margin-bottom:0;flex:1">
            <el-input v-model.trim="form.city" placeholder="市" />
          </el-form-item>
          <el-form-item prop="district" style="margin-bottom:0;flex:1">
            <el-input v-model.trim="form.district" placeholder="区/县" />
          </el-form-item>
        </div>
      </el-form-item>
      <el-form-item label="详细地址" prop="detail">
        <el-input v-model.trim="form.detail" type="textarea" :rows="2" maxlength="120" placeholder="街道、门牌号、楼层等" />
      </el-form-item>
      <el-form-item label="设为默认">
        <el-checkbox v-model="form.isDefault" :true-label="1" :false-label="0">下次下单优先使用该地址</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer><div>
      <el-button size="small" @click="$emit('update:visible', false)">取消</el-button>
      <el-button size="small" type="danger" :loading="saving" @click="onSave">保存</el-button>
    </div></template>
  </el-dialog>
</template>

<script>
import { addAddress, updateAddress } from '@/api/address'

const emptyForm = () => ({
  id: null,
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: 0
})

export default {
  name: 'AddressFormDialog',
  props: {
    visible: { type: Boolean, default: false },
    address: { type: Object, default: null }
  },
  data() {
    return {
      saving: false,
      form: emptyForm(),
      rules: {
        receiverName: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
        receiverPhone: [
          { required: true, message: '请输入手机号', trigger: 'blur' },
          { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
        ],
        province: [{ required: true, message: '省必填', trigger: 'blur' }],
        city: [{ required: true, message: '市必填', trigger: 'blur' }],
        district: [{ required: true, message: '区必填', trigger: 'blur' }],
        detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
      }
    }
  },
  watch: {
    visible(val) {
      if (val) this.fill()
    }
  },
  methods: {
    fill() {
      const a = this.address
      this.form = a
        ? {
            // id 是雪花字符串，原样保留（不要 Number）
            id: a.id,
            receiverName: a.receiverName || '',
            receiverPhone: a.receiverPhone || '',
            province: a.province || '',
            city: a.city || '',
            district: a.district || '',
            detail: a.detail || '',
            isDefault: Number(a.isDefault) || 0
          }
        : emptyForm()
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    onSave() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.saving = true
        try {
          if (this.form.id) {
            await updateAddress({ ...this.form })
          } else {
            const { id, ...payload } = this.form
            await addAddress(payload)
          }
          this.$message.success(this.form.id ? '地址已更新' : '地址已新增')
          this.$emit('saved')
          this.$emit('update:visible', false)
        } finally {
          this.saving = false
        }
      })
    }
  }
}
</script>
