<template>
  <div class="page page--tab">
    <van-nav-bar title="购物车" />
    <div v-if="list.length" class="cart-body">
      <van-swipe-cell v-for="item in list" :key="item.id">
        <div class="cart-item" :class="{ off: !isValid(item) }">
          <van-checkbox
            :model-value="item.checked === 1"
            :disabled="!isValid(item)"
            checked-color="#4a6cf7"
            @update:model-value="v => onCheck(item, v)"
          />
          <van-image :src="thumb(item.skuImage, 80)" fit="cover" class="ci-img" @click="router.push(`/goods/${item.goodsId}`)" />
          <div class="ci-mid">
            <div class="ci-name van-ellipsis">{{ item.goodsName }}</div>
            <div class="ci-spec text-gray">{{ specText(item.specJson) }}</div>
            <van-tag v-if="!isValid(item)" type="danger" plain>已下架</van-tag>
            <div class="ci-bot">
              <span class="price"><span class="symbol">¥</span>{{ fenToYuan(item.price) }}</span>
              <van-stepper
                :model-value="item.num"
                min="1"
                :max="Math.max(item.stock, 1)"
                :disabled="!isValid(item)"
                @update:model-value="v => onNum(item, v)"
              />
            </div>
          </div>
        </div>
        <template #right>
          <van-button square type="danger" text="删除" class="del-btn" @click="onDel(item)" />
        </template>
      </van-swipe-cell>
    </div>
    <van-empty v-else description="购物车还是空的">
      <van-button round size="small" color="#4a6cf7" @click="router.push('/home')">去逛逛</van-button>
    </van-empty>

    <!-- 结算栏（下架行不参与） -->
    <div v-if="list.length" class="cart-bar">
      <van-checkbox :model-value="allChecked" checked-color="#4a6cf7" @update:model-value="toggleAll">全选</van-checkbox>
      <div class="cart-total">
        合计：<span class="price"><span class="symbol">¥</span>{{ fenToYuan(totalPrice) }}</span>
      </div>
      <van-button round color="#4a6cf7" :disabled="!checkedCount" @click="checkout">结算({{ checkedCount }})</van-button>
    </div>

    <TabBar />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCartList, updateCart, deleteCart } from '@/api/cart'
import { fenToYuan, specText, thumb } from '@/utils/format'
import TabBar from '@/components/TabBar.vue'

const router = useRouter()
const list = ref([])

// 上架行才可勾选/结算
const isValid = item => item.goodsStatus === 1 && item.skuStatus === 1

const validList = computed(() => list.value.filter(isValid))
const allChecked = computed(() => validList.value.length > 0 && validList.value.every(i => i.checked === 1))
const checkedCount = computed(() => validList.value.filter(i => i.checked === 1).length)
const totalPrice = computed(() =>
  validList.value.filter(i => i.checked === 1).reduce((sum, i) => sum + Number(i.price || 0) * i.num, 0),
)

const load = async () => {
  list.value = await getCartList()
}

const onCheck = async (item, v) => {
  await updateCart({ id: item.id, checked: v ? 1 : 0 })
  item.checked = v ? 1 : 0
}

const onNum = async (item, v) => {
  try {
    await updateCart({ id: item.id, num: v })
    item.num = v
  } catch (e) {
    // 失败（如超库存）回刷，恢复真实数量
    await load()
  }
}

const onDel = async item => {
  await deleteCart(item.id)
  await load()
}

const toggleAll = async v => {
  const targets = validList.value.filter(i => (i.checked === 1) !== v)
  for (const item of targets) {
    await updateCart({ id: item.id, checked: v ? 1 : 0 })
    item.checked = v ? 1 : 0
  }
}

const checkout = () => router.push('/order/confirm')

onMounted(load)
</script>

<style scoped>
.cart-body {
  padding-bottom: 56px;
}

.cart-item {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  margin: 8px 10px 0;
  border-radius: 8px;
  padding: 12px 10px;
}

.cart-item.off {
  opacity: 0.5;
}

.ci-img {
  width: 72px;
  height: 72px;
  border-radius: 6px;
  overflow: hidden;
  flex: none;
}

.ci-mid {
  flex: 1;
  min-width: 0;
}

.ci-name {
  font-size: 14px;
}

.ci-spec {
  font-size: 12px;
  margin: 4px 0;
}

.ci-bot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
}

.del-btn {
  height: 100%;
  border-radius: 0 8px 8px 0;
}

.cart-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 50px;
  z-index: 10;
  max-width: 750px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-top: 1px solid #ebedf0;
  padding: 8px 12px;
}

.cart-total {
  flex: 1;
  text-align: right;
  font-size: 13px;
}
</style>
