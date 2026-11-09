<template>
  <van-popup :show="show" position="bottom" round style="max-height: 70vh" @update:show="$emit('update:show', $event)">
    <div class="ap-head">
      <span>选择收货地址</span>
      <van-icon name="cross" @click="$emit('update:show', false)" />
    </div>
    <div class="ap-list">
      <van-cell
        v-for="a in list"
        :key="a.id"
        :label="`${a.province}${a.city}${a.district}${a.detail}`"
        clickable
        @click="onPick(a)"
      >
        <template #title>
          {{ a.receiverName }}  {{ a.receiverPhone }}
          <van-tag v-if="a.isDefault === 1" type="primary" class="ap-tag">默认</van-tag>
        </template>
      </van-cell>
      <van-empty v-if="loaded && !list.length" description="暂无收货地址">
        <van-button round type="primary" color="#4a6cf7" size="small" @click="router.push('/address/edit')">新建地址</van-button>
      </van-empty>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getAddressList } from '@/api/address'

const props = defineProps({ show: Boolean })
const emit = defineEmits(['update:show', 'select'])
const router = useRouter()

const list = ref([])
const loaded = ref(false)

watch(() => props.show, v => {
  if (v && !loaded.value) {
    getAddressList().then(rows => {
      list.value = rows || []
      loaded.value = true
    })
  }
})

const onPick = a => {
  emit('select', a)
  emit('update:show', false)
}
</script>

<style scoped>
.ap-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  font-weight: 600;
  font-size: 15px;
}
.ap-list {
  overflow-y: auto;
  max-height: 55vh;
  padding-bottom: 20px;
}
.ap-tag {
  margin-left: 6px;
}
</style>
