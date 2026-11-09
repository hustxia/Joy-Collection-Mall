// 微服务工程 × 三端前端 浏览器走查（原生 CDP，无第三方依赖）
// 每个页面：注入登录态 → 导航 → 等渲染 → 断言页面文案/表格行/图片 → 收集 console error、异常、失败请求 → 截图
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const SHOTS = process.env.MALL_SHOTS_DIR
  || 'C:/Users/xiayuhong/WorkBuddy/2026-10-20-14-50-30/shots/mall-cloud'
mkdirSync(SHOTS, { recursive: true })
const sleep = ms => new Promise(r => setTimeout(r, ms))

// 地址与账号全部可配置，token 优先外部传入，否则脚本自己登录
const ADMIN = process.env.MALL_WEB_URL || 'http://127.0.0.1:9431'
const PC = process.env.MALL_PC_URL || 'http://127.0.0.1:9432'
const H5 = process.env.MALL_H5_URL || 'http://localhost:9531'
const MEMBER_PHONE = process.env.MALL_MEMBER_PHONE || '13800000001'
const MEMBER_PASSWORD = process.env.MALL_MEMBER_PASSWORD || '123456'
const ADMIN_USERNAME = process.env.MALL_ADMIN_USERNAME || 'admin'
const ADMIN_PASSWORD = process.env.MALL_ADMIN_PASSWORD || 'admin123'

async function post(base, path, body) {
  const res = await fetch(base + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body || {})
  })
  try { return await res.json() } catch { return {} }
}

async function loginAdmin() {
  const preset = process.argv[2] || process.env.MALL_ADMIN_TOKEN
  if (preset) return preset
  const cap = await (await fetch(ADMIN + '/dev-api/api/mall/auth/captcha')).json()
  const answer = cap && cap.data && cap.data.answer
  if (!answer) {
    console.error('取不到验证码答案：目标环境需 AUTH_CAPTCHA_EXPOSE_ANSWER=true，或传 MALL_ADMIN_TOKEN')
    process.exit(2)
  }
  const res = await post(ADMIN, '/dev-api/api/mall/auth/login', {
    username: ADMIN_USERNAME, password: ADMIN_PASSWORD,
    captchaId: cap.data.captchaId, captchaAnswer: String(answer)
  })
  const token = res && res.data && (res.data.token || res.data.accessToken)
  if (!token) { console.error('后台登录失败：' + JSON.stringify(res).slice(0, 200)); process.exit(2) }
  return token
}

async function loginMember() {
  const preset = process.argv[3] || process.env.MALL_MEMBER_TOKEN
  if (preset) return preset
  const res = await post(H5, '/api/shop/auth/login', { phone: MEMBER_PHONE, password: MEMBER_PASSWORD })
  const token = res && res.data && (res.data.token || res.data.accessToken)
  if (!token) { console.error('会员登录失败：' + JSON.stringify(res).slice(0, 200)); process.exit(2) }
  return token
}

const ADMIN_TOKEN = await loginAdmin()
const MEMBER_TOKEN = await loginMember()

// 购物车页要真数据才看得出渲染是否正常：冒烟脚本跑完会清空购物车，
// 这里自己补一件（已有就不动），保证走查结果与上一次跑批无关。
async function seedCart() {
  const auth = { Authorization: 'Bearer ' + MEMBER_TOKEN }
  try {
    const cart = await (await fetch(H5 + '/api/shop/cart/list', { headers: auth })).json()
    if ((cart.data || []).length > 0) return
    const detail = await (await fetch(H5 + '/api/shop/goods/1001')).json()
    const sku = ((detail.data && detail.data.skus) || []).find(s => Number(s.stock) > 0)
    if (!sku) return
    await fetch(H5 + '/api/shop/cart/add', {
      method: 'POST',
      headers: { ...auth, 'Content-Type': 'application/json' },
      body: JSON.stringify({ skuId: sku.id, num: 1 })
    })
  } catch (e) {
    // 补数据失败不阻断走查，购物车断言会如实报空
  }
}
await seedCart()

const pages = [
  // ── 后台（token 键 'token'）
  { app: 'admin', name: '后台首页', url: ADMIN + '/#/home', expect: ['悦集商城', '快捷入口'] },
  { app: 'admin', name: '商品列表', url: ADMIN + '/#/mall/product/mall_goods/list', rows: '.el-table__row' },
  { app: 'admin', name: '商品SKU', url: ADMIN + '/#/mall/product/mall_sku/list', rows: '.el-table__row' },
  { app: 'admin', name: '商品类目', url: ADMIN + '/#/mall/product/mall_category/list', rows: '.el-table__row' },
  { app: 'admin', name: '订单列表', url: ADMIN + '/#/mall/order/mall_order/list', rows: '.el-table__row' },
  { app: 'admin', name: '订单日志', url: ADMIN + '/#/mall/order/mall_order_log/list', rows: '.el-table__row' },
  // V12 起后台菜单按业务域重排（dashboard/product/order/member/marketing/merchant/trade），
  // 下面 URL 与 fe/mall-web/src/router/gen-routes.js 逐一对应
  { app: 'admin', name: '售后列表', url: ADMIN + '/#/mall/order/mall_refund/list', rows: '.el-table__row' },
  { app: 'admin', name: '会员列表', url: ADMIN + '/#/mall/member/mall_member/list', rows: '.el-table__row' },
  { app: 'admin', name: '品牌列表', url: ADMIN + '/#/mall/product/mall_brand/list', rows: '.el-table__row' },
  { app: 'admin', name: '轮播图', url: ADMIN + '/#/mall/marketing/mall_banner/list', rows: '.el-table__row' },
  { app: 'admin', name: '优惠券', url: ADMIN + '/#/mall/marketing/mall_coupon/list', rows: '.el-table__row' },
  { app: 'admin', name: '领券记录', url: ADMIN + '/#/mall/marketing/mall_coupon_record/list', rows: '.el-table__row' },
  { app: 'admin', name: '收货地址', url: ADMIN + '/#/mall/member/mall_address/list', rows: '.el-table__row' },
  { app: 'admin', name: '经营看板', url: ADMIN + '/#/mall/dashboard/mall_dashboard/index', expect: ['GMV'] },
  { app: 'admin', name: '评价管理', url: ADMIN + '/#/mall/product/mall_review/list', expect: ['评价管理'], rows: '.el-table__row' },
  { app: 'admin', name: '站内消息', url: ADMIN + '/#/mall/member/mall_notice/list', expect: ['站内消息'], rows: '.el-table__row' },
  // ── 后台 V10：商户入驻 / 结算 / 提现
  { app: 'admin', name: '商家入驻', url: ADMIN + '/#/mall/merchant/mall_merchant/list', expect: ['商家入驻', '商家台账'], rows: '.el-table__row' },
  { app: 'admin', name: '商家结算', url: ADMIN + '/#/mall/merchant/mall_merchant_settlement/list', expect: ['商家结算'], rows: '.el-table__row' },
  { app: 'admin', name: '商家提现', url: ADMIN + '/#/mall/merchant/mall_merchant_withdrawal/list', expect: ['商家提现'], rows: '.el-table__row' },
  // ── 后台 V12：交易设置（运费模板 / 发货单 / 发票 / 支付流水 / 对账）
  { app: 'admin', name: '运费模板', url: ADMIN + '/#/mall/trade/mall_freight_template/list', expect: ['运费模板'], rows: '.el-table__row' },
  { app: 'admin', name: '发货单', url: ADMIN + '/#/mall/trade/mall_delivery/list', expect: ['发货'], rows: '.el-table__row' },
  { app: 'admin', name: '发票管理', url: ADMIN + '/#/mall/trade/mall_invoice/list', expect: ['发票'], rows: '.el-table__row' },
  { app: 'admin', name: '支付流水', url: ADMIN + '/#/mall/trade/mall_payment/list', expect: ['支付'], rows: '.el-table__row' },
  // 对账差异在「零差异」时列表为空，只断言页面文案与请求正常
  { app: 'admin', name: '支付对账', url: ADMIN + '/#/mall/trade/mall_pay_reconcile/list', expect: ['对账'] },
  // ── PC 前台（token 键 'mall-pc-token'）
  { app: 'pc', name: 'PC 首页', url: PC + '/', expect: ['悦集商城'], imgs: true },
  { app: 'pc', name: 'PC 商品详情', url: PC + '/goods/1001', expect: ['悦集 X7 Pro', '商品评价'], imgs: true },
  { app: 'pc', name: 'PC 购物车', url: PC + '/cart', expect: ['购物车'], rows: '.cart-item, .el-table__row' },
  { app: 'pc', name: 'PC 订单列表', url: PC + '/order/list', expect: ['订单'] },
  { app: 'pc', name: 'PC 售后列表', url: PC + '/refund/list', expect: ['售后'] },
  { app: 'pc', name: 'PC 地址管理', url: PC + '/address', expect: ['地址'] },
  { app: 'pc', name: 'PC 我的收藏', url: PC + '/favorite', expect: ['我的收藏'], rows: '.fv-card' },
  { app: 'pc', name: 'PC 我的积分', url: PC + '/point', expect: ['我的积分', '当前积分'], rows: '.pt-row' },
  { app: 'pc', name: 'PC 消息中心', url: PC + '/notice', expect: ['消息中心'], rows: '.nt-row' },
  // V10：店铺主页（id=1 为平台自营店铺，恒定启用）
  { app: 'pc', name: 'PC 店铺主页', url: PC + '/merchant/1', expect: ['店内商品'], imgs: true },
  // ── H5 前台（token 键 'mall-shop-token'）
  { app: 'h5', name: 'H5 首页', url: H5 + '/home', expect: ['为你推荐'], imgs: true },
  { app: 'h5', name: 'H5 商品详情', url: H5 + '/goods/1001', expect: ['悦集 X7 Pro', '商品评价'], imgs: true },
  { app: 'h5', name: 'H5 购物车', url: H5 + '/cart', expect: ['购物车'] },
  { app: 'h5', name: 'H5 我的订单', url: H5 + '/order/list', expect: ['订单'] },
  { app: 'h5', name: 'H5 我的售后', url: H5 + '/refund/list', expect: ['售后'] },
  { app: 'h5', name: 'H5 优惠券', url: H5 + '/coupon/center', expect: ['券'] },
  { app: 'h5', name: 'H5 我的收藏', url: H5 + '/favorite', expect: ['我的收藏'], rows: '.fv-item' },
  { app: 'h5', name: 'H5 我的积分', url: H5 + '/point', expect: ['我的积分'], rows: '.pt-item' },
  { app: 'h5', name: 'H5 每日签到', url: H5 + '/signin', expect: ['每日签到', '连续签到'] },
  { app: 'h5', name: 'H5 消息中心', url: H5 + '/notice', expect: ['消息中心'], rows: '.nt-item' },
  { app: 'h5', name: 'H5 我的评价', url: H5 + '/review/my', expect: ['我的评价'], rows: '.rv-item' },
  // V10：店铺主页
  { app: 'h5', name: 'H5 店铺主页', url: H5 + '/merchant/1', expect: ['店内商品'], imgs: true }
]

// V12：订单详情页要真实订单号才能走查（物流轨迹 + 发票入口都在里头），
// 从会员订单列表里挑一笔已完成、一笔待支付的，挑不到就跳过这两项，不让脚本假失败。
async function pickOrders() {
  try {
    const res = await fetch(H5 + '/api/shop/order/page?pageNum=1&pageSize=30',
      { headers: { Authorization: 'Bearer ' + MEMBER_TOKEN } })
    const body = await res.json()
    const rows = (body.data && body.data.records) || []
    const done = rows.find(o => Number(o.status) === 40)
    const unpaid = rows.find(o => Number(o.status) === 10)
    return { finishedId: done && done.id, unpaidNo: unpaid && unpaid.orderNo }
  } catch (e) {
    return {}
  }
}
const sampled = await pickOrders()
if (sampled.finishedId) {
  pages.push({ app: 'pc', name: 'PC 订单详情', url: PC + '/order/detail/' + sampled.finishedId,
    expect: ['订单信息', '运费'], rows: '.el-table__row' })
  pages.push({ app: 'h5', name: 'H5 订单详情', url: H5 + '/order/' + sampled.finishedId,
    expect: ['商品总额', '运费'] })
}
if (sampled.unpaidNo) {
  pages.push({ app: 'h5', name: 'H5 收银台', url: H5 + '/pay/' + sampled.unpaidNo,
    expect: ['收银台', '沙箱支付'] })
}

const TOKEN_KEY = { admin: 'token', pc: 'mall-pc-token', h5: 'mall-shop-token' }
const TOKEN = { admin: ADMIN_TOKEN, pc: MEMBER_TOKEN, h5: MEMBER_TOKEN }

// ---------- Chrome ----------
const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
const dir = mkdtempSync(tmpdir() + '/mclui-')
const chrome = spawn(exe, [`--user-data-dir=${dir}`, '--headless=new', '--remote-debugging-port=9802',
  '--no-first-run', '--no-default-browser-check', '--window-size=1440,900', '--hide-scrollbars',
  '--disable-features=IsolateOrigins,site-per-process', 'about:blank'], { stdio: 'ignore' })
await sleep(2600)
const list = await (await fetch('http://127.0.0.1:9802/json/list')).json()
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
  } else if (msg.method) {
    listeners.forEach(fn => fn(msg))
  }
}
const call = (method, params = {}) => new Promise((res, rej) => {
  const mid = ++seq; pending.set(mid, { res, rej })
  ws.send(JSON.stringify({ id: mid, method, params }))
})

let consoleErrors = [], failedReqs = [], pageExceptions = []
listeners.push(msg => {
  if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
    consoleErrors.push((msg.params.args || []).map(a => a.value || a.description || '').join(' ').slice(0, 200))
  }
  if (msg.method === 'Runtime.exceptionThrown') {
    const d = msg.params.exceptionDetails
    pageExceptions.push((d.exception && d.exception.description || d.text || '').slice(0, 200))
  }
  if (msg.method === 'Network.responseReceived') {
    const r = msg.params.response
    if (r.status >= 400 && !r.url.startsWith('data:')) failedReqs.push(`${r.status} ${r.url.slice(0, 110)}`)
  }
})
await call('Runtime.enable')
await call('Network.enable')
await call('Page.enable')

async function navigate(url) {
  await call('Page.navigate', { url })
  await sleep(1200)
  // 等异步数据渲染（列表/图片）
  for (let i = 0; i < 12; i++) {
    const done = await evaluate(`document.readyState === 'complete'`)
    if (done) break
    await sleep(300)
  }
  await sleep(1400)
}

async function evaluate(expression) {
  const r = await call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
  if (r.exceptionDetails) return null
  return r.result.value
}

let pass = 0, fail = 0
const fails = []
const ok = (cond, name, extra = '') => {
  if (cond) { pass++; console.log(`  OK   ${name}${extra ? '  [' + extra + ']' : ''}`) }
  else { fail++; fails.push(name); console.log(`  FAIL ${name}${extra ? '  [' + extra + ']' : ''}`) }
}

let lastApp = ''
for (const p of pages) {
  if (p.app !== lastApp) {
    // 切换应用：先落到目标 origin，写入登录态，再回 about:blank 避免跨域残留
    const origin = new URL(p.url).origin
    await navigate(origin + '/')  // 落到应用根再写 localStorage：写进 Chrome 的 404 错误页上下文会丢
    await evaluate(`localStorage.setItem('${TOKEN_KEY[p.app]}','${TOKEN[p.app]}'); 'ok'`)
    console.log(`\n── ${p.app.toUpperCase()}`)
    lastApp = p.app
  }
  consoleErrors = []; failedReqs = []; pageExceptions = []
  await navigate(p.url)

  const info = await evaluate(`(() => {
    const text = document.body.innerText || ''
    return {
      len: text.length,
      text: text.slice(0, 4000),
      rows: document.querySelectorAll('${p.rows || '.nothing'}').length,
      badImgs: Array.from(document.images).filter(i => i.complete && i.naturalWidth === 0).length,
      goodImgs: Array.from(document.images).filter(i => i.naturalWidth > 0).length,
      errBox: !!document.querySelector('.el-message--error')
    }
  })()`)

  ok(info && info.len > 60, `${p.name} 渲染`, `${info && info.len} 字符`)
  for (const t of (p.expect || [])) {
    ok(info && info.text.includes(t), `${p.name} 含「${t}」`)
  }
  if (p.rows) ok(info && info.rows > 0, `${p.name} 有数据行`, `${info && info.rows} 行`)
  if (p.imgs) ok(info && info.goodImgs > 0 && info.badImgs === 0, `${p.name} 图片正常`,
    `正常 ${info && info.goodImgs} / 破图 ${info && info.badImgs}`)
  ok(pageExceptions.length === 0, `${p.name} 无 JS 异常`, pageExceptions[0] || '')
  ok(consoleErrors.length === 0, `${p.name} 无 console error`, consoleErrors[0] || '')
  const bad = failedReqs.filter(u => !u.includes('favicon'))
  ok(bad.length === 0, `${p.name} 无失败请求`, bad[0] || '')

  const shot = await call('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`${SHOTS}/${p.app}-${p.name.replace(/[ /]/g, '_')}.png`, Buffer.from(shot.data, 'base64'))
}

console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
if (fail) { console.log('失败项：\n  - ' + fails.join('\n  - ')) }
chrome.kill()
process.exit(fail ? 1 : 0)
