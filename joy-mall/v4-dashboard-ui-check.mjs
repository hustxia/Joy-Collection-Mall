// V4 P0-2 经营看板 后台 UI 走查（CDP，单 Chrome 实例）
// 校验：后台经营看板页渲染、8 个指标卡有数值、Top5 表格有行、无 console error
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { execFileSync } from 'node:child_process'

const SHOTS = 'C:/Users/xiayuhong/WorkBuddy/2026-10-20-14-50-30/shots/v4-dashboard'
mkdirSync(SHOTS, { recursive: true })
const sleep = ms => new Promise(r => setTimeout(r, ms))
let pass = 0, fail = 0
const ok = (cond, name, extra = '') => {
  if (cond) { pass++; console.log(`  OK   ${name}${extra ? '  [' + extra + ']' : ''}`) }
  else { fail++; console.log(`  FAIL ${name}${extra ? '  [' + extra + ']' : ''}`) }
}

// ---- admin token：命令行优先，否则直插 sys_session ----
const MYSQL = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
function adminToken() {
  if (process.argv[2]) return process.argv[2]
  const token = 'dui_' + Math.random().toString(36).slice(2) + Date.now().toString(36)
  const sql = `INSERT INTO sys_session (token, username, login_ip, expire_time, deleted)
    VALUES ('${token}','admin','127.0.0.1', DATE_ADD(NOW(), INTERVAL 8 HOUR), 0)
    ON DUPLICATE KEY UPDATE expire_time = DATE_ADD(NOW(), INTERVAL 8 HOUR);`
  execFileSync(MYSQL, ['-uroot', '-proot', '-h127.0.0.1', 'yueji_mall', '--default-character-set=utf8mb4', '-e', sql], { stdio: 'ignore' })
  return token
}

// ---------- Chrome ----------
const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', 'C:/Program Files/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
const dir = mkdtempSync(tmpdir() + '/dbui-')
const chrome = spawn(exe, [`--user-data-dir=${dir}`, '--headless=new', '--remote-debugging-port=9801', '--no-first-run', '--no-default-browser-check', '--window-size=1440,900', '--hide-scrollbars', 'about:blank'], { stdio: 'ignore' })
await sleep(2600)
const list = await (await fetch('http://127.0.0.1:9801/json/list')).json()
const target = list.find(t => t.type === 'page') || list[0]
const ws = new WebSocket(target.webSocketDebuggerUrl)
await new Promise(r => { ws.onopen = r })
let seq = 0
const call = (method, params) => new Promise(res => {
  const mid = ++seq
  const h = ev => { const m = JSON.parse(ev.data); if (m.id === mid) { ws.removeEventListener('message', h); res(m.result) } }
  ws.addEventListener('message', h)
  ws.send(JSON.stringify({ id: mid, method, params }))
})
let consoleErrors = []
ws.addEventListener('message', ev => {
  const m = JSON.parse(ev.data)
  if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') consoleErrors.push(m.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 200))
  if (m.method === 'Runtime.exceptionThrown') { const d = m.params.exceptionDetails; consoleErrors.push((d?.exception?.description || d?.text || '').slice(0, 200)) }
})
await call('Page.enable', {}); await call('Runtime.enable', {})
const evalJs = async expr => (await call('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true }))?.result?.value
const shot = async name => { const { data } = await call('Page.captureScreenshot', { format: 'png' }); writeFileSync(`${SHOTS}/${name}`, Buffer.from(data, 'base64')) }
const viewport = (w, h) => call('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: w < 800 })
const nav = async (url, wait = 2600) => { await call('Page.navigate', { url }); await sleep(wait) }
const text = () => evalJs('document.body.innerText.replace(/\\s+/g," ").trim()')
const setLocal = (k, v) => evalJs(`localStorage.setItem(${JSON.stringify(k)}, ${JSON.stringify(v)})`)

const ADMIN = adminToken()
console.log('== 经营看板 后台 UI 走查 (9431) ==')
await viewport(1440, 900)
await nav('http://localhost:9431/#/login')
await setLocal('token', ADMIN)
await setLocal('perms', JSON.stringify(['*:*:*']))
await nav('http://localhost:9431/#/mall/other/mall_dashboard/index', 3200)

const t = await text()
ok(/经营看板/.test(t), '页面标题渲染', 't=' + t.slice(0, 40))
ok(/成交金额 GMV/.test(t) && /客单价/.test(t) && /售后率/.test(t), '核心指标卡文案齐全')

// 8 个 stat-value 是否都有非空数值
const statInfo = await evalJs(`(() => {
  const els = [...document.querySelectorAll('.stat-value')].filter(e => e.offsetParent !== null);
  const vals = els.map(e => (e.innerText || '').replace(/\\s+/g,''));
  return { count: els.length, empty: vals.filter(v => !v || v === '¥0.00' || v === '¥0.0' || v === '0' ).length, sample: vals.slice(0, 8) };
})()`)
ok(statInfo.count >= 8, `指标卡数量>=8 (${statInfo.count})`, JSON.stringify(statInfo.sample))
ok(statInfo.empty === 0, `指标卡无空值 (empty=${statInfo.empty})`, JSON.stringify(statInfo.sample))

// Top5 表格行
const rows = await evalJs(`(() => {
  const r = [...document.querySelectorAll('.el-table__row')].filter(e => e.offsetParent !== null);
  return r.length;
})()`)
ok(rows >= 1, `Top5 表格有数据行 (${rows} 行)`)

// 切换「今日」验证响应式（不报错即可）
await evalJs(`(() => { const b=[...document.querySelectorAll('.el-radio-button')].find(e=>e.offsetParent!==null && (e.innerText||'').includes('今日')); if(b) b.click(); })()`)
await sleep(1200)
const t2 = await text()
ok(/经营看板/.test(t2), '切换时间范围后仍渲染')

await shot('dashboard.png')
const errs = consoleErrors.length
console.log(`console error：${errs}`)
for (const e of [...new Set(consoleErrors)]) console.log('  [err]', e)
ok(errs === 0, '无 console error')

console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
process.exit(fail === 0 ? 0 : 1)
