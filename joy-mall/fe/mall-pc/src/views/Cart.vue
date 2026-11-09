<template>
  <div>
    <div class="page-card">
      <div class="page-head">
        <span class="page-title">我的购物车</span>
        <span class="muted">共 {{ rows.length }} 种商品</span>
      </div>

      <el-table
        ref="table"
        v-loading="loading"
        :data="rows"
        row-key="id"
        empty-text="购物车还是空的，去挑点东西吧"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="46" :selectable="rowSelectable" />
        <el-table-column label="商品" min-width="360">
          <template #default="{ row }">
            <div class="cart-prod">
              <pc-image :src="row.skuImage" :img-style="{ width: '64px', height: '64px', objectFit: 'cover', borderRadius: '6px', display: 'block' }" />
              <div>
                <div class="cart-prod-name">
                  <router-link :to="'/goods/' + row.goodsId">{{ row.goodsName }}</router-link>
                </div>
                <div class="cart-prod-spec">{{ specText(row.specJson) || '默认规格' }}</div>
                <el-tag v-if="!rowSelectable(row)" type="info" size="mini" style="margin-top:6px">
                  {{ row.goodsStatus === 1 && row.skuStatus === 1 ? '库存不足' : '已下架' }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="130" align="right">
          <template #default="{ row }">¥{{ $price(row.price) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="170" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row.num"
              size="small"
              :min="1"
              :max="Math.max(row.stock, 1)"
              :disabled="!rowSelectable(row)"
              @change="value => onNumChange(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="小计" width="140" align="right">
          <template #default="{ row }">
            <span class="money">¥{{ $price(row.price * row.num) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button type="text" @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="cart-bar">
      <el-checkbox :value="allChecked" :indeterminate="someChecked" @change="toggleAll">全选</el-checkbox>
      <el-button type="text" @click="removeSelected">删除选中</el-button>
      <div class="cart-bar-mid">
        已选 <em class="money">{{ selectedCount }}</em> 件，合计
        <span class="cart-bar-total">¥{{ $price(selectedAmount) }}</span>
      </div>
      <el-button type="danger" :disabled="!selectedRows.length" @click="goCheckout">去结算</el-button>
    </div>
  </div>
</template>

<script>
import { getCartList, updateCart, deleteCart } from '@/api/cart'
import { specText } from '@/utils/format'
import PcImage from '@/components/PcImage.vue'
import store from '@/store'

export default {
  name: 'Cart',
  components: { PcImage },
  data() {
    return {
      rows: [],
      selectedRows: [],
      loading: false,
      // 程序化回填勾选态时会触发 selection-change，用它挡掉「回填 → 又写库」的回环
      syncing: false
    }
  },
  computed: {
    selectedCount() {
      return this.selectedRows.reduce((sum, r) => sum + r.num, 0)
    },
    selectedAmount() {
      return this.selectedRows.reduce((sum, r) => sum + Number(r.price) * r.num, 0)
    },
    availableRows() {
      return this.rows.filter(r => this.rowSelectable(r))
    },
    allChecked() {
      return this.availableRows.length > 0 && this.selectedRows.length === this.availableRows.length
    },
    someChecked() {
      return this.selectedRows.length > 0 && this.selectedRows.length < this.availableRows.length
    }
  },
  created() {
    this.load()
  },
  methods: {
    // Vue2 模板只能访问实例属性：import 进来的函数必须挂到 methods，否则渲染期报
    // "specText is not a function"，整张表格行都渲染不出来（走查实测）
    specText,
    rowSelectable(row) {
      return row.goodsStatus === 1 && row.skuStatus === 1 && Number(row.stock) > 0
    },
    async load() {
      this.loading = true
      try {
        this.rows = (await getCartList()) || []
        store.refreshCartCount()
        // 后端持久化了 checked，回填到表格勾选态
        this.$nextTick(() => {
          this.syncing = true
          this.rows.forEach(row => {
            if (Number(row.checked) === 1 && this.rowSelectable(row)) {
              this.$refs.table.toggleRowSelection(row, true)
            }
          })
          this.$nextTick(() => { this.syncing = false })
        })
      } finally {
        this.loading = false
      }
    },
    async onSelectionChange(rows) {
      this.selectedRows = rows
      if (this.syncing) return
      // 勾选态需要落库：下单接口取的是「购物车已勾选行」，不落库会与界面不一致
      const selectedIds = rows.map(r => r.id)
      const changed = this.rows.filter(r => {
        const should = selectedIds.includes(r.id) ? 1 : 0
        return Number(r.checked) !== should
      })
      await Promise.all(
        changed.map(r => {
          r.checked = selectedIds.includes(r.id) ? 1 : 0
          return updateCart({ id: r.id, checked: r.checked })
        })
      )
    },
    async toggleAll(checked) {
      // toggleRowSelection 每行都会触发一次 selection-change：先挡掉逐行回调，
      // 全部切换完再一次性按勾选态落库，避免 N 次 PUT
      this.syncing = true
      this.availableRows.forEach(row => this.$refs.table.toggleRowSelection(row, checked))
      this.rows.forEach(row => {
        if (this.rowSelectable(row)) row.checked = checked ? 1 : 0
      })
      this.syncing = false
      await Promise.all(this.availableRows.map(row => updateCart({ id: row.id, checked: row.checked })))
    },
    async onNumChange(row, value) {
      if (!value) return
      try {
        await updateCart({ id: row.id, num: value })
        store.refreshCartCount()
      } catch (e) {
        this.load()
      }
    },
    async onRemove(row) {
      await this.$confirm(`确定从购物车移除「${row.goodsName}」？`, '提示', { type: 'warning' })
      await deleteCart(row.id)
      this.$message.success('已移除')
      this.load()
    },
    async removeSelected() {
      if (!this.selectedRows.length) return
      await this.$confirm(`确定删除选中的 ${this.selectedRows.length} 种商品？`, '提示', { type: 'warning' })
      for (const row of this.selectedRows) {
        await deleteCart(row.id)
      }
      this.$message.success('已删除')
      this.selectedRows = []
      this.load()
    },
    goCheckout() {
      if (!this.selectedRows.length) {
        this.$message.warning('请先勾选要结算的商品')
        return
      }
      // 确认页不带 skuId 时按购物车勾选行下单
      this.$router.push('/order/confirm')
    }
  }
}
</script>
