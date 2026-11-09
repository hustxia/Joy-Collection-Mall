<template>
  <div class="page">
    <van-nav-bar title="消息中心" left-arrow fixed placeholder @click-left="router.back()">
      <template #right>
        <span v-if="list.length" class="nav-read-all" @click="onReadAll">全部已读</span>
      </template>
    </van-nav-bar>

    <van-tabs v-model:active="type" shrink sticky @change="onTypeChange">
      <van-tab title="全部" :name="0" />
      <van-tab title="订单" :name="1" />
      <van-tab title="售后" :name="2" />
      <van-tab title="营销" :name="3" />
    </van-tabs>

    <van-empty v-if="!loading && !list.length" description="暂无消息" />

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
      <div
        v-for="it in list"
        :key="it.id"
        class="nt-item"
        :class="{ unread: it.readStatus === 0 }"
        @click="onRead(it)"
      >
        <div class="nt-top">
          <span class="nt-dot" v-if="it.readStatus === 0" />
          <span class="nt-title">{{ it.title }}</span>
          <span class="nt-time">{{ it.createTime }}</span>
        </div>
        <div class="nt-content">{{ it.content }}</div>
      </div>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getNoticeList, readNotice, readAllNotices } from '@/api/notice'

const router = useRouter()
const type = ref(0)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const finished = ref(false)

const onLoad = async () => {
  try {
    const params = { pageNum: page.value, pageSize: size }
    if (type.value) params.type = type.value
    const data = await getNoticeList(params)
    const rows = data.list || []
    total.value = data.total || 0
    list.value = page.value === 1 ? rows : list.value.concat(rows)
    page.value += 1
    finished.value = list.value.length >= total.value
  } catch (e) {
    finished.value = true
  } finally {
    loading.value = false
  }
}

const onTypeChange = () => {
  list.value = []
  page.value = 1
  finished.value = false
  loading.value = true
  onLoad()
}

const onRead = async it => {
  if (it.readStatus === 0) {
    await readNotice(it.id)
    it.readStatus = 1
  }
}

const onReadAll = async () => {
  const data = await readAllNotices()
  showToast(`已读 ${data.read || 0} 条`)
  list.value.forEach(it => { it.readStatus = 1 })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}
.nav-read-all {
  font-size: 13px;
  color: #4a6cf7;
}
.nt-item {
  background: #fff;
  padding: 12px 14px;
  border-bottom: 1px solid #f2f3f5;
}
.nt-top {
  display: flex;
  align-items: center;
  gap: 6px;
}
.nt-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e1251b;
  flex: none;
}
.nt-title {
  flex: 1;
  font-size: 14px;
  color: #323233;
}
.nt-item.unread .nt-title {
  font-weight: 600;
}
.nt-time {
  font-size: 11px;
  color: #969799;
  flex: none;
}
.nt-content {
  margin-top: 6px;
  font-size: 12px;
  color: #646566;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
