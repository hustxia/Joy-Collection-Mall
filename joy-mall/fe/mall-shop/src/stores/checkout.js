import { defineStore } from 'pinia'

// 确认订单页选中的地址（地址列表 select 模式回写）
export const useCheckoutStore = defineStore('checkout', {
  state: () => ({
    selectedAddress: null,
  }),
  actions: {
    selectAddress(address) {
      this.selectedAddress = address
    },
    clear() {
      this.selectedAddress = null
    },
  },
})
