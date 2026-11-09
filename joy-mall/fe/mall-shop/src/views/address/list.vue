<template>
  <div class="page">
    <van-nav-bar :title="isSelect ? '选择地址' : '收货地址'" left-arrow @click-left="router.back()" />

    <div v-for="a in list" :key="a.id" class="addr-card" @click="onCardClick(a)">
      <div class="addr-line1">
        <span class="addr-name">{{ a.receiverName }}</span>
        <span class="addr-phone">{{ a.receiverPhone }}</span>
      </div>
      <div class="addr-line2">{{ a.province }}{{ a.city }}{{ a.district }}{{ a.detail }}</div>
      <div class="addr-ops" @click.stop>
        <van-tag v-if="a.isDefault === 1" type="primary" color="#4a6cf7">默认</van-tag>
        <span v-else class="op" @click="onSetDefault(a)">设为默认</span>
        <span class="ops-right">
          <span class="op" @click="router.push(`/address/edit?id=${a.id}`)">编辑</span>
          <span class="op" @click="onDelete(a)">删除</span>
        </span>
      </div>
    </div>
    <van-empty v-if="!list.length" description="暂无收货地址" />

    <div class="new-btn-wrap">
      <van-button block round color="#4a6cf7" icon="plus" @click="router.push('/address/edit')">新建地址</van-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getAddressList, deleteAddress, setDefaultAddress } from '@/api/address'
import { useCheckoutStore } from '@/stores/checkout'

const route = useRoute()
const router = useRouter()
const checkout = useCheckoutStore()

// select=1 时为确认订单页选地址模式
const isSelect = computed(() => route.query.select === '1')
const list = ref([])

const load = async () => {
  list.value = await getAddressList()
}

const onCardClick = a => {
  if (!isSelect.value) return
  checkout.selectAddress(a)
  router.back()
}

const onSetDefault = async a => {
  await setDefaultAddress(a.id)
  showToast('已设为默认地址')
  await load()
}

const onDelete = async a => {
  await showConfirmDialog({ title: '删除地址', message: '确定删除该地址吗？', confirmButtonColor: '#4a6cf7' })
  await deleteAddress(a.id)
  showToast('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.addr-card {
  background: #fff;
  margin: 8px 10px 0;
  border-radius: 8px;
  padding: 12px;
}

.addr-line1 {
  font-size: 15px;
  font-weight: 600;
}

.addr-phone {
  margin-left: 12px;
  font-weight: 400;
  color: #646566;
}

.addr-line2 {
  margin-top: 6px;
  font-size: 13px;
  color: #646566;
}

.addr-ops {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.ops-right .op {
  margin-left: 16px;
}

.op {
  font-size: 12px;
  color: #969799;
}

.new-btn-wrap {
  padding: 16px;
}
</style>
