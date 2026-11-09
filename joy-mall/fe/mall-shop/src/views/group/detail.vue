<template>
  <div class="page">
    <van-nav-bar title="拼团详情" left-arrow fixed placeholder @click-left="router.back()" />

    <template v-if="activity">
      <van-image v-if="activity.mainImage" :src="activity.mainImage" width="100%" height="280" fit="cover" />

      <div class="gpd-price-bar">
        <div>
          <span class="gpd-price">￥{{ fenToYuan(activity.groupPrice) }}</span>
          <span class="gpd-origin">￥{{ fenToYuan(activity.originPrice) }}</span>
        </div>
        <van-tag type="danger" size="large">{{ activity.groupSize }}人团</van-tag>
      </div>

      <div class="gpd-card">
        <div class="gpd-name">{{ activity.goodsName }}</div>
        <div class="gpd-meta">
          <span>成团时限 {{ activity.validHours || 24 }} 小时</span>
          <span>活动时间：{{ activity.startTime }} ~ {{ activity.endTime }}</span>
        </div>
      </div>

      <!-- 进行中可参团列表 -->
      <div class="gpd-card">
        <div class="gpd-sub-title">正在进行的团</div>
        <template v-if="teams.length">
          <div v-for="t in teams" :key="t.teamId" class="gpd-team-row">
            <div class="gpd-team-info">
              <van-icon name="friends-o" size="20" color="#4a6cf7" />
              <span class="gpd-team-size">{{ t.currentSize }}/{{ t.groupSize }} 人</span>
              <span class="gpd-team-expire">截 {{ shortExpire(t.expireTime) }}</span>
            </div>
            <van-button round type="primary" color="#e1251b" size="small" @click="onPickTeam(t)">参与拼团</van-button>
          </div>
        </template>
        <van-empty v-else image-size="60" description="暂无可参的团，快来开新团吧" />
      </div>

      <div class="gpd-bar">
        <van-button
          round
          block
          type="primary"
          color="#e1251b"
          :loading="opening"
          @click="showPicker = true"
        >
          ￥{{ fenToYuan(activity.groupPrice) }} 立即开团
        </van-button>
      </div>
    </template>
    <van-loading v-else-if="loading" class="gpd-loading" vertical>加载中...</van-loading>
    <van-empty v-else-if="loadError" description="活动不存在" />

    <!-- 地址选择：开团 / 参团共用 -->
    <AddressPicker v-model:show="showPicker" @select="onConfirm" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showDialog, showToast } from 'vant'
import { getGroupDetail, openGroup, joinGroupTeam } from '@/api/group'
import { fenToYuan } from '@/utils/format'
import AddressPicker from '@/components/AddressPicker.vue'

const route = useRoute()
const router = useRouter()
// id 为字符串，禁止 Number() 转换
const id = route.params.id

const activity = ref(null)
const teams = ref([])
const loading = ref(true)
const loadError = ref(false)
const opening = ref(false)
const showPicker = ref(false)
// 当前待下单动作：null=开团；否则为参团的 teamId
const pendingTeamId = ref(null)

const shortExpire = t => (t ? String(t).replace('T', ' ').slice(5, 16) : '')

const load = async () => {
  try {
    const data = await getGroupDetail(id)
    activity.value = data
    teams.value = data.teams || []
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const onPickTeam = team => {
  pendingTeamId.value = team.teamId
  showPicker.value = true
}

const onConfirm = async address => {
  opening.value = true
  try {
    // 失败时 request 拦截器已 toast 后端 msg（如「该团已成团」）
    const data = pendingTeamId.value
      ? await joinGroupTeam(pendingTeamId.value, { addressId: address.id })
      : await openGroup(id, { addressId: address.id })
    const msg = data.teamStatus === 1 ? '参团成功，已满足人数成团！' : '下单成功，等待团员加入'
    showDialog({
      title: pendingTeamId.value ? '参团成功' : '开团成功',
      message: `${msg}\n订单号 ${data.orderNo}\n应付 ￥${fenToYuan(data.payAmount)}`,
      theme: 'round-button',
      confirmButtonColor: '#e1251b',
    }).then(() => {
      router.push(`/pay/${data.orderNo}`)
    })
  } catch (e) {
    showToast('下单未完成')
  } finally {
    opening.value = false
    showPicker.value = false
    pendingTeamId.value = null
    load()
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 76px;
}
.gpd-price-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #e1251b, #ff6b61);
  padding: 12px 16px;
}
.gpd-price {
  color: #fff;
  font-size: 24px;
  font-weight: 700;
}
.gpd-origin {
  color: rgba(255, 255, 255, 0.8);
  font-size: 13px;
  text-decoration: line-through;
  margin-left: 8px;
}
.gpd-card {
  background: #fff;
  margin: 10px;
  border-radius: 8px;
  padding: 14px;
}
.gpd-name {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}
.gpd-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #969799;
  margin-top: 8px;
}
.gpd-sub-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 6px;
}
.gpd-team-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f2f3f5;
}
.gpd-team-row:last-child {
  border-bottom: none;
}
.gpd-team-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.gpd-team-size {
  font-size: 13px;
  color: #323233;
}
.gpd-team-expire {
  font-size: 11px;
  color: #969799;
}
.gpd-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);
}
.gpd-loading {
  margin-top: 60px;
}
</style>
