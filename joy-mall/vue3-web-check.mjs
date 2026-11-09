// Vue3 迁移专项走查：mall-web（后台）关键页渲染 + console/异常/失败请求捕获
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const WEB = 'http://127.0.0.1:9431'
const SHOTS = 'D:/workspace/project/artifacts/vue3-migration'
mkdirSync(SHOTS, { recursive: true })
const sleep = ms => new Promise(r => setTimeout(r, ms))

// 登录拿 token + perms，写 localStorage 后再进页面（v-hasPermi 依赖 perms）
async function login() {
  const cap = await (await fetch(WEB + '/dev-api/api/mall/auth/captcha')).json()
  const al = await (await fetch(WEB + '/dev-api/api/mall/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123', captchaId: cap.data.captchaId, captchaAnswer: String(cap.data.answer) })
  })).json()
  return al.data && (al.data.token || al.data.accessToken)
}
const TOKEN = await login()
if (!TOKEN) { console.error('admin login failed（后端未就绪?）'); process.exit(2) }
console.log('admin token ok')

const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
const chrome = spawn(exe, [`--user-data-dir=${mkdtempSync(tmpdir() + '/v3web-')}`, '--headless=new', '--remote-debugging-port=9838', '--no-first-run', '--window-size=1440,900', '--hide-scrollbars', 'about:blank'], { stdio: 'ignore' })
await sleep(2600)
const list = await (await fetch('http://127.0.0.1:9838/json/list')).json()
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
  if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') bucket.errors.push(m.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 300))
  if (m.method === 'Runtime.exceptionThrown') { const d = m.params.exceptionDetails; bucket.exceptions.push((d.exception && d.exception.description || d.text || '').slice(0, 300)) }
  if (m.method === 'Network.responseReceived') {
    const s = m.params.response.status
    if (s >= 400 && !m.params.response.url.includes('/favicon')) bucket.failed.push(`${s} ${m.params.response.url}`.slice(0, 200))
  }
})
const nav = async (url) => { await call('Page.navigate', { url }); await sleep(3500) }
const ev = async (expr) => (await call('Runtime.evaluate', { expression: expr, returnByValue: true })).result.value
const shot = async (name) => {
  const { data } = await call('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`${SHOTS}/${name}.png`, Buffer.from(data, 'base64'))
}

let pass = 0, fail = 0
async function check(name, hash, assertFn) {
  bucket = { errors: [], failed: [], exceptions: [] }
  await nav(WEB + '/#' + hash)
  await sleep(1200)
  const info = await ev(`(() => ({ text: document.body.innerText.slice(0, 800), appChildren: document.querySelector('#app') ? document.querySelector('#app').children.length : -1 }))()`)
  let verdict = 'PASS', detail = ''
  try { detail = assertFn ? (assertFn(info) || '') : '' }
  catch (e) { verdict = 'FAIL'; detail = e.message }
  const noise = [...bucket.errors, ...bucket.exceptions, ...bucket.failed].filter(e =>
    !e.includes('favicon') && !e.includes('DevTools'))
  if (noise.length && verdict === 'PASS') { verdict = 'WARN'; detail += ' | noise: ' + noise.slice(0, 2).join(' ;; ').slice(0, 250) }
  console.log(`[${verdict}] ${name} ${hash} ${detail}`)
  verdict === 'FAIL' ? fail++ : pass++
  await shot(name)
}
const assert = (cond, msg) => { if (!cond) throw new Error(msg) }

await check('登录页(未依赖态)', '/login', i => { assert(i.appChildren > 0, 'app 空挂载'); assert(/登\s*录/.test(i.text), '无登录表单') })
// 先种会话再进应用
await nav(WEB + '/#/login')
await ev(`localStorage.setItem('token', ${JSON.stringify(TOKEN)});
  localStorage.setItem('perms', JSON.stringify(['*:*:*'])); 'ok'`)

await check('工作台', '/mall/dashboard/mall_dashboard/index', i => { assert(i.appChildren > 0, 'app 空挂载'); assert(i.text.length > 100, '页面近乎空白: ' + i.text.slice(0, 60)) })
await check('商品管理', '/mall/product/mall_goods/list', i => { assert(i.appChildren > 0, 'app 空挂载'); assert(/商品|查询|新增/.test(i.text), '无列表要素: ' + i.text.slice(0, 60)) })
await check('订单管理', '/mall/order/mall_order/list', i => { assert(i.appChildren > 0, 'app 空挂载') })
await check('会员管理', '/mall/member/mall_member/list', i => { assert(i.appChildren > 0, 'app 空挂载') })
await check('优惠券', '/mall/marketing/mall_coupon/list', i => { assert(i.appChildren > 0, 'app 空挂载') })
await check('售后管理', '/mall/order/mall_refund/list', i => { assert(i.appChildren > 0, 'app 空挂载') })
await check('AI会话记录', '/aichat/mall_chat_session/list', i => { assert(i.appChildren > 0, 'app 空挂载') })
await check('AI用量看板', '/aichat/mall_chat_stat/list', i => { assert(i.appChildren > 0, 'app 空挂载') })

console.log(`\n结果: ${pass} pass, ${fail} fail`)
chrome.kill()
process.exit(fail ? 1 : 0)
