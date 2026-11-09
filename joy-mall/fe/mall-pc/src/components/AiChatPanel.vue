<template>
  <div class="ai-chat-root">
    <!-- 悬浮客服球：未登录也展示，点开后在面板内提示登录 -->
    <div v-show="!open" class="ai-ball" title="在线客服" @click="openPanel">
      <i class="el-icon-service"></i>
    </div>

    <!-- 滑出聊天面板 -->
    <transition name="ai-slide">
      <div v-show="open" class="ai-panel">
        <!-- 标题栏 -->
        <div class="ai-head">
          <span class="ai-head-title"><i class="el-icon-service"></i> AI 客服 · 悦集商城</span>
          <span class="ai-head-btns">
            <i class="el-icon-minus" title="最小化" @click="open = false"></i>
            <i class="el-icon-close" title="关闭" @click="open = false"></i>
          </span>
        </div>

        <!-- 未登录：不发任何请求 -->
        <div v-if="!isLogin" class="ai-login-tip">
          <i class="el-icon-lock"></i>
          <p>登录后可使用在线客服</p>
          <el-button type="danger" size="small" round @click="goLogin">去登录</el-button>
        </div>

        <template v-else>
          <!-- 消息区 -->
          <div ref="list" class="ai-msgs">
            <div v-for="m in messages" :key="m.id" class="ai-row" :class="'ai-row--' + m.role">
              <!-- 系统提示：失败/转人工等，红色小字居中，不用弹窗 -->
              <div v-if="m.role === 'system'" class="ai-sys">{{ m.content }}</div>

              <template v-else>
                <div class="ai-bubble" :class="m.role === 'user' ? 'ai-bubble--me' : 'ai-bubble--ai'">
                  <span v-if="m.role === 'ai' && !m.done" class="ai-caret"></span>{{ m.role === 'ai' ? m.shown : m.content }}
                </div>

                <!-- 有用 / 没用 反馈（仅 AI 文本消息，回答完成后出现） -->
                <div v-if="m.role === 'ai' && m.done && m.content" class="ai-fb">
                  <span :class="{ on: m.rating === 'up' }" @click="feedback(m, 'up')">👍 有用</span>
                  <span :class="{ on: m.rating === 'down' }" @click="feedback(m, 'down')">👎 没用</span>
                </div>

                <!-- 卡片：order/refund 小卡，transfer 提示条 -->
                <div v-for="(c, i) in m.cards" :key="m.id + '-c' + i" class="ai-card" :class="{ 'ai-card--transfer': c.type === 'transfer' }">
                  <div v-if="c.title" class="ai-card-title">{{ c.title }}</div>
                  <div v-if="c.fields && c.fields.length" class="ai-card-fields">
                    <div v-for="f in c.fields" :key="f.label" class="ai-card-field">
                      <span class="ai-card-label">{{ f.label }}</span>
                      <span class="ai-card-value">{{ f.value }}</span>
                    </div>
                  </div>
                  <div v-if="c.type === 'transfer'" class="ai-card-tip">已为您转接人工客服，请稍候</div>
                  <div class="ai-card-action">
                    <el-button size="mini" type="danger" plain round @click="goCard(c)">
                      {{ c.type === 'transfer' ? '查看我的工单' : (c.action && c.action.label) || '查看' }}
                    </el-button>
                  </div>
                </div>
              </template>
            </div>

            <!-- 发送中：三个跳动点 -->
            <div v-if="sending" class="ai-row ai-row--ai">
              <div class="ai-bubble ai-bubble--ai ai-bubble--typing">
                正在输入<span class="ai-dots"><i></i><i></i><i></i></span>
              </div>
            </div>
          </div>

          <!-- 快捷问题 chips -->
          <div v-if="suggestions.length" class="ai-chips">
            <span v-for="s in suggestions" :key="s" class="ai-chip" @click="quickAsk(s)">{{ s }}</span>
          </div>

          <!-- 输入区：Enter 发送，Shift+Enter 换行 -->
          <div class="ai-input-row">
            <el-input
              v-model="input"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="请输入您的问题，Enter 发送"
              @keydown.enter.exact.prevent="send"
            ></el-input>
            <el-button type="danger" :disabled="sending || !input.trim()" @click="send">发送</el-button>
          </div>

          <div class="ai-foot">
            <span class="ai-transfer" @click="transfer">转人工</span>
          </div>
        </template>
      </div>
    </transition>
  </div>
</template>

<script>
import store from '@/store'
import { createSession, sendMessage, getHistory, sendFeedback, transferToHuman } from '@/api/aiChat'

// 会话 id 持久化：每个端独立 key，避免与 H5 互相覆盖
const SESSION_KEY = 'mall-pc-ai-session'

export default {
  name: 'AiChatPanel',
  data() {
    return {
      open: false,
      sessionId: localStorage.getItem(SESSION_KEY) || '',
      messages: [],
      suggestions: [],
      input: '',
      sending: false,
      transferring: false
    }
  },
  computed: {
    // store.state 是 Vue.observable，登出/登录后面板状态会自动刷新
    isLogin() {
      return !!store.state.token
    }
  },
  watch: {
    // 消息任何变化（含打字机逐字更新）都滚到底部，保证最新内容始终可见
    messages: {
      deep: true,
      handler() {
        this.scrollBottom()
      }
    }
  },
  beforeDestroy() {
    // 打字机定时器必须在组件销毁前清理，否则定时器会继续改已卸载的数据造成内存泄漏
    this.clearTimer()
  },
  methods: {
    openPanel() {
      this.open = true
      if (this.isLogin && !this.messages.length) {
        this.init()
      }
    },
    /** 首次打开：有历史会话则恢复，失败静默重建 */
    init() {
      if (this.sessionId) {
        this.restore()
      } else {
        this.createSession()
      }
    },
    restore() {
      getHistory(this.sessionId, { pageNum: 1, pageSize: 50 })
        .then(data => {
          const records = (data && data.records) || []
          this.messages = records.map(r => ({
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
          this.scrollBottom()
        })
        .catch(() => {
          // 恢复失败（会话过期/被清理等）静默重建，不报错弹窗（api 层已传 silent 不弹 Message）
          localStorage.removeItem(SESSION_KEY)
          this.sessionId = ''
          this.createSession()
        })
    },
    createSession() {
      createSession({ channel: 'pc', source: 'float' })
        .then(data => {
          this.sessionId = data.sessionId
          localStorage.setItem(SESSION_KEY, this.sessionId)
          const g = data.greeting || {}
          this.messages.push({
            id: g.id || 'greet',
            role: 'ai',
            msgType: g.msgType || 'text',
            content: g.content || '您好，我是悦集商城 AI 客服~',
            shown: g.content || '您好，我是悦集商城 AI 客服~',
            cards: g.cards || [],
            done: true,
            rating: ''
          })
          this.suggestions = data.suggestions || []
        })
        .catch(e => {
          this.pushSystem((e && e.message) || '客服暂不可用，请稍后重试')
        })
    },
    send() {
      const text = this.input.trim()
      if (!text || this.sending) return
      this.input = ''
      this.messages.push({ id: 'u' + Date.now(), role: 'user', msgType: 'text', content: text, done: true })
      this.sending = true
      sendMessage({ sessionId: this.sessionId, content: text })
        .then(res => {
          this.typeOut(res)
          if (res && res.suggestions && res.suggestions.length) {
            this.suggestions = res.suggestions
          }
        })
        .catch(e => {
          this.pushSystem((e && e.message) || '发送失败，请稍后重试')
        })
        .finally(() => {
          this.sending = false
        })
    },
    quickAsk(s) {
      if (this.sending) return
      this.input = s
      this.send()
    },
    /** AI 回复打字机：定时器先清再建，且组件销毁时清理，避免多个定时器并行串台 */
    typeOut(res) {
      this.clearTimer()
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
      this.messages.push(msg)
      // 必须改「数组里的响应式代理」而不是上面的原始对象：原始对象变更不触发重渲染
      const target = this.messages[this.messages.length - 1]
      const step = Math.max(1, Math.ceil(msg.content.length / 60))
      this._twTimer = setInterval(() => {
        if (target.shown.length >= target.content.length) {
          this.clearTimer()
          target.done = true
          return
        }
        target.shown = target.content.slice(0, target.shown.length + step)
      }, 24)
    },
    clearTimer() {
      if (this._twTimer) {
        clearInterval(this._twTimer)
        this._twTimer = null
      }
    },
    feedback(m, rating) {
      if (m.rating === rating) return
      m.rating = rating
      sendFeedback({ messageId: m.id, rating }).catch(() => {})
    },
    transfer() {
      if (this.transferring || this.sending) return
      this.transferring = true
      transferToHuman(this.sessionId)
        .then(data => {
          const tip = data && data.alreadyTransferred ? '您已有进行中的人工工单' : '已为您转接人工客服'
          this.messages.push({
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
          this.pushSystem((e && e.message) || '转人工失败，请稍后重试')
        })
        .finally(() => {
          this.transferring = false
        })
    },
    goCard(c) {
      // 转人工卡片没有 action.url，兜底去「我的」页看工单
      const url = (c.action && c.action.url) || (c.type === 'transfer' ? '/user' : '/order/list')
      this.$router.push(url)
    },
    goLogin() {
      this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } })
    },
    pushSystem(content) {
      this.messages.push({ id: 's' + Date.now(), role: 'system', content })
    },
    scrollBottom() {
      this.$nextTick(() => {
        const el = this.$refs.list
        if (el) {
          el.scrollTop = el.scrollHeight
        }
      })
    }
  }
}
</script>

<style scoped>
.ai-chat-root {
  font-size: 14px;
}
/* 悬浮球 */
.ai-ball {
  position: fixed;
  right: 24px;
  bottom: 80px;
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: #e1251b;
  color: #fff;
  font-size: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(225, 37, 27, 0.4);
  z-index: 2001;
  transition: transform 0.2s;
}
.ai-ball:hover {
  transform: scale(1.08);
}
/* 面板 */
.ai-panel {
  position: fixed;
  right: 24px;
  bottom: 110px;
  width: 380px;
  height: 620px;
  max-height: calc(100vh - 140px);
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  z-index: 2001;
  overflow: hidden;
}
.ai-slide-enter-active,
.ai-slide-leave-active {
  transition: all 0.25s ease;
}
.ai-slide-enter,
.ai-slide-leave-to {
  opacity: 0;
  transform: translateX(24px);
}
/* 标题栏 */
.ai-head {
  height: 46px;
  flex-shrink: 0;
  background: #e1251b;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
}
.ai-head-title i {
  margin-right: 6px;
}
.ai-head-btns i {
  margin-left: 12px;
  cursor: pointer;
  font-size: 16px;
  opacity: 0.9;
}
.ai-head-btns i:hover {
  opacity: 1;
}
/* 未登录提示 */
.ai-login-tip {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
}
.ai-login-tip i {
  font-size: 40px;
  margin-bottom: 12px;
}
.ai-login-tip p {
  margin: 0 0 16px;
}
/* 消息区 */
.ai-msgs {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  background: #f7f8fa;
}
.ai-row {
  margin-bottom: 14px;
}
.ai-row--user {
  text-align: right;
}
.ai-row--system {
  text-align: center;
}
.ai-sys {
  display: inline-block;
  color: #e1251b;
  font-size: 12px;
  line-height: 18px;
}
.ai-bubble {
  display: inline-block;
  max-width: 78%;
  padding: 9px 12px;
  border-radius: 10px;
  line-height: 20px;
  word-break: break-all;
  text-align: left;
  white-space: pre-wrap;
}
.ai-bubble--me {
  background: #e1251b;
  color: #fff;
  border-top-right-radius: 2px;
}
.ai-bubble--ai {
  background: #f0f1f5;
  color: #303133;
  border-top-left-radius: 2px;
}
/* 打字光标 */
.ai-caret {
  display: inline-block;
  width: 2px;
  height: 14px;
  background: #e1251b;
  vertical-align: -2px;
  margin-right: 2px;
  animation: ai-blink 0.8s infinite;
}
@keyframes ai-blink {
  50% {
    opacity: 0;
  }
}
/* 正在输入三个跳动点 */
.ai-bubble--typing {
  color: #909399;
}
.ai-dots {
  display: inline-flex;
  margin-left: 4px;
}
.ai-dots i {
  width: 5px;
  height: 5px;
  margin: 0 2px;
  border-radius: 50%;
  background: #909399;
  animation: ai-jump 1s infinite;
}
.ai-dots i:nth-child(2) {
  animation-delay: 0.2s;
}
.ai-dots i:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes ai-jump {
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
.ai-fb {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.ai-fb span {
  margin-right: 12px;
  cursor: pointer;
}
.ai-fb span.on {
  color: #e1251b;
}
/* 卡片 */
.ai-card {
  display: inline-block;
  width: 78%;
  margin-top: 8px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  text-align: left;
  box-sizing: border-box;
}
.ai-card--transfer {
  background: #fdf3f2;
  border-color: #f5d8d6;
}
.ai-card-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.ai-card-fields {
  margin-bottom: 4px;
}
.ai-card-field {
  display: flex;
  font-size: 12px;
  line-height: 22px;
}
.ai-card-label {
  width: 76px;
  flex-shrink: 0;
  color: #909399;
}
.ai-card-value {
  color: #303133;
  flex: 1;
}
.ai-card-tip {
  font-size: 12px;
  color: #e1251b;
  margin: 4px 0;
}
.ai-card-action {
  text-align: right;
  margin-top: 6px;
}
/* 快捷问题 */
.ai-chips {
  flex-shrink: 0;
  display: flex;
  overflow-x: auto;
  padding: 8px 12px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
}
.ai-chip {
  flex-shrink: 0;
  margin-right: 8px;
  padding: 4px 12px;
  border: 1px solid #f0c4c1;
  border-radius: 14px;
  color: #e1251b;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.ai-chip:hover {
  background: #fdf3f2;
}
/* 输入区 */
.ai-input-row {
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 12px;
  background: #fff;
}
.ai-input-row .el-button {
  flex-shrink: 0;
}
.ai-foot {
  flex-shrink: 0;
  padding: 0 12px 8px;
  background: #fff;
}
.ai-transfer {
  font-size: 12px;
  color: #909399;
  cursor: pointer;
}
.ai-transfer:hover {
  color: #e1251b;
}
</style>
