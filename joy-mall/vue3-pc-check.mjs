// Vue3 迁移专项走查：mall-pc 关键页渲染 + console/异常/失败请求捕获
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const PC = 'http://127.0.0.1:9432'
const SHOTS = 'D:/workspace/project/artifacts/vue3-migration'
mkdirSync(SHOTS, { recursive: true })
const sleep = ms => new Promise(r => setTimeout(r, ms))

const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
const chrome = spawn(exe, [`--user-data-dir=${mkdtempSync(tmpdir() + '/v3mig-')}`, '--headless=new', '--remote-debugging-port=9837', '--no-first-run', '--window-size=1440,900', '--hide-scrollbars', 'about:blank'], { stdio: 'ignore' })
await sleep(2600)
const list = await (await fetch('http://127.0.0.1:9837/json/list')).json()
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
async function check(name, path, assertFn) {
  bucket = { errors: [], failed: [], exceptions: [] }
  await nav(PC + path)
  const info = await ev(`(() => ({ title: document.title, text: document.body.innerText.slice(0, 600), appChildren: document.querySelector('#app') ? document.querySelector('#app').children.length : -1 }))()`)
  let verdict = 'PASS', detail = ''
  try { detail = assertFn ? (assertFn(info) || '') : '' }
  catch (e) { verdict = 'FAIL'; detail = e.message }
  const noise = [...bucket.errors, ...bucket.exceptions, ...bucket.failed].filter(e =>
    !e.includes('favicon') && !e.includes('DevTools'))
  if (noise.length && verdict === 'PASS') { verdict = 'WARN'; detail += ' | noise: ' + noise.slice(0, 2).join(' ;; ') }
  console.log(`[${verdict}] ${name} ${path} ${detail}`)
  verdict === 'FAIL' ? fail++ : pass++
  await shot(name)
}
const assert = (cond, msg) => { if (!cond) throw new Error(msg) }

await check('首页', '/', i => { assert(i.appChildren > 0, 'app 空挂载'); assert(i.text.includes('悦集') || i.text.length > 50, '首页内容异常: ' + i.text.slice(0, 80)) })
await check('商品列表', '/list', i => { assert(i.appChildren > 0, 'app 空挂载') })
await check('登录页', '/login', i => { assert(i.appChildren > 0, 'app 空挂载'); assert(/登录|密码|手机/.test(i.text), '登录表单缺失: ' + i.text.slice(0, 80)) })
await check('秒杀列表', '/seckill', i => { assert(i.appChildren > 0, 'app 空挂载') })
await check('拼团列表', '/group', i => { assert(i.appChildren > 0, 'app 空挂载') })
await check('优惠券中心', '/coupon/center', i => { assert(i.appChildren > 0, 'app 空挂载') })
await check('404 回落', '/no-such-page', i => { assert(i.appChildren > 0, '路由回落失败') })

console.log(`\n结果: ${pass} pass, ${fail} fail`)
chrome.kill()
process.exit(fail ? 1 : 0)
