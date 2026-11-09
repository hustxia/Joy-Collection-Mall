// 菜单二级下拉 + 消息触达页 验证：截图 + console 错误
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const WEB = 'http://127.0.0.1:9431'
const OUT = 'C:/tmp/mall/artifacts/menu'
mkdirSync(OUT, { recursive: true })
const sleep = ms => new Promise(r => setTimeout(r, ms))

async function post(path, body) {
  const res = await fetch(WEB + path, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body || {})
  })
  try { return await res.json() } catch { return {} }
}

const cap = await (await fetch(WEB + '/dev-api/api/mall/auth/captcha')).json()
const login = await post('/dev-api/api/mall/auth/login', {
  username: 'admin', password: 'admin123',
  captchaId: cap.data.captchaId, captchaAnswer: String(cap.data.answer)
})
const TOKEN = login.data.token
console.log('token ok')

const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
const dir = mkdtempSync(tmpdir() + '/menu-')
const port = 9813
const chrome = spawn(exe, [`--user-data-dir=${dir}`, '--headless=new', `--remote-debugging-port=${port}`,
  '--no-first-run', '--no-default-browser-check', '--window-size=1440,900', '--hide-scrollbars', 'about:blank'], { stdio: 'ignore' })
await sleep(2600)
const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
const ws = new WebSocket((list.find(t => t.type === 'page') || list[0]).webSocketDebuggerUrl)
await new Promise(r => { ws.onopen = r })
let seq = 0
const pending = new Map(); const listeners = []
ws.onmessage = ev => {
  const m = JSON.parse(ev.data)
  if (m.id && pending.has(m.id)) { const { res, rej } = pending.get(m.id); pending.delete(m.id); m.error ? rej(new Error(m.error.message)) : res(m.result) }
  else if (m.method) listeners.forEach(f => f(m))
}
const call = (method, params = {}) => new Promise((res, rej) => { const id = ++seq; pending.set(id, { res, rej }); ws.send(JSON.stringify({ id, method, params })) })

await call('Page.enable'); await call('Runtime.enable'); await call('Log.enable')
let errors = []
listeners.push(m => {
  if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') errors.push(m.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 300))
  if (m.method === 'Runtime.exceptionThrown') errors.push('EXC ' + (m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text || '').slice(0, 300))
})

async function shot(name) {
  const r = await call('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`${OUT}/${name}.png`, Buffer.from(r.data, 'base64'))
}
async function evaluate(expr) {
  const r = await call('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
  return r.result.value
}

await call('Page.navigate', { url: WEB + '/#/login' })
await sleep(1200)
await call('Runtime.evaluate', { expression: `localStorage.setItem('token','${TOKEN}')` })

// 1. 商品域：应自动展开二级
errors = []
await call('Page.navigate', { url: WEB + '/#/mall/product/mall_goods/list' })
await sleep(3000)
await shot('01-goods-expanded')
const menuInfo = await evaluate(`JSON.stringify({
  primary: [...document.querySelectorAll('.primary-item')].map(e => e.innerText.replace(/\\s+/g,' ').trim()),
  subs: [...document.querySelectorAll('.sub-item')].map(e => e.innerText.trim()),
  topBar: !!document.querySelector('.subnav')
})`)
console.log('菜单:', menuInfo)

// 2. 点「营销管理」一级 → 下拉该域
await evaluate(`(() => {
  const el = [...document.querySelectorAll('.primary-item')].find(e => e.innerText.indexOf('营销管理') >= 0)
  el && el.click(); return !!el
})()`)
await sleep(1600)
await shot('02-marketing-open')
console.log('点营销后子项:', await evaluate(`JSON.stringify([...document.querySelectorAll('.sub-item')].map(e=>e.innerText.trim()))`))

// 3. 消息触达页
errors = []
await call('Page.navigate', { url: WEB + '/#/mall/service/mall_message/index' })
await sleep(3000)
await shot('03-message-page')
const msgState = await evaluate(`JSON.stringify({
  rows: document.querySelectorAll('.el-table__row').length,
  text: (document.getElementById('app')||{}).innerText ? document.getElementById('app').innerText.replace(/\\s+/g,' ').slice(0,160) : ''
})`)
console.log('消息页:', msgState)
console.log('消息页错误数:', errors.length)
errors.slice(0, 4).forEach(e => console.log('  ' + e.replace(/\n/g, ' | ').slice(0, 200)))
chrome.kill()
process.exit(0)
