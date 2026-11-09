// 操作手册截图采集：V14 商家端 / 促销引擎 / 工单消息 + V15 促销 C 端展示 / 评价增强
// 用法：node scripts/capture-manual-shots.mjs          采全部
//       node scripts/capture-manual-shots.mjs --only=admin-22,merchant-02
// 前置：后端 10 服务 + 三端前端已启动（start-local.sh / start-fe.sh）
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const WEB = 'http://127.0.0.1:9431'
const H5 = 'http://127.0.0.1:9531'
const PC = 'http://127.0.0.1:9432'
const GW = 'http://127.0.0.1:8080'
const OUT = 'D:/workspace/project/docs/系统功能手册/images'
mkdirSync(OUT, { recursive: true })
const sleep = ms => new Promise(r => setTimeout(r, ms))

const only = (process.argv.find(a => a.startsWith('--only=')) || '').slice(7).split(',').filter(Boolean)
const want = name => !only.length || only.some(o => name.startsWith(o))

const jpost = async (url, body, headers = {}) => {
  const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body || {}) })
  try { return await r.json() } catch { return {} }
}

// ---- 三方登录态 ----
const cap = await (await fetch(WEB + '/dev-api/api/mall/auth/captcha')).json()
const al = await jpost(WEB + '/dev-api/api/mall/auth/login', { username: 'admin', password: 'admin123', captchaId: cap.data.captchaId, captchaAnswer: String(cap.data.answer) })
const ADMIN = al.data && (al.data.token || al.data.accessToken)
if (!ADMIN) { console.error('admin login failed', JSON.stringify(al).slice(0, 200)); process.exit(2) }
const ml = await jpost(GW + '/shop/auth/login', { phone: '13800000001', password: '123456' })
const MEMBER = ml.data && (ml.data.token || ml.data.accessToken)
if (!MEMBER) { console.error('member login failed', JSON.stringify(ml).slice(0, 200)); process.exit(2) }
const mcl = await jpost(GW + '/merchant-admin/auth/login', { username: 'merchant', password: '123456' })
const MERCHANT = mcl.data && mcl.data.token
if (!MERCHANT) { console.error('merchant login failed', JSON.stringify(mcl).slice(0, 200)); process.exit(2) }
// 商家端布局从 localStorage 读店铺与用户信息，模拟登录必须一并写入
const MERCHANT_INFO = JSON.stringify((mcl.data && mcl.data.merchant) || {})
const MERCHANT_USER = JSON.stringify((mcl.data && mcl.data.user) || {})
console.log('tokens ok: admin/member/merchant')

// ---- 无头 Chrome ----
const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
const chrome = spawn(exe, [`--user-data-dir=${mkdtempSync(tmpdir() + '/manual-')}`, '--headless=new', '--remote-debugging-port=9835',
  '--no-first-run', '--window-size=1440,900', '--hide-scrollbars', '--force-device-scale-factor=1',
  '--font-render-hinting=none', 'about:blank'], { stdio: 'ignore' })
await sleep(2600)
const list = await (await fetch('http://127.0.0.1:9835/json/list')).json()
const ws = new WebSocket((list.find(t => t.type === 'page') || list[0]).webSocketDebuggerUrl)
await new Promise(r => { ws.onopen = r })
let seq = 0; const pending = new Map()
ws.onmessage = ev => {
  const m = JSON.parse(ev.data)
  if (m.id && pending.has(m.id)) { const { res, rej } = pending.get(m.id); pending.delete(m.id); m.error ? rej(new Error(m.error.message)) : res(m.result) }
}
const call = (method, params = {}) => new Promise((res, rej) => { const id = ++seq; pending.set(id, { res, rej }); ws.send(JSON.stringify({ id, method, params })) })
await call('Page.enable'); await call('Runtime.enable')

const evaluate = async expr => {
  const r = await call('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
  return r.result && r.result.value
}

// 视口：desktop 1440x900 / h5 390x844@2x
async function viewport(kind) {
  if (kind === 'h5') {
    await call('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true })
  } else {
    await call('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
  }
}

async function shot(name, { base, url, wait = 2600, kind = 'desktop', sidebar = '', full = false, before = '' }) {
  if (!want(name)) return
  await viewport(kind)
  await call('Page.navigate', { url: base + url })
  await sleep(wait)
  if (sidebar) {
    await evaluate(`(() => {
      const items = [...document.querySelectorAll('.sub-item')]
      const it = items.find(e => (e.innerText || '').trim().includes(${JSON.stringify(sidebar)}))
      if (!it) return 'notfound'
      const grp = it.closest('.nav-item'), list = it.closest('.sub-list')
      if (list && getComputedStyle(list).display === 'none' && grp) grp.click()
      return 'ok'
    })()`)
    await sleep(500)
  }
  if (before) { await evaluate(before); await sleep(900) }
  const height = full ? Math.min(await evaluate('Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)') || 900, 2600) : 0
  const params = { format: 'png' }
  if (full) params.clip = { x: 0, y: 0, width: kind === 'h5' ? 390 : 1440, height, scale: 1 }
  const png = await call('Page.captureScreenshot', params)
  writeFileSync(`${OUT}/${name}.png`, Buffer.from(png.data, 'base64'))
  const app = await evaluate(`(document.querySelector('#app') || document.body).innerText.replace(/\\s+/g,' ').slice(0,70)`)
  console.log(`[${name}] ${height || (kind === 'h5' ? 844 : 900)}px  ${app}`)
}

// 把令牌写进各自域名，并清掉其它旧值避免串号
await call('Page.navigate', { url: WEB + '/#/login' }); await sleep(1200)
await evaluate(`localStorage.removeItem('merchantToken');localStorage.setItem('token','${ADMIN}');1`)
await call('Page.navigate', { url: H5 + '/' }); await sleep(1200)
await evaluate(`localStorage.setItem('mall-shop-token','${MEMBER}');1`)
await call('Page.navigate', { url: PC + '/' }); await sleep(1200)
await evaluate(`localStorage.setItem('mall-pc-token','${MEMBER}');1`)
await call('Page.navigate', { url: WEB + '/#/merchant/login' }); await sleep(1200)
await evaluate(`localStorage.setItem('merchantToken','${MERCHANT}');localStorage.setItem('merchant',${JSON.stringify(MERCHANT_INFO)});localStorage.setItem('merchantUser',${JSON.stringify(MERCHANT_USER)});1`)

// ── 后台：覆盖重截（菜单已改二级下拉 + V15 列） ──
await shot('admin-02-dashboard', { base: WEB, url: '/#/mall/dashboard/mall_dashboard/index', sidebar: '经营看板' })
await shot('admin-08-refund', { base: WEB, url: '/#/mall/order/mall_refund/list', sidebar: '售后退款单' })
await shot('admin-11-review', { base: WEB, url: '/#/mall/product/mall_review/list', sidebar: '评价管理' })

// ── 后台：V14/V15 新增页 ──
const ADMIN_PAGES = [
  ['admin-22-promotion', '/#/mall/marketing/mall_promotion/list', '促销活动'],
  ['admin-23-member-level', '/#/mall/marketing/mall_member_level/list', '会员等级'],
  ['admin-24-seckill', '/#/mall/marketing/mall_seckill/list', '秒杀活动'],
  ['admin-25-group', '/#/mall/marketing/mall_group/list', '拼团活动'],
  ['admin-26-lottery', '/#/mall/marketing/mall_lottery/list', '抽奖活动'],
  ['admin-27-distribution', '/#/mall/marketing/mall_distribution/list', '分销管理'],
  ['admin-28-ticket', '/#/mall/service/mall_ticket/list', '客服工单'],
  ['admin-29-message', '/#/mall/service/mall_message/index', '消息触达']
]
for (const [name, url, sb] of ADMIN_PAGES) await shot(name, { base: WEB, url, sidebar: sb })

// ── 商家端（同一 9431，/merchant/** 独立会话） ──
await shot('merchant-01-login', { base: WEB, url: '/#/merchant/login', before: "localStorage.removeItem('merchantToken')" })
await evaluate(`localStorage.setItem('merchantToken','${MERCHANT}');localStorage.setItem('merchant',${JSON.stringify(MERCHANT_INFO)});localStorage.setItem('merchantUser',${JSON.stringify(MERCHANT_USER)});1`)
const MERCHANT_PAGES = [
  ['merchant-02-overview', 'overview'],
  ['merchant-03-goods', 'goods'],
  ['merchant-04-order', 'order'],
  ['merchant-05-refund', 'refund'],
  ['merchant-06-fund', 'fund']
]
for (const [name, p] of MERCHANT_PAGES) await shot(name, { base: WEB, url: '/#/merchant/' + p, wait: 2800 })

// ── PC ──
await shot('pc-03-detail', { base: PC, url: '/goods/1001', wait: 3000 })
await shot('pc-15-seckill', { base: PC, url: '/seckill' })
await shot('pc-16-group', { base: PC, url: '/group' })
await shot('pc-17-lottery', { base: PC, url: '/lottery' })
await shot('pc-18-distribution', { base: PC, url: '/distribution', wait: 3000 })
await shot('pc-19-confirm', { base: PC, url: '/order/confirm?skuId=2001&num=1', wait: 3200, full: true })

// ── H5（390 视口，长图） ──
await shot('h5-03-detail', { base: H5, url: '/goods/1001', kind: 'h5', wait: 3000, full: true })
await shot('h5-17-seckill', { base: H5, url: '/seckill', kind: 'h5', full: true })
await shot('h5-18-group', { base: H5, url: '/group', kind: 'h5', full: true })
await shot('h5-19-lottery', { base: H5, url: '/lottery', kind: 'h5', full: true })
await shot('h5-20-distribution', { base: H5, url: '/distribution', kind: 'h5', wait: 3000, full: true })
await shot('h5-21-review-my', { base: H5, url: '/review/my', kind: 'h5', wait: 3000, full: true })
await shot('h5-22-confirm', { base: H5, url: '/order/confirm?skuId=2001&num=1', kind: 'h5', wait: 3200, full: true })

chrome.kill()
console.log('\ndone ->', OUT)
