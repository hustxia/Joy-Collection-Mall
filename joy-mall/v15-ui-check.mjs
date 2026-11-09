// V15 走查：评价增强 / 售后扩展 / 促销 C 端展示
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const WEB = 'http://127.0.0.1:9431', H5 = 'http://127.0.0.1:9531', PC = 'http://127.0.0.1:9432'
const GW = 'http://127.0.0.1:8080'
const SHOTS = 'D:/workspace/project/artifacts/v15'
mkdirSync(SHOTS, { recursive: true })
const sleep = ms => new Promise(r => setTimeout(r, ms))

async function post(base, path, body, token) {
  const res = await fetch(base + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: JSON.stringify(body || {})
  })
  try { return await res.json() } catch { return {} }
}

// 后台 token
const cap = await (await fetch(WEB + '/dev-api/api/mall/auth/captcha')).json()
const al = await post(WEB, '/dev-api/api/mall/auth/login', { username: 'admin', password: 'admin123', captchaId: cap.data.captchaId, captchaAnswer: String(cap.data.answer) })
const ADMIN = al.data && (al.data.token || al.data.accessToken)
if (!ADMIN) { console.error('admin login failed'); process.exit(2) }
// 会员 token
const ml = await post(GW, '/shop/auth/login', { phone: '13800000001', password: '123456' })
const MEMBER = ml.data && (ml.data.token || ml.data.accessToken)
if (!MEMBER) { console.error('member login failed ' + JSON.stringify(ml).slice(0, 200)); process.exit(2) }
const OID = await (await fetch(GW + '/shop/order/page?page=1&size=1', { headers: { Authorization: 'Bearer ' + MEMBER } })).json();
const ORDER_ID = OID.data && OID.data.records && OID.data.records[0] && (OID.data.records[0].id || OID.data.records[0].orderNo) || ''
console.log('tokens ok, ORDER_ID=' + ORDER_ID)

const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
const chrome = spawn(exe, [`--user-data-dir=${mkdtempSync(tmpdir() + '/v15-')}`, '--headless=new', '--remote-debugging-port=9833', '--no-first-run', '--window-size=1440,900', '--hide-scrollbars', 'about:blank'], { stdio: 'ignore' })
await sleep(2600)
const list = await (await fetch('http://127.0.0.1:9833/json/list')).json()
const ws = new WebSocket((list.find(t => t.type === 'page') || list[0]).webSocketDebuggerUrl)
await new Promise(r => { ws.onopen = r })
let seq = 0; const pending = new Map(); const listeners = []
ws.onmessage = ev => {
  const m = JSON.parse(ev.data)
  if (m.id && pending.has(m.id)) { const { res, rej } = pending.get(m.id); pending.delete(m.id); m.error ? rej(new Error(m.error.message)) : res(m.result) }
  else if (m.method) listeners.forEach(f => f(m))
}
const call = (method, params = {}) => new Promise((res, rej) => { const id = ++seq; pending.set(id, { res, rej }); ws.send(JSON.stringify({ id, method, params })) })
await call('Page.enable'); await call('Runtime.enable'); await call('Log.enable'); await call('Network.enable')

let bucket = { errors: [], failed: [], exceptions: [] }
listeners.push(m => {
  if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') bucket.errors.push(m.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 250))
  if (m.method === 'Runtime.exceptionThrown') { const d = m.params.exceptionDetails; bucket.exceptions.push((d.exception && d.exception.description || d.text || '').slice(0, 250)) }
  if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') bucket.errors.push('[log] ' + m.params.entry.text.slice(0, 200))
  if (m.method === 'Network.responseReceived') { const r = m.params.response; if (r.status >= 400 && r.url.indexOf('hot-update') < 0) bucket.failed.push(r.status + ' ' + r.url.slice(0, 140)) }
})

const results = []
async function visit(base, url, name) {
  bucket = { errors: [], failed: [], exceptions: [] }
  await call('Page.navigate', { url: base + url })
  await sleep(2700)
  const chk = await call('Runtime.evaluate', { expression: `JSON.stringify({app:document.querySelector('#app')?document.querySelector('#app').innerHTML.length:document.body.innerHTML.length, text:(document.body.innerText||'').replace(/\\s+/g,' ').slice(0,260)})`, returnByValue: true })
  let info = {}; try { info = JSON.parse(chk.result.value) } catch {}
  const shot = await call('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`${SHOTS}/${name}.png`, Buffer.from(shot.data, 'base64'))
  results.push({ name, url, ...info, ...bucket })
  console.log(`[${name}] app=${info.app} err=${bucket.errors.length} exc=${bucket.exceptions.length} fail=${bucket.failed.length}`)
}

// 后台
await call('Page.navigate', { url: WEB + '/#/login' }); await sleep(1400)
await call('Runtime.evaluate', { expression: `localStorage.setItem('token','${ADMIN}');1` })
await visit(WEB, '/#/mall/product/mall_review/list', '后台-评价增强')
await visit(WEB, '/#/mall/order/mall_refund/list', '后台-售后扩展')

// H5：先注入会员令牌
await call('Page.navigate', { url: H5 + '/' }); await sleep(1400)
await call('Runtime.evaluate', { expression: `localStorage.setItem('mall-shop-token','${MEMBER}');1` })
for (const p of [
  { url: '/goods/1001', name: 'H5-商品详情(评价+促销)' },
  { url: '/review/my', name: 'H5-我的评价' },
  { url: `/refund/apply?orderId=${ORDER_ID}`, name: 'H5-售后申请' },
  { url: '/refund/list', name: 'H5-我的售后' },
  { url: `/order/${ORDER_ID}`, name: 'H5-订单详情' }
]) await visit(H5, p.url, p.name)

// PC：注入令牌
await call('Page.navigate', { url: PC + '/' }); await sleep(1400)
await call('Runtime.evaluate', { expression: `localStorage.setItem('mall-pc-token','${MEMBER}');1` })
for (const p of [
  { url: '/goods/1001', name: 'PC-商品详情(评价+促销)' },
  { url: '/refund/apply', name: 'PC-售后申请' },
  { url: '/refund/list', name: 'PC-我的售后' },
  { url: `/order/detail/${ORDER_ID}`, name: 'PC-订单详情' }
]) await visit(PC, p.url, p.name)

console.log('\n===== 问题页面 =====')
let bad = 0
for (const r of results) {
  if (!r.errors.length && !r.exceptions.length && !r.failed.length && r.app > 200) continue
  bad++
  console.log(`\n### ${r.name} ${r.url}`)
  if (r.app <= 200) console.log('  !! 疑似白屏 app=' + r.app)
  r.exceptions.slice(0, 3).forEach(e => console.log('  EXC ' + e.replace(/\n/g, ' | ').slice(0, 220)))
  r.errors.slice(0, 4).forEach(e => console.log('  ERR ' + e.replace(/\n/g, ' | ').slice(0, 220)))
  r.failed.slice(0, 5).forEach(e => console.log('  NET ' + e))
}
console.log(bad === 0 ? '\nALL PASS' : `\n${bad} 页有问题`)
chrome.kill(); process.exit(bad === 0 ? 0 : 1)
