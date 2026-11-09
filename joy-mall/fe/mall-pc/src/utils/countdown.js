/** 活动倒计时 mixin：组件里提供 endTime（或开始时间场景自行覆盖 remainingMs） */
export default {
  data() {
    return { now: Date.now(), timer: null }
  },
  computed: {
    remainingMs() {
      if (!this.endTime) return 0
      const end = new Date(String(this.endTime).replace('T', ' ')).getTime()
      return Math.max(0, end - this.now)
    },
    countdownText() {
      const ms = this.remainingMs
      if (ms <= 0) return '已结束'
      const s = Math.floor(ms / 1000)
      const pad = n => String(n).padStart(2, '0')
      return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`
    }
  },
  mounted() {
    this.timer = setInterval(() => { this.now = Date.now() }, 1000)
  },
  beforeDestroy() {
    if (this.timer) clearInterval(this.timer)
  }
}
