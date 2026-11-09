<template>
  <div class="page">
    <van-nav-bar title="我的售后" left-arrow @click-left="router.back()" />

    <van-tabs v-model:active="active" sticky>
      <van-tab v-for="t in tabs" :key="t.label" :title="t.label" />
    </van-tabs>

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="loadPage"
    >
      <div v-for="r in records" :key="r.id" class="card" @click="router.push(`/refund/${r.id}`)">
        <div class="card-head">
          <span class="no">{{ r.refundNo }}</span>
          <span class="st" :class="`st-${r.status}`">{{ refundStatusText(r.status) }}</span>
        </div>
        <div class="card-body">
          <div class="row"><span class="k">关联订单</span><span>{{ r.orderNo }}</span></div>
          <div class="row"><span class="k">类型</span><span>{{ refundTypeText(r.type) }}</span></div>
          <div class="row"><span class="k">退款金额</span><span class="price">¥{{ fenToYuan(r.amount) }}</span></div>
          <div class="row reason"><span class="k">原因</span><span class="van-ellipsis">{{ r.reason }}</span></div>
        </div>
        <div class="card-foot">
          <span class="text-gray time">{{ r.createTime }}</span>
          <van-button
            v-if="r.status === 1 || r.status === 2"
            size="small"
            round
            plain
            @click.stop="onClose(r)"
          >撤销申请</van-button>
        </div>
      </div>
    </van-list>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import { closeRefund, getRefundPage } from '@/api/refund'
import { fenToYuan, refundStatusText, refundTypeText } from '@/utils/format'

const router = useRouter()
const tabs = [
  { label: '全部', status: undefined },
  { label: '待审核', status: 1 },
  { label: '已同意', status: 2 },
  { label: '已退款', status: 4 },
  { label: '已拒绝', status: 3 },
  { label: '已关闭', status: 5 },
]
const active = ref(0)
const records = ref([])
const loading = ref(false)
const finished = ref(false)
const pageNum = ref(1)
const pageSize = 10

const currentStatus = () => tabs[active.value].status

const loadPage = async () => {
  const page = await getRefundPage({ status: currentStatus(), pageNum: pageNum.value, pageSize })
  records.value.push(...(page.records || []))
  loading.value = false
  if (records.value.length >= Number(page.total)) {
    finished.value = true
  } else {
    pageNum.value += 1
  }
}

watch(active, () => {
  records.value = []
  pageNum.value = 1
  finished.value = false
  loading.value = true
  loadPage()
})

const onClose = async r => {
  await showConfirmDialog({
    title: '撤销售后',
    message: '确定撤销该售后申请吗？撤销后可重新发起。',
    confirmButtonColor: '#4a6cf7',
  })
  try {
    await closeRefund(r.id)
    showSuccessToast('已撤销')
    records.value = []
    pageNum.value = 1
    finished.value = false
    loading.value = true
    loadPage()
  } catch (e) {
    showToast(e.message || '撤销失败')
  }
}
</script>

<style scoped>
.card {
  background: #fff;
  border-radius: 10px;
  margin: 10px 12px 0;
  padding: 12px 14px;
}
.card-head { display: flex; justify-content: space-between; align-items: center; }
.no { font-size: 13px; font-weight: 600; }
.st { font-size: 12px; }
.st-1 { color: #ff976a; }
.st-2 { color: #1989fa; }
.st-3 { color: #ee0a24; }
.st-4 { color: #07c160; }
.st-5 { color: #969799; }
.card-body { margin-top: 8px; border-top: 1px solid #f2f3f5; padding-top: 4px; }
.row { display: flex; justify-content: space-between; font-size: 13px; padding: 4px 0; gap: 16px; }
.row .k { color: #969799; flex: none; }
.price { color: #ee0a24; font-weight: 600; }
.reason span:last-child { max-width: 70%; }
.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.time { font-size: 11px; }
</style>
