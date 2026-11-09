// 后台全页面诊断：逐个导航 → 收集 console error / 未捕获异常 / 失败请求 / 页面错误提示
// 用法：node diag-admin.mjs
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const WEB = process.env.MALL_WEB_URL || 'http://127.0.0.1:9431'
const SHOTS = process.env.MALL_SHOTS_DIR || 'D:/workspace/project/artifacts/diag'
mkdirSync(SHOTS, { recursive: true })
const sleep = ms => new Promise(r => setTimeout(r, ms))

async function post(base, path, body) {
  const res = await fetch(base + path, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body || {})
  })
  try { return await res.json() } catch { return {} }
}

// ── 平台管理员登录
const cap = await (await fetch(WEB + '/dev-api/api/mall/auth/captcha')).json()
const answer = cap && cap.data && cap.data.answer
if (!answer) { console.error('取不到验证码答案，需 AUTH_CAPTCHA_EXPOSE_ANSWER=true'); process.exit(2) }
const loginRes = await post(WEB, '/dev-api/api/mall/auth/login', {
  username: 'admin', password: 'admin123',
  captchaId: cap.data.captchaId, captchaAnswer: String(answer)
})
const ADMIN_TOKEN = (loginRes.data && (loginRes.data.token || loginRes.data.accessToken))
if (!ADMIN_TOKEN) { console.error('后台登录失败 ' + JSON.stringify(loginRes).slice(0, 300)); process.exit(2) }
console.log('admin token ok')

// ── 商家登录（V11 独立令牌）
let MERCHANT_TOKEN = ''
const mRes = await post(WEB, '/dev-api/merchant-admin/auth/login', { username: 'merchant', password: '123456' })
if (mRes && mRes.data && (mRes.data.token || mRes.data.accessToken)) {
  MERCHANT_TOKEN = mRes.data.token || mRes.data.accessToken
  console.log('merchant token ok')
} else {
  console.log('merchant login 失败: ' + JSON.stringify(mRes).slice(0, 200))
}

const pages = [
  { name: '首页', url: '/#/home' },
  { name: '经营看板', url: '/#/mall/dashboard/mall_dashboard/index' },
  { name: '商品类目', url: '/#/mall/product/mall_category/list' },
  { name: '商品', url: '/#/mall/product/mall_goods/list' },
  { name: '商品SKU', url: '/#/mall/product/mall_sku/list' },
  { name: '品牌', url: '/#/mall/product/mall_brand/list' },
  { name: '评价管理', url: '/#/mall/product/mall_review/list' },
  { name: '订单', url: '/#/mall/order/mall_order/list' },
  { name: '订单流转日志', url: '/#/mall/order/mall_order_log/list' },
  { name: '售后退款单', url: '/#/mall/order/mall_refund/list' },
  { name: '会员', url: '/#/mall/member/mall_member/list' },
  { name: '收货地址', url: '/#/mall/member/mall_address/list' },
  { name: '站内消息', url: '/#/mall/member/mall_notice/list' },
  { name: '优惠券', url: '/#/mall/marketing/mall_coupon/list' },
  { name: '领券记录', url: '/#/mall/marketing/mall_coupon_record/list' },
  { name: '轮播图', url: '/#/mall/marketing/mall_banner/list' },
  { name: '促销活动', url: '/#/mall/marketing/mall_promotion/list' },
  { name: '会员等级', url: '/#/mall/marketing/mall_member_level/list' },
  { name: '商家入驻', url: '/#/mall/merchant/mall_merchant/list' },
  { name: '商家结算', url: '/#/mall/merchant/mall_merchant_settlement/list' },
  { name: '商家提现', url: '/#/mall/merchant/mall_merchant_withdrawal/list' },
  { name: '运费模板', url: '/#/mall/trade/mall_freight_template/list' },
  { name: '发货单', url: '/#/mall/trade/mall_delivery/list' },
  { name: '发票管理', url: '/#/mall/trade/mall_invoice/list' },
  { name: '支付流水', url: '/#/mall/trade/mall_payment/list' },
  { name: '支付对账', url: '/#/mall/trade/mall_pay_reconcile/list' },
  { name: '客服工单', url: '/#/mall/service/mall_ticket/list' },
  { name: '消息触达', url: '/#/mall/service/mall_message/index' }
]

const merchantPages = [
  { name: '商家-经营概览', url: '/#/merchant/overview' },
  { name: '商家-商品管理', url: '/#/merchant/goods' },
  { name: '商家-订单与发货', url: '/#/merchant/order' },
  { name: '商家-售后审核', url: '/#/merchant/refund' },
  { name: '商家-结算与提现', url: '/#/merchant/fund' }
]

// ---------- Chrome ----------
const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
const dir = mkdtempSync(tmpdir() + '/diag-')
const port = 9811
const chrome = spawn(exe, [`--user-data-dir=${dir}`, '--headless=new', `--remote-debugging-port=${port}`,
  '--no-first-run', '--no-default-browser-check', '--window-size=1440,900', '--hide-scrollbars',
  '--disable-features=IsolateOrigins,site-per-process', 'about:blank'], { stdio: 'ignore' })
await sleep(2600)
const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
const target = list.find(t => t.type === 'page') || list[0]
const ws = new WebSocket(target.webSocketDebuggerUrl)
await new Promise(r => { ws.onopen = r })

let seq = 0
const pending = new Map()
const listeners = []
ws.onmessage = ev => {
  const msg = JSON.parse(ev.data)
  if (msg.id && pending.has(msg.id)) {
    const { res, rej } = pending.get(msg.id); pending.delete(msg.id)
    msg.error ? rej(new Error(msg.error.message)) : res(msg.result)
  } else if (msg.method) listeners.forEach(fn => fn(msg))
}
const call = (method, params = {}) => new Promise((res, rej) => {
  const mid = ++seq; pending.set(mid, { res, rej })
  ws.send(JSON.stringify({ id: mid, method, params }))
})

await call('Page.enable')
await call('Runtime.enable')
await call('Log.enable')
await call('Network.enable')

// 先注入平台令牌
await call('Page.navigate', { url: WEB + '/#/login' })
await sleep(1500)
await call('Runtime.evaluate', { expression: `localStorage.setItem('token','${ADMIN_TOKEN}');1` })

let bucket = { errors: [], failed: [], exceptions: [] }
listeners.push(msg => {
  if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
    bucket.errors.push(msg.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 400))
  }
  if (msg.method === 'Runtime.exceptionThrown') {
    const d = msg.params.exceptionDetails
    bucket.exceptions.push((d.exception && d.exception.description || d.text || '').slice(0, 400))
  }
  if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') {
    bucket.errors.push('[log] ' + msg.params.entry.text.slice(0, 300))
  }
  if (msg.method === 'Network.responseReceived') {
    const r = msg.params.response
    if (r.status >= 400 && r.url.indexOf('hot-update') < 0) {
      bucket.failed.push(r.status + ' ' + r.url.replace(WEB, '').slice(0, 180))
    }
  }
})

const results = []
async function visit(p, kind) {
  bucket = { errors: [], failed: [], exceptions: [] }
  await call('Page.navigate', { url: WEB + p.url })
  await sleep(kind === 'merchant' ? 2600 : 2400)
  const chk = await call('Runtime.evaluate', {
    expression: `(() => {
      const app = document.querySelector('#app')
      const rows = document.querySelectorAll('.el-table__row').length
      const btns = document.querySelectorAll('.el-button').length
      const text = (document.body.innerText || '').replace(/\\s+/g, ' ').slice(0, 300)
      return JSON.stringify({ appHtml: app ? app.innerHTML.length : 0, rows, btns, text })
    })()`,
    returnByValue: true
  })
  let info = {}
  try { info = JSON.parse(chk.result.value) } catch {}
  const shot = await call('Page.captureScreenshot', { format: 'png' })
  const file = `${SHOTS}/${kind}-${p.name}.png`
  writeFileSync(file, Buffer.from(shot.data, 'base64'))
  results.push({ ...p, kind, ...info, ...bucket, shot: file })
  console.log(`[${kind}] ${p.name} rows=${info.rows} err=${bucket.errors.length} exc=${bucket.exceptions.length} fail=${bucket.failed.length}`)
}

for (const p of pages) await visit(p, 'admin')

// 切商家令牌
if (MERCHANT_TOKEN) {
  await call('Runtime.evaluate', { expression: `localStorage.setItem('merchantToken','${MERCHANT_TOKEN}');1` })
  for (const p of merchantPages) await visit(p, 'merchant')
}

writeFileSync(SHOTS + '/report.json', JSON.stringify(results, null, 2))
console.log('\n===== 有问题的页面 =====')
for (const r of results) {
  if (!r.errors.length && !r.exceptions.length && !r.failed.length && r.appHtml > 200) continue
  console.log(`\n### [${r.kind}] ${r.name}  ${r.url}`)
  if (r.appHtml <= 200) console.log('  !! 白屏 appHtml=' + r.appHtml)
  r.exceptions.slice(0, 3).forEach(e => console.log('  EXC ' + e.replace(/\n/g, ' | ').slice(0, 300)))
  r.errors.slice(0, 5).forEach(e => console.log('  ERR ' + e.replace(/\n/g, ' | ').slice(0, 300)))
  r.failed.slice(0, 6).forEach(e => console.log('  NET ' + e))
}
chrome.kill()
process.exit(0)
