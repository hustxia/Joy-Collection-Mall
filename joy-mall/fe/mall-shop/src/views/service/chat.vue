<template>
  <div class="page svc-page">
    <van-nav-bar title="在线客服" left-arrow fixed placeholder @click-left="goBack" />

    <!-- 未登录：不发任何请求 -->
    <div v-if="!userStore.isLogin" class="svc-login-tip">
      <van-icon name="lock" size="42" color="#c8c9cc" />
      <p>登录后可使用在线客服</p>
      <van-button round type="danger" size="small" color="#e1251b" @click="goLogin">去登录</van-button>
    </div>

    <template v-else>
      <!-- 消息区 -->
      <div ref="list" class="svc-msgs">
        <template v-for="m in messages">
          <!-- 系统提示：失败/转人工等，红色小字居中，不用弹窗 -->
          <div v-if="m.role === 'system'" :key="m.id" class="svc-sys">{{ m.content }}</div>

          <div v-else :key="m.id" class="svc-row" :class="m.role === 'user' ? 'svc-row--me' : 'svc-row--ai'">
            <div class="svc-bubble" :class="m.role === 'user' ? 'svc-bubble--me' : 'svc-bubble--ai'">
              <span v-if="m.role === 'ai' && !m.done" class="svc-caret"></span>{{ m.role === 'ai' ? m.shown : m.content }}
            </div>

            <!-- 有用 / 没用 反馈（仅 AI 文本消息，回答完成后出现） -->
            <div v-if="m.role === 'ai' && m.done && m.content" class="svc-fb">
              <span :class="{ on: m.rating === 'up' }" @click="feedback(m, 'up')">👍 有用</span>
              <span :class="{ on: m.rating === 'down' }" @click="feedback(m, 'down')">👎 没用</span>
            </div>

            <!-- 卡片：order/refund 小卡，transfer 提示条 -->
            <div
              v-for="(c, i) in m.cards"
              :key="m.id + '-c' + i"
              class="svc-card"
              :class="{ 'svc-card--transfer': c.type === 'transfer' }"
            >
              <div v-if="c.title" class="svc-card-title">{{ c.title }}</div>
              <div v-if="c.fields && c.fields.length" class="svc-card-fields">
                <div v-for="f in c.fields" :key="f.label" class="svc-card-field">
                  <span class="svc-card-label">{{ f.label }}</span>
                  <span class="svc-card-value">{{ f.value }}</span>
                </div>
              </div>
              <div v-if="c.type === 'transfer'" class="svc-card-tip">已为您转接人工客服，请稍候</div>
              <div class="svc-card-action">
                <van-button size="small" plain round color="#e1251b" @click="goCard(c)">
                  {{ c.type === 'transfer' ? '查看我的工单' : (c.action && c.action.label) || '查看' }}
                </van-button>
              </div>
            </div>
          </div>
        </template>

        <!-- 发送中：三个跳动点 -->
        <div v-if="sending" class="svc-row svc-row--ai">
          <div class="svc-bubble svc-bubble--ai svc-bubble--typing">
            正在输入<span class="svc-dots"><i></i><i></i><i></i></span>
          </div>
        </div>
      </div>

      <!-- 底部固定输入栏：快捷问题横向滚动 + 输入框 -->
      <div class="svc-bottom">
        <div v-if="suggestions.length" class="svc-chips">
          <span v-for="s in suggestions" :key="s" class="svc-chip" @click="quickAsk(s)">{{ s }}</span>
        </div>
        <div class="svc-input-row">
          <textarea
            v-model="input"
            class="svc-input"
            rows="1"
            placeholder="请输入您的问题"
            @keydown.enter.exact.prevent="send"
          ></textarea>
          <van-button
            type="danger"
            size="small"
            round
            color="#e1251b"
            :disabled="sending || !input.trim()"
            @click="send"
          >发送</van-button>
          <span class="svc-transfer" @click="transfer">转人工</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { createSession, sendMessage, getHistory, sendFeedback, transferToHuman } from '@/api/aiChat'

// 会话 id 持久化：每个端独立 key，避免与 PC 互相覆盖
const SESSION_KEY = 'mall-shop-ai-session'

const router = useRouter()
const userStore = useUserStore()

const sessionId = ref(localStorage.getItem(SESSION_KEY) || '')
const messages = ref([])
const suggestions = ref([])
const input = ref('')
const sending = ref(false)
const transferring = ref(false)
const list = ref(null)

// 消息任何变化（含打字机逐字更新）都滚到底部，保证最新内容始终可见
watch(messages, () => scrollBottom(), { deep: true })

const goBack = () => {
  // 从悬浮球进入时有历史栈；直接输入网址进入时兜底回首页
  if (window.history.length > 1) router.back()
  else router.replace('/home')
}
const goLogin = () => router.push({ path: '/login', query: { redirect: '/service-chat' } })

/** 首次进入：有历史会话则恢复，失败静默重建 */
const init = () => {
  if (sessionId.value) restore()
  else createSessionFn()
}

const restore = () => {
  getHistory(sessionId.value, { pageNum: 1, pageSize: 50 })
    .then(data => {
      const records = (data && data.records) || []
      messages.value = records.map(r => ({
        id: r.id,
        role: r.role,
        msgType: r.msgType || 'text',
        content: r.content || '',
        // 历史消息直接整段展示，不走打字机
        shown: r.content || '',
        cards: r.cards || [],
        done: true,
        rating: ''
      }))
    })
    .catch(() => {
      // 恢复失败（会话过期/被清理等）静默重建，不报错弹窗（api 层已传 silent 不弹 toast）
      localStorage.removeItem(SESSION_KEY)
      sessionId.value = ''
      createSessionFn()
    })
}

const createSessionFn = () => {
  createSession({ channel: 'h5', source: 'float' })
    .then(data => {
      sessionId.value = data.sessionId
      localStorage.setItem(SESSION_KEY, data.sessionId)
      const g = data.greeting || {}
      messages.value.push({
        id: g.id || 'greet',
        role: 'ai',
        msgType: g.msgType || 'text',
        content: g.content || '您好，我是悦集商城 AI 客服~',
        shown: g.content || '您好，我是悦集商城 AI 客服~',
        cards: g.cards || [],
        done: true,
        rating: ''
      })
      suggestions.value = data.suggestions || []
    })
    .catch(e => {
      pushSystem((e && e.message) || '客服暂不可用，请稍后重试')
    })
}

const send = () => {
  const text = input.value.trim()
  if (!text || sending.value) return
  input.value = ''
  messages.value.push({ id: 'u' + Date.now(), role: 'user', msgType: 'text', content: text, done: true })
  sending.value = true
  sendMessage({ sessionId: sessionId.value, content: text })
    .then(res => {
      typeOut(res)
      if (res && res.suggestions && res.suggestions.length) {
        suggestions.value = res.suggestions
      }
    })
    .catch(e => {
      pushSystem((e && e.message) || '发送失败，请稍后重试')
    })
    .finally(() => {
      sending.value = false
    })
}

const quickAsk = s => {
  if (sending.value) return
  input.value = s
  send()
}

/** AI 回复打字机：定时器先清再建，且页面卸载时清理，避免多个定时器并行串台 */
const twTimer = ref(null)
const clearTimer = () => {
  if (twTimer.value) {
    clearInterval(twTimer.value)
    twTimer.value = null
  }
}
onBeforeUnmount(clearTimer)

const typeOut = res => {
  clearTimer()
  const msg = {
    id: (res && res.messageId) || 'a' + Date.now(),
    role: 'ai',
    msgType: (res && res.msgType) || 'text',
    content: (res && res.content) || '',
    shown: '',
    cards: (res && res.cards) || [],
    done: false,
    rating: ''
  }
  messages.value.push(msg)
  // 必须改「数组里的响应式代理」而不是上面的原始对象：
  // 原始对象变更不会触发 Vue 3 重渲染，气泡会一直空白（v16 走查实测踩中）
  const target = messages.value[messages.value.length - 1]
  const step = Math.max(1, Math.ceil(msg.content.length / 60))
  twTimer.value = setInterval(() => {
    if (target.shown.length >= target.content.length) {
      clearTimer()
      target.done = true
      return
    }
    target.shown = target.content.slice(0, target.shown.length + step)
  }, 24)
}

const feedback = (m, rating) => {
  if (m.rating === rating) return
  m.rating = rating
  sendFeedback({ messageId: m.id, rating }).catch(() => {})
}

const transfer = () => {
  if (transferring.value || sending.value) return
  transferring.value = true
  transferToHuman(sessionId.value)
    .then(data => {
      const tip = data && data.alreadyTransferred ? '您已有进行中的人工工单' : '已为您转接人工客服'
      messages.value.push({
        id: 't' + Date.now(),
        role: 'ai',
        msgType: 'transfer_card',
        content: tip,
        shown: tip,
        cards: [{ type: 'transfer', title: '工单 ' + ((data && data.ticketNo) || ''), fields: [], action: null }],
        done: true,
        rating: ''
      })
    })
    .catch(e => {
      pushSystem((e && e.message) || '转人工失败，请稍后重试')
    })
    .finally(() => {
      transferring.value = false
    })
}

const goCard = c => {
  // 转人工卡片没有 action.url，兜底去「我的」页看工单
  const url = (c.action && c.action.url) || (c.type === 'transfer' ? '/user' : '/order/list')
  router.push(url)
}

const pushSystem = content => {
  messages.value.push({ id: 's' + Date.now(), role: 'system', content })
}

const scrollBottom = () => {
  nextTick(() => {
    const el = list.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

onMounted(() => {
  if (userStore.isLogin) init()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}
.svc-page {
  display: flex;
  flex-direction: column;
}
/* 未登录提示 */
.svc-login-tip {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #969799;
  padding: 60px 0;
}
.svc-login-tip p {
  margin: 12px 0 16px;
}
/* 消息区：底部留出输入栏高度，避免被固定栏遮住 */
.svc-msgs {
  flex: 1;
  overflow-y: auto;
  padding: 14px 12px 90px;
  box-sizing: border-box;
}
.svc-row {
  margin-bottom: 14px;
}
.svc-row--me {
  text-align: right;
}
.svc-row--system {
  text-align: center;
}
.svc-sys {
  display: inline-block;
  color: #e1251b;
  font-size: 12px;
  line-height: 18px;
}
.svc-bubble {
  display: inline-block;
  max-width: 78%;
  padding: 9px 12px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 20px;
  word-break: break-all;
  text-align: left;
  white-space: pre-wrap;
}
.svc-bubble--me {
  background: #e1251b;
  color: #fff;
  border-top-right-radius: 2px;
}
.svc-bubble--ai {
  background: #f0f1f5;
  color: #323233;
  border-top-left-radius: 2px;
}
/* 打字光标 */
.svc-caret {
  display: inline-block;
  width: 2px;
  height: 14px;
  background: #e1251b;
  vertical-align: -2px;
  margin-right: 2px;
  animation: svc-blink 0.8s infinite;
}
@keyframes svc-blink {
  50% {
    opacity: 0;
  }
}
/* 正在输入三个跳动点 */
.svc-bubble--typing {
  color: #969799;
}
.svc-dots {
  display: inline-flex;
  margin-left: 4px;
}
.svc-dots i {
  width: 5px;
  height: 5px;
  margin: 0 2px;
  border-radius: 50%;
  background: #969799;
  animation: svc-jump 1s infinite;
}
.svc-dots i:nth-child(2) {
  animation-delay: 0.2s;
}
.svc-dots i:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes svc-jump {
  0%,
  60%,
  100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-4px);
  }
}
/* 反馈按钮 */
.svc-fb {
  margin-top: 4px;
  font-size: 12px;
  color: #969799;
}
.svc-fb span {
  margin-right: 12px;
}
.svc-fb span.on {
  color: #e1251b;
}
/* 卡片 */
.svc-card {
  display: inline-block;
  width: 82%;
  margin-top: 8px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  text-align: left;
  box-sizing: border-box;
}
.svc-card--transfer {
  background: #fdf3f2;
  border-color: #f5d8d6;
}
.svc-card-title {
  font-weight: 600;
  color: #323233;
  font-size: 13px;
  margin-bottom: 6px;
}
.svc-card-fields {
  margin-bottom: 4px;
}
.svc-card-field {
  display: flex;
  font-size: 12px;
  line-height: 22px;
}
.svc-card-label {
  width: 76px;
  flex-shrink: 0;
  color: #969799;
}
.svc-card-value {
  color: #323233;
  flex: 1;
}
.svc-card-tip {
  font-size: 12px;
  color: #e1251b;
  margin: 4px 0;
}
.svc-card-action {
  text-align: right;
  margin-top: 6px;
}
/* 底部固定输入栏：env 兜底 iPhone 安全区；fixed 布局在软键盘弹起时随页面收缩 */
.svc-bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  padding-bottom: env(safe-area-inset-bottom);
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.04);
  z-index: 10;
}
/* 快捷问题横向滚动 */
.svc-chips {
  display: flex;
  overflow-x: auto;
  padding: 8px 12px 0;
}
.svc-chip {
  flex-shrink: 0;
  margin-right: 8px;
  padding: 4px 12px;
  border: 1px solid #f0c4c1;
  border-radius: 14px;
  color: #e1251b;
  font-size: 12px;
  white-space: nowrap;
}
.svc-chip:active {
  background: #fdf3f2;
}
.svc-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
}
.svc-input {
  flex: 1;
  border: none;
  outline: none;
  background: #f5f6f8;
  border-radius: 16px;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 18px;
  resize: none;
  font-family: inherit;
}
.svc-transfer {
  flex-shrink: 0;
  font-size: 12px;
  color: #969799;
}
</style>
