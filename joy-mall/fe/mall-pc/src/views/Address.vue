<template>
  <div>
    <div class="page-head">
      <span class="page-title">收货地址</span>
      <el-button type="danger" size="small" icon="el-icon-plus" @click="openForm(null)">新增地址</el-button>
    </div>

    <div v-loading="loading" class="addr-grid">
      <div v-for="a in list" :key="a.id" class="addr-item" :class="{ default: Number(a.isDefault) === 1 }">
        <div class="addr-item-head">
          <span>{{ a.receiverName }}</span>
          <span class="phone">{{ a.receiverPhone }}</span>
          <el-tag v-if="Number(a.isDefault) === 1" size="mini" type="danger" effect="plain">默认</el-tag>
        </div>
        <div class="addr-item-text">{{ fullAddress(a) }}</div>
        <div class="addr-item-foot">
          <a @click="openForm(a)">编辑</a>
          <a v-if="Number(a.isDefault) !== 1" @click="onSetDefault(a)">设为默认</a>
          <a @click="onDelete(a)">删除</a>
        </div>
      </div>
    </div>

    <div v-if="!loading && !list.length" class="page-card">
      <el-empty description="还没有收货地址，先添加一个吧" :image-size="90">
        <el-button type="danger" size="small" @click="openForm(null)">新增地址</el-button>
      </el-empty>
    </div>

    <address-form-dialog v-model="formVisible" :address="editing" @saved="load" />
  </div>
</template>

<script>
import { getAddressList, deleteAddress, setDefaultAddress } from '@/api/address'
import AddressFormDialog from '@/components/AddressFormDialog.vue'

export default {
  name: 'Address',
  components: { AddressFormDialog },
  data() {
    return { list: [], loading: false, formVisible: false, editing: null }
  },
  created() {
    this.load()
  },
  methods: {
    fullAddress(a) {
      return [a.province, a.city, a.district, a.detail].filter(Boolean).join('')
    },
    async load() {
      this.loading = true
      try {
        this.list = (await getAddressList()) || []
      } finally {
        this.loading = false
      }
    },
    openForm(a) {
      this.editing = a || null
      this.formVisible = true
    },
    async onSetDefault(a) {
      await setDefaultAddress(a.id)
      this.$message.success('已设为默认地址')
      this.load()
    },
    async onDelete(a) {
      await this.$confirm(`确定删除「${a.receiverName}」这个收货地址？`, '删除地址', { type: 'warning' })
      await deleteAddress(a.id)
      this.$message.success('已删除')
      this.load()
    }
  }
}
</script>
