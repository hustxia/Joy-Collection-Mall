// V8/V9 营销页面专项走查：后台 4 新页 + H5/PC 新路由
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const WEB = 'http://127.0.0.1:9431'
const H5 = 'http://127.0.0.1:9531'
const PC = 'http://127.0.0.1:9432'
const SHOTS = 'D:/workspace/project/artifacts/v89'
mkdirSync(SHOTS, { recursive: true })
const sleep = ms => new Promise(r => setTimeout(r, ms))

async function post(base, path, body) {
  const res = await fetch(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body || {}) })
  try { return await res.json() } catch { return {} }
}

const cap = await (await fetch(WEB + '/dev-api/api/mall/auth/captcha')).json()
const loginRes = await post(WEB, '/dev-api/api/mall/auth/login', { username: 'admin', password: 'admin123', captchaId: cap.data.captchaId, captchaAnswer: String(cap.data.answer) })
const ADMIN_TOKEN = loginRes.data && (loginRes.data.token || loginRes.data.accessToken)
if (!ADMIN_TOKEN) { console.error('admin login failed ' + JSON.stringify(loginRes).slice(0, 200)); process.exit(2) }
console.log('admin token ok')

const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
const dir = mkdtempSync(tmpdir() + '/v89-')
const chrome = spawn(exe, [`--user-data-dir=${dir}`, '--headless=new', '--remote-debugging-port=9822', '--no-first-run', '--window-size=1440,900', '--hide-scrollbars', 'about:blank'], { stdio: 'ignore' })
await sleep(2600)
const list = await (await fetch('http://127.0.0.1:9822/json/list')).json()
const target = list.find(t => t.type === 'page') || list[0]
const ws = new WebSocket(target.webSocketDebuggerUrl)
await new Promise(r => { ws.onopen = r })
let seq = 0
const pending = new Map()
const listeners = []
ws.onmessage = ev => {
  const msg = JSON.parse(ev.data)
  if (msg.id && pending.has(msg.id)) { const { res, rej } = pending.get(msg.id); pending.delete(msg.id); msg.error ? rej(new Error(msg.error.message)) : res(msg.result) }
  else if (msg.method) listeners.forEach(fn => fn(msg))
}
const call = (m, p = {}) => new Promise((res, rej) => { const mid = ++seq; pending.set(mid, { res, rej }); ws.send(JSON.stringify({ id: mid, method: m, params: p })) })
await call('Page.enable'); await call('Runtime.enable'); await call('Log.enable'); await call('Network.enable')

let bucket = { errors: [], failed: [], exceptions: [] }
listeners.push(msg => {
  if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') bucket.errors.push(msg.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 300))
  if (msg.method === 'Runtime.exceptionThrown') { const d = msg.params.exceptionDetails; bucket.exceptions.push((d.exception && d.exception.description || d.text || '').slice(0, 300)) }
  if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') bucket.errors.push('[log] ' + msg.params.entry.text.slice(0, 250))
  if (msg.method === 'Network.responseReceived') { const r = msg.params.response; if (r.status >= 400 && r.url.indexOf('hot-update') < 0) bucket.failed.push(r.status + ' ' + r.url.slice(0, 150)) }
})

const results = []
async function visit(base, url, name, tokenKey) {
  bucket = { errors: [], failed: [], exceptions: [] }
  if (tokenKey) { await call('Page.navigate', { url: base + '/' }); await sleep(1200); await call('Runtime.evaluate', { expression: `localStorage.setItem('${tokenKey}','${tokenVal[tokenKey] || ''}');1` }) }
  await call('Page.navigate', { url: base + url })
  await sleep(2600)
  const chk = await call('Runtime.evaluate', { expression: `JSON.stringify({app:document.querySelector('#app')?document.querySelector('#app').innerHTML.length:document.body.innerHTML.length, text:(document.body.innerText||'').replace(/\\s+/g,' ').slice(0,200)})`, returnByValue: true })
  let info = {}; try { info = JSON.parse(chk.result.value) } catch {}
  const shot = await call('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`${SHOTS}/${name}.png`, Buffer.from(shot.data, 'base64'))
  results.push({ name, url, ...info, ...bucket })
  console.log(`[${name}] app=${info.app} err=${bucket.errors.length} exc=${bucket.exceptions.length} fail=${bucket.failed.length}`)
}

const tokenVal = { admin: ADMIN_TOKEN }

// ── 后台 4 新页（hash 路由）
const adminPages = [
  { url: '/#/mall/marketing/mall_seckill/list', name: '后台-秒杀活动' },
  { url: '/#/mall/marketing/mall_group/list', name: '后台-拼团活动' },
  { url: '/#/mall/marketing/mall_lottery/list', name: '后台-抽奖奖品' },
  { url: '/#/mall/marketing/mall_distribution/list', name: '后台-分销管理' }
]
await call('Page.navigate', { url: WEB + '/#/login' }); await sleep(1500)
await call('Runtime.evaluate', { expression: `localStorage.setItem('token','${ADMIN_TOKEN}');1` })
for (const p of adminPages) await visit(WEB, p.url, p.name, null)

// ── H5 匿名可看 3 页
const h5Pages = [
  { url: '/seckill', name: 'H5-秒杀列表' },
  { url: '/seckill/9001', name: 'H5-秒杀详情' },
  { url: '/group', name: 'H5-拼团列表' },
  { url: '/group/9002', name: 'H5-拼团详情' },
  { url: '/lottery', name: 'H5-抽奖列表' },
  { url: '/lottery/9101', name: 'H5-抽奖页' }
]
for (const p of h5Pages) await visit(H5, p.url, p.name, null)

// ── PC 匿名可看 3 页
const pcPages = [
  { url: '/seckill', name: 'PC-秒杀列表' },
  { url: '/seckill/9001', name: 'PC-秒杀详情' },
  { url: '/group', name: 'PC-拼团列表' },
  { url: '/group/9002', name: 'PC-拼团详情' },
  { url: '/lottery', name: 'PC-抽奖列表' }
]
for (const p of pcPages) await visit(PC, p.url, p.name, null)

console.log('\n===== 问题页面 =====')
let bad = 0
for (const r of results) {
  if (!r.errors.length && !r.exceptions.length && !r.failed.length && r.app > 200) continue
  bad++
  console.log(`\n### ${r.name} ${r.url}`)
  if (r.app <= 200) console.log('  !! 疑似白屏 app=' + r.app)
  r.exceptions.slice(0, 3).forEach(e => console.log('  EXC ' + e.replace(/\n/g, ' | ').slice(0, 250)))
  r.errors.slice(0, 4).forEach(e => console.log('  ERR ' + e.replace(/\n/g, ' | ').slice(0, 250)))
  r.failed.slice(0, 5).forEach(e => console.log('  NET ' + e))
}
console.log(bad === 0 ? '\nALL PASS' : `\n${bad} 页有问题`)
chrome.kill(); process.exit(bad === 0 ? 0 : 1)
