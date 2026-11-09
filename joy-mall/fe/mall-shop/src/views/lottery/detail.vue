<template>
  <div class="page">
    <van-nav-bar title="抽奖" left-arrow fixed placeholder @click-left="router.back()" />

    <template v-if="activity">
      <!-- 转盘区 -->
      <div class="ltd-wheel-wrap">
        <div class="ltd-title">{{ activity.name }}</div>
        <div class="ltd-remain">今日剩余 {{ remain }} / {{ dailyLimit }} 次</div>
        <div class="ltd-wheel-box">
          <div
            class="ltd-wheel"
            :style="{ transform: `rotate(${rotateDeg}deg)`, background: wheelBg }"
          >
            <div
              v-for="(p, i) in sectors"
              :key="p.id"
              class="ltd-sector-label"
              :style="labelStyle(i)"
            >
              <span class="ltd-sector-type">{{ prizeIcon(p.prizeType) }}</span>
              <span class="ltd-sector-name">{{ p.name }}</span>
            </div>
          </div>
          <div class="ltd-pointer" :class="{ spinning }" @click="onDraw">
            <van-icon name="gift-o" size="22" />
            <span>{{ spinning ? '抽奖中' : '抽奖' }}</span>
          </div>
        </div>
      </div>

      <!-- 实物奖品地址（可选，抽中实物必须提供） -->
      <van-cell-group inset class="ltd-addr">
        <van-cell
          :title="address ? `${address.receiverName}  ${address.receiverPhone}` : '收货地址（抽中实物需要）'"
          :label="address ? `${address.province}${address.city}${address.district}${address.detail}` : '未选择'"
          is-link
          @click="showPicker = true"
        />
      </van-cell-group>

      <van-cell-group inset class="ltd-links">
        <van-cell title="我的中奖记录" is-link icon="orders-o" @click="router.push('/lottery/my')" />
      </van-cell-group>

      <div class="ltd-rule">
        每日限抽 {{ activity.dailyLimit }} 次{{ activity.consumePoints > 0 ? `，每次消耗 ${activity.consumePoints} 积分` : '，免费参与' }}；
        活动时间 {{ activity.startTime }} ~ {{ activity.endTime }}
      </div>
    </template>
    <van-loading v-else-if="loading" class="ltd-loading" vertical>加载中...</van-loading>
    <van-empty v-else-if="loadError" description="活动不存在" />

    <AddressPicker v-model:show="showPicker" @select="a => (address = a)" />

    <!-- 抽奖结果 -->
    <van-dialog
      v-model:show="showResult"
      :title="result && result.isWin === 1 ? '恭喜中奖' : '很遗憾'"
      :message="resultText"
      show-cancel-button
      :cancel-button-text="result && result.isWin === 1 ? '查看记录' : '继续抽奖'"
      confirm-button-text="确定"
      cancel-button-color="#4a6cf7"
      @cancel="onResultCancel"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getLotteryRemain, getLotteryActivities, drawLottery } from '@/api/lottery'
import AddressPicker from '@/components/AddressPicker.vue'

const route = useRoute()
const router = useRouter()
// id 为字符串，禁止 Number() 转换
const id = route.params.id

const activity = ref(null)
const loading = ref(true)
const loadError = ref(false)
const remain = ref(0)
const dailyLimit = ref(0)
const spinning = ref(false)
const rotateDeg = ref(0)
const showPicker = ref(false)
const address = ref(null)
const showResult = ref(false)
const result = ref(null)

// 转盘扇区：最多 8 个奖品
const sectors = computed(() => (activity.value ? (activity.value.prizes || []).slice(0, 8) : []))
const sectorAngle = computed(() => (sectors.value.length ? 360 / sectors.value.length : 360))
const wheelBg = computed(() => {
  const n = sectors.value.length || 1
  const stops = []
  for (let i = 0; i < n; i++) {
    stops.push(`${i % 2 === 0 ? '#fff7f0' : '#ffe9e0'} ${i * (360 / n)}deg ${(i + 1) * (360 / n)}deg`)
  }
  return `conic-gradient(${stops.join(', ')})`
})

const prizeIcon = t => ({ 0: '积分', 1: '优惠券', 2: '实物', 3: '谢谢' }[t] || '奖')

const labelStyle = i => {
  const mid = i * sectorAngle.value + sectorAngle.value / 2
  return { transform: `rotate(${mid}deg) translate(0, -78px) rotate(${-mid}deg)` }
}

const resultText = computed(() => {
  if (!result.value) return ''
  return result.value.isWin === 1
    ? `抽中「${result.value.prizeName}」${result.value.orderId ? '\n奖品已生成零元订单' : ''}`
    : result.value.prizeName
})

const loadRemain = async () => {
  try {
    const d = await getLotteryRemain(id)
    remain.value = d.remain
    dailyLimit.value = d.dailyLimit
  } catch (e) {
    // 未登录时静默，页面仍可浏览
  }
}

const onDraw = async () => {
  if (spinning.value) return
  spinning.value = true
  let data
  try {
    // 失败时 request 拦截器已 toast 后端 msg（如「今日抽奖次数已用完」）
    data = await drawLottery(id, address.value ? { addressId: address.value.id } : {})
  } catch (e) {
    spinning.value = false
    return
  }
  result.value = data
  remain.value = data.remain

  // 动画：优先停在与中奖奖品名匹配的扇区，否则随机停格
  const idx = sectors.value.findIndex(p => p.name === data.prizeName)
  const target = idx >= 0
    ? 360 * 5 - (idx * sectorAngle.value + sectorAngle.value / 2)
    : 360 * 5 + Math.floor(Math.random() * 360)
  rotateDeg.value = target
  // 与 CSS transition 时长一致
  setTimeout(() => {
    spinning.value = false
    showResult.value = true
  }, 4200)
}

const onResultCancel = () => {
  if (result.value && result.value.isWin === 1) {
    router.push('/lottery/my')
  }
}

onMounted(async () => {
  try {
    const list = (await getLotteryActivities()) || []
    activity.value = list.find(a => a.id === id) || null
    if (!activity.value) loadError.value = true
    await loadRemain()
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}
.ltd-wheel-wrap {
  background: linear-gradient(160deg, #e1251b, #ff7a45);
  padding: 18px 16px 30px;
  text-align: center;
}
.ltd-title {
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}
.ltd-remain {
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
  margin: 6px 0 16px;
}
.ltd-wheel-box {
  position: relative;
  width: 260px;
  height: 260px;
  margin: 0 auto;
}
.ltd-wheel {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 6px solid #ffd8bf;
  transition: transform 4s cubic-bezier(0.25, 0.1, 0.1, 1);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.18);
}
.ltd-sector-label {
  position: absolute;
  left: 50%;
  top: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: -26px 0 0 -40px;
  width: 80px;
  font-size: 11px;
  color: #7a3b1e;
}
.ltd-sector-type {
  font-weight: 700;
  color: #e1251b;
}
.ltd-sector-name {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ltd-pointer {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 74px;
  height: 74px;
  border-radius: 50%;
  background: #e1251b;
  border: 4px solid #fff;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.25);
  cursor: pointer;
}
.ltd-pointer.spinning {
  opacity: 0.75;
}
.ltd-addr {
  margin-top: -16px;
  position: relative;
  z-index: 1;
}
.ltd-links {
  margin-top: 10px;
}
.ltd-rule {
  font-size: 11px;
  color: #969799;
  padding: 12px 16px;
  line-height: 1.6;
}
.ltd-loading {
  margin-top: 60px;
}
</style>
