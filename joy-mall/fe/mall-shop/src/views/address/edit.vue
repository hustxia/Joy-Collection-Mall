<template>
  <div class="page">
    <van-nav-bar :title="form.id ? '编辑地址' : '新建地址'" left-arrow @click-left="router.back()" />

    <van-cell-group inset class="block">
      <van-field v-model="form.receiverName" label="收货人" placeholder="请输入姓名" />
      <van-field v-model="form.receiverPhone" label="手机号" type="tel" maxlength="11" placeholder="请输入手机号" />
      <van-field v-model="form.province" label="省份" placeholder="如：浙江省" />
      <van-field v-model="form.city" label="城市" placeholder="如：杭州市" />
      <van-field v-model="form.district" label="区/县" placeholder="如：西湖区" />
      <van-field v-model="form.detail" label="详细地址" type="textarea" rows="2" autosize placeholder="街道、门牌号等" />
      <van-cell title="设为默认地址">
        <template #right-icon>
          <van-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" active-color="#4a6cf7" size="20" />
        </template>
      </van-cell>
    </van-cell-group>

    <div class="btn-wrap">
      <van-button block round color="#4a6cf7" :loading="saving" @click="onSave">保存</van-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getAddressList, addAddress, updateAddress } from '@/api/address'

const route = useRoute()
const router = useRouter()
const saving = ref(false)

const form = reactive({
  id: null,
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: 0,
})

const onSave = async () => {
  if (!form.receiverName || !form.receiverPhone || !form.province || !form.city || !form.district || !form.detail) {
    return showToast('请填写完整地址信息')
  }
  if (!/^1\d{10}$/.test(form.receiverPhone)) {
    return showToast('手机号格式不正确')
  }
  saving.value = true
  try {
    if (form.id) {
      await updateAddress({ ...form })
    } else {
      const { id, ...data } = form
      await addAddress(data)
    }
    showToast('已保存')
    router.back()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  // 无单条查询接口，从列表里找
  const id = route.query.id
  if (!id) return
  const list = await getAddressList()
  const found = list.find(a => String(a.id) === String(id))
  if (found) Object.assign(form, found)
})
</script>

<style scoped>
.block {
  margin-top: 8px;
}

.btn-wrap {
  padding: 20px 16px;
}
</style>
