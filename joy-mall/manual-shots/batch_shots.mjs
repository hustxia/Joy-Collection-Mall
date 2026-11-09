#!/usr/bin/env node
// batch_shots.mjs —— 悦集商城操作手册批量截图（单实例 Chrome，逐页注入登录态）
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'

const ADMIN = '36c51a8002f34ceda49928311c876788'
const MEMBER = '046402954aa446d3a19cd80f46985eba'
const MEMBER_JSON = JSON.stringify({ id: '3001', nickname: '清风徐来', phone: '13800000001', avatar: null, gender: 1, points: 26857 })
const OUT = 'C:/tmp/mall/manual-shots'
mkdirSync(OUT, { recursive: true })

const WEB = 'http://localhost:9431', PC = 'http://localhost:9432', H5 = 'http://localhost:9531'

const HASH = true // mall-web 是 VueRouter hash 模式
const webUrl = r => WEB + '/#' + r

// [name, origin, fullUrl, width, height, storage]
const jobs = [
  // ── 运营后台（hash 路由）──
  ['admin-01-login',     WEB, webUrl('/login'),                          1440, 900, {}],
  ['admin-02-dashboard', WEB, webUrl('/mall/other/mall_dashboard/index'),1440, 900, { token: ADMIN }],
  ['admin-03-goods',     WEB, webUrl('/mall/product/mall_goods/list'),   1440, 900, { token: ADMIN }],
  ['admin-04-sku',       WEB, webUrl('/mall/product/mall_sku/list'),     1440, 900, { token: ADMIN }],
  ['admin-05-category',  WEB, webUrl('/mall/product/mall_category/list'),1440, 900, { token: ADMIN }],
  ['admin-06-order',     WEB, webUrl('/mall/order/mall_order/list'),     1440, 900, { token: ADMIN }],
  ['admin-07-order-log', WEB, webUrl('/mall/order/mall_order_log/list'), 1440, 900, { token: ADMIN }],
  ['admin-08-refund',    WEB, webUrl('/mall/other/mall_refund/list'),    1440, 900, { token: ADMIN }],
  ['admin-09-coupon',    WEB, webUrl('/mall/other/mall_coupon/list'),    1440, 900, { token: ADMIN }],
  ['admin-10-member',    WEB, webUrl('/mall/other/mall_member/list'),    1440, 900, { token: ADMIN }],
  ['admin-11-review',    WEB, webUrl('/mall/product/mall_review/list'),  1440, 900, { token: ADMIN }],
  ['admin-12-banner',    WEB, webUrl('/mall/other/mall_banner/list'),    1440, 900, { token: ADMIN }],
  ['admin-13-notice',    WEB, webUrl('/mall/other/mall_notice/list'),    1440, 900, { token: ADMIN }],
  // ── PC 商城 ──
  ['pc-01-home',    PC, '/',             1440, 900, {}],
  ['pc-02-list',    PC, '/list',         1440, 900, {}],
  ['pc-03-detail',  PC, '/goods/1001',   1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  ['pc-04-cart',    PC, '/cart',         1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  ['pc-05-orders',  PC, '/order/list',   1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  ['pc-06-order-detail', PC, '/order/detail/2116733628441509889', 1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  ['pc-07-coupon',  PC, '/coupon/center',1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  ['pc-08-refund',  PC, '/refund/list',  1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  ['pc-09-refund-detail', PC, '/refund/detail/2116733620233232386', 1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  ['pc-10-point',   PC, '/point',        1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  ['pc-11-notice',  PC, '/notice',       1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  ['pc-12-address', PC, '/address',      1440, 900, { 'mall-pc-token': MEMBER, 'mall-pc-member': MEMBER_JSON }],
  // ── H5 商城 ──
  ['h5-01-home',     H5, '/home',          390, 844, {}],
  ['h5-02-category', H5, '/category',      390, 844, {}],
  ['h5-03-detail',   H5, '/goods/1001',    390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }],
  ['h5-04-cart',     H5, '/cart',          390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }],
  ['h5-05-user',     H5, '/user',          390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }],
  ['h5-06-orders',   H5, '/order/list',    390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }],
  ['h5-07-refund',   H5, '/refund/list',   390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }],
  ['h5-08-signin',   H5, '/signin',        390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }],
  ['h5-09-point',    H5, '/point',         390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }],
  ['h5-10-coupon',   H5, '/coupon/center', 390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }],
  ['h5-11-notice',   H5, '/notice',        390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }],
  ['h5-12-favorite', H5, '/favorite',      390, 844, { 'mall-shop-token': MEMBER, 'mall-shop-member': MEMBER_JSON }],
]

const chromePaths = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'
]
const exe = chromePaths.find(p => existsSync(p))
if (!exe) { console.error('no chrome/edge'); process.exit(2) }
const cp = spawn(exe, [
  '--user-data-dir=' + OUT + '/.chrome-profile', '--headless=new', '--remote-debugging-port=9779',
  '--no-first-run', '--no-default-browser-check', '--window-size=1440,1000', 'about:blank'
], { stdio: 'ignore' })
await new Promise(r => setTimeout(r, 2500))

const list = await (await fetch('http://127.0.0.1:9779/json/list')).json()
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
await call('Page.enable', {}); await call('Runtime.enable', {})

const failed = []
for (const [name, origin, route, width, height, storage] of jobs) {
  const url = route.startsWith('http') ? route : origin + route
  await call('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: width < 600 ? 2 : 1, mobile: width < 600 })
  await call('Page.navigate', { url: origin + '/__blank_probe__' }).catch(()=>{})
  await new Promise(r => setTimeout(r, 2500))
  if (Object.keys(storage).length) {
    const expr = Object.entries(storage).map(([k, v]) => `localStorage.setItem(${JSON.stringify(k)}, ${JSON.stringify(v)});`).join(' ')
    await call('Runtime.evaluate', { expression: expr })
  }
  await call('Page.navigate', { url })
  await new Promise(r => setTimeout(r, name.includes('login') ? 3500 : 5000))
  const st = await call('Runtime.evaluate', { expression: 'JSON.stringify({href:location.href,blank:document.querySelector("#app")&&document.querySelector("#app").innerHTML.trim()===""})', returnByValue: true })
  const shot = await call('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`${OUT}/${name}.png`, Buffer.from(shot.data, 'base64'))
  const s = JSON.parse(st.result?.value || '{}')
  const bad = s.blank
  console.log((bad ? 'WARN ' : 'OK   ') + name, s.href || '')
  if (bad) failed.push(name)
}
cp.kill()
console.log(failed.length ? 'FAILED: ' + failed.join(',') : 'ALL DONE')
