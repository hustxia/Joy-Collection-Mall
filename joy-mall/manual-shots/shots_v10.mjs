#!/usr/bin/env node
// shots_v10.mjs —— V10 手册补拍：商户入驻 / 结算 / 提现 / 店铺主页 / 跨店拆单
// 前置：网关 8080 + 8 服务 + 三端前端（9431 / 9432 / 9531）全部在跑
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'

const OUT = 'C:/tmp/mall/manual-shots'
mkdirSync(OUT, { recursive: true })

const WEB = 'http://localhost:9431'
const PC = 'http://localhost:9432'
const H5 = 'http://localhost:9531'
const sleep = ms => new Promise(r => setTimeout(r, ms))

const post = async (base, path, body, token) => {
  const res = await fetch(base + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: JSON.stringify(body || {})
  })
  try { return await res.json() } catch { return {} }
}
const del = async (base, path, token) => {
  const res = await fetch(base + path, { method: 'DELETE', headers: { Authorization: 'Bearer ' + token } })
  try { return await res.json() } catch { return {} }
}
const get = async (base, path, token) => {
  const res = await fetch(base + path, { headers: token ? { Authorization: 'Bearer ' + token } : {} })
  try { return await res.json() } catch { return {} }
}

// ── 登录 ──
const cap = await (await fetch(WEB + '/dev-api/api/mall/auth/captcha')).json()
const adminLogin = await post(WEB, '/dev-api/api/mall/auth/login', {
  username: 'admin', password: 'admin123',
  captchaId: cap?.data?.captchaId, captchaAnswer: String(cap?.data?.answer)
})
const ADMIN = adminLogin?.data?.token || adminLogin?.data?.accessToken
const memLogin = await post(H5, '/api/shop/auth/login', { phone: '13800000001', password: '123456' })
const MEMBER = memLogin?.data?.token || memLogin?.data?.accessToken
if (!ADMIN || !MEMBER) { console.error('登录失败', JSON.stringify(adminLogin).slice(0, 200), JSON.stringify(memLogin).slice(0, 200)); process.exit(2) }
const memberInfo = memLogin?.data?.member || memLogin?.data?.userInfo || { id: '3001', nickname: '清风徐来', phone: '13800000001', points: 26857 }
const MEMBER_JSON = JSON.stringify(memberInfo)

// ── 准备跨店购物车：2001 归商家1，2014 归另一商家 ──
const cartList = await get(H5, '/api/shop/cart/list', MEMBER)
for (const row of (cartList?.data || [])) await del(H5, '/api/shop/cart/remove/' + row.id, MEMBER)
for (const skuId of [2001, 2014]) {
  const r = await post(H5, '/api/shop/cart/add', { skuId, num: 1 }, MEMBER)
  console.log('cart add', skuId, JSON.stringify(r).slice(0, 120))
}
const after = await get(H5, '/api/shop/cart/list', MEMBER)
console.log('cart rows:', (after?.data || []).length)

// ── 截图任务 ──
// [name, origin, route, width, height, storage]
const jobs = [
  ['admin-03-goods',     WEB, '/#/mall/product/mall_goods/list',                  1440, 900, { token: ADMIN }],
  ['admin-14-merchant',  WEB, '/#/mall/merchant/mall_merchant/list',              1440, 900, { token: ADMIN }],
  ['admin-15-settlement',WEB, '/#/mall/merchant/mall_merchant_settlement/list',   1440, 900, { token: ADMIN }],
  ['admin-16-withdrawal',WEB, '/#/mall/merchant/mall_merchant_withdrawal/list',   1440, 900, { token: ADMIN }],
  ['pc-01-home',         PC,  '/',                          1440, 900, {}],
  ['pc-13-shop',         PC,  '/merchant/1',                1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  ['pc-14-confirm',      PC,  '/order/confirm',             1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  ['h5-01-home',         H5,  '/home',                       390, 844, {}],
  ['h5-13-shop',         H5,  '/merchant/1',                 390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }],
  ['h5-14-confirm',      H5,  '/order/confirm',              390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }]
]

const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
if (!exe) { console.error('no chrome/edge'); process.exit(2) }
const cp = spawn(exe, [
  '--user-data-dir=' + OUT + '/.chrome-profile-v10', '--headless=new', '--remote-debugging-port=9781',
  '--no-first-run', '--no-default-browser-check', '--window-size=1440,1000', '--hide-scrollbars', 'about:blank'
], { stdio: 'ignore' })
await sleep(2500)

const list = await (await fetch('http://127.0.0.1:9781/json/list')).json()
const page = list.find(t => t.type === 'page') || list[0]
const ws = new WebSocket(page.webSocketDebuggerUrl)
await new Promise(r => { ws.onopen = r })
let seq = 0
const call = (method, params) => new Promise(res => {
  const mid = ++seq
  const h = ev => { const m = JSON.parse(ev.data); if (m.id === mid) { ws.removeEventListener('message', h); res(m.result) } }
  ws.addEventListener('message', h)
  ws.send(JSON.stringify({ id: mid, method, params }))
})
await call('Page.enable', {})
await call('Runtime.enable', {})

const failed = []
for (const [name, origin, route, width, height, storage] of jobs) {
  await call('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: width < 600 ? 2 : 1, mobile: width < 600 })
  await call('Page.navigate', { url: origin + '/__blank_probe__' }).catch(() => {})
  await sleep(1800)
  if (Object.keys(storage).length) {
    const expr = Object.entries(storage).map(([k, v]) => `localStorage.setItem(${JSON.stringify(k)}, ${JSON.stringify(v)});`).join(' ')
    await call('Runtime.evaluate', { expression: expr })
  }
  await call('Page.navigate', { url: origin + route })
  await sleep(5200)
  const probe = await call('Runtime.evaluate', {
    expression: 'JSON.stringify({href:location.href,text:(document.querySelector("#app")||document.body).innerText.slice(0,300)})',
    returnByValue: true
  })
  const shot = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync(`${OUT}/${name}.png`, Buffer.from(shot.data, 'base64'))
  const s = JSON.parse(probe.result?.value || '{}')
  const bad = !s.text || s.text.trim().length < 8
  console.log((bad ? 'WARN ' : 'OK   ') + name.padEnd(20), s.href || '')
  if (bad) failed.push(name)
}
cp.kill()
console.log(failed.length ? 'FAILED: ' + failed.join(',') : 'ALL DONE')
