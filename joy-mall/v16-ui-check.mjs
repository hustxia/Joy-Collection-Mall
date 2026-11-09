// V16 走查：在线 AI 客服（后台三页 + PC 侧滑窗 + H5 全屏页）
// 前置：11 个后端服务 + 网关 8080 + 三端 dev server（9431/9432/9531），LLM_PROVIDER=mock
// 判定：白屏 / console error / 异常 / 4xx-5xx 请求 / 关键元素缺失 / 发消息后无 AI 回复
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const WEB = 'http://127.0.0.1:9431', PC = 'http://127.0.0.1:9432', H5 = 'http://127.0.0.1:9531'
const GW = 'http://127.0.0.1:8080'
const SHOTS = 'D:/workspace/project/artifacts/v16'
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

// ---- 令牌 ----
const cap = await (await fetch(WEB + '/dev-api/api/mall/auth/captcha')).json()
const al = await post(WEB, '/dev-api/api/mall/auth/login', { username: 'admin', password: 'admin123', captchaId: cap.data.captchaId, captchaAnswer: String(cap.data.answer) })
const ADMIN = al.data && (al.data.token || al.data.accessToken)
if (!ADMIN) { console.error('admin login failed'); process.exit(2) }
const ml = await post(GW, '/shop/auth/login', { phone: '13800000001', password: '123456' })
const MEMBER = ml.data && (ml.data.token || ml.data.accessToken)
if (!MEMBER) { console.error('member login failed ' + JSON.stringify(ml).slice(0, 200)); process.exit(2) }
console.log('tokens ok')

// ---- CDP ----
const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
const chrome = spawn(exe, [`--user-data-dir=${mkdtempSync(tmpdir() + '/v16-')}`, '--headless=new', '--remote-debugging-port=9834', '--no-first-run', '--window-size=1440,900', '--hide-scrollbars', 'about:blank'], { stdio: 'ignore' })
await sleep(2600)
const list = await (await fetch('http://127.0.0.1:9834/json/list')).json()
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

// 悬浮球 / 聊天窗都要模拟移动端尺寸，H5 走 375x812
await call('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })

let bucket = { errors: [], failed: [], exceptions: [] }
listeners.push(m => {
  if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') bucket.errors.push(m.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 250))
  if (m.method === 'Runtime.exceptionThrown') { const d = m.params.exceptionDetails; bucket.exceptions.push((d.exception && d.exception.description || d.text || '').slice(0, 250)) }
  if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') bucket.errors.push('[log] ' + m.params.entry.text.slice(0, 200))
  if (m.method === 'Network.responseReceived') { const r = m.params.response; if (r.status >= 400 && r.url.indexOf('hot-update') < 0) bucket.failed.push(r.status + ' ' + r.url.slice(0, 140)) }
})

const results = []
const ev = async expr => {
  const r = await call('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
  return r.result && r.result.value
}
async function shot(name) {
  const s = await call('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`${SHOTS}/${name}.png`, Buffer.from(s.data, 'base64'))
}
async function visit(base, url, name, opts = {}) {
  bucket = { errors: [], failed: [], exceptions: [] }
  await call('Page.navigate', { url: base + url })
  await sleep(opts.wait || 2600)
  const info = JSON.parse(await ev(`JSON.stringify({app:document.querySelector('#app')?document.querySelector('#app').innerHTML.length:document.body.innerHTML.length, text:(document.body.innerText||'').replace(/\\s+/g,' ').slice(0,260)})`) || '{}')
  await shot(name)
  results.push({ name, url, ...info, ...bucket })
  console.log(`[${name}] app=${info.app} err=${bucket.errors.length} exc=${bucket.exceptions.length} fail=${bucket.failed.length}`)
}
// 输入框赋值（Vue 受控组件必须走原生 setter + input 事件）
const typeInto = (sel, text) => ev(`(() => {
  const el = document.querySelector('${sel}'); if (!el) return 'no-el';
  const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, ${JSON.stringify(text)});
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
  return 'ok';
})()`)

// ========== 1. 后台三页 ==========
await call('Page.navigate', { url: WEB + '/#/login' }); await sleep(1400)
await ev(`localStorage.setItem('token','${ADMIN}');1`)
for (const p of [
  { url: '/#/mall/aichat/mall_chat_session/index', name: '后台-AI会话记录' },
  { url: '/#/mall/aichat/mall_chat_faq/index', name: '后台-FAQ知识库' },
  { url: '/#/mall/aichat/mall_chat_stat/index', name: '后台-用量看板' }
]) await visit(WEB, p.url, p.name, { wait: 3000 })

// ========== 2. PC 侧滑聊天窗 ==========
await call('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
await call('Page.navigate', { url: PC + '/' }); await sleep(1600)
await ev(`localStorage.setItem('mall-pc-token','${MEMBER}');1`)
await call('Page.navigate', { url: PC + '/' }); await sleep(3000)
{
  bucket = { errors: [], failed: [], exceptions: [] }
  const ball = await ev(`!!document.querySelector('.ai-ball')`)
  const clicked = await ev(`(()=>{const b=document.querySelector('.ai-ball'); if(!b) return 'no-ball'; b.click(); return 'ok'})()`)
  await sleep(2200)
  await shot('PC-1-客服球打开')
  const typing = await typeInto('.ai-input-row textarea, .ai-input-row input', '运费怎么算')
  await sleep(400)
  const sent = await ev(`(()=>{const b=[...document.querySelectorAll('.ai-input-row button')].pop(); if(!b) return 'no-btn'; b.click(); return 'ok'})()`)
  await sleep(3200)
  await shot('PC-2-问答中')
  const st = JSON.parse(await ev(`JSON.stringify({
    ball: ${ball}, clicked: ${JSON.stringify(clicked)}, typing: ${JSON.stringify(typing)}, sent: ${JSON.stringify(sent)},
    rows: document.querySelectorAll('.ai-row').length,
    aiBubbles: document.querySelectorAll('.ai-bubble--ai').length,
    lastAi: (document.querySelector('.ai-row--ai:last-of-type .ai-bubble')||{}).innerText || '',
    chips: document.querySelectorAll('.ai-chip').length,
    cards: document.querySelectorAll('.ai-card').length,
    text: (document.body.innerText||'').replace(/\\s+/g,' ').slice(0,300)
  })`) || '{}')
  results.push({ name: 'PC-聊天窗交互', url: '/', app: 9999, ...st, ...bucket })
  console.log(`[PC-聊天窗交互] rows=${st.rows} ai=${st.aiBubbles} chips=${st.chips} cards=${st.cards} 回复="${(st.lastAi || '').slice(0, 40)}" err=${bucket.errors.length} fail=${bucket.failed.length}`)
  results[results.length - 1].assert = {
    panel: st.clicked === 'ok',
    replied: (st.lastAi || '').length > 5 && st.aiBubbles >= 2,
    chips: st.chips > 0
  }
}

// ========== 3. H5 全屏聊天页 ==========
await call('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true })
await call('Page.navigate', { url: H5 + '/' }); await sleep(1600)
await ev(`localStorage.setItem('mall-shop-token','${MEMBER}');1`)
await call('Page.navigate', { url: H5 + '/' }); await sleep(3000)
{
  bucket = { errors: [], failed: [], exceptions: [] }
  const ball = await ev(`!!document.querySelector('.svc-ball')`)
  const clicked = await ev(`(()=>{const b=document.querySelector('.svc-ball'); if(!b) return 'no-ball'; b.click(); return 'ok'})()`)
  await sleep(2800)
  const route = await ev(`location.hash || location.pathname`)
  await shot('H5-1-客服页(悬浮球进入)')
  const typing = await typeInto('.svc-input-row input, .svc-input-row textarea', '七天无理由退货吗')
  await sleep(400)
  const sent = await ev(`(()=>{const b=[...document.querySelectorAll('.svc-input-row button')].pop(); if(!b) return 'no-btn'; b.click(); return 'ok'})()`)
  // mock 流式输出需要数秒，轮询等最后一条 AI 气泡出现非空文字（最长 10s）
  let lastAi = ''
  for (let i = 0; i < 10; i++) {
    await sleep(1000)
    lastAi = String(await ev(`(()=>{const bs=document.querySelectorAll('.svc-bubble--ai'); return (bs.length?bs[bs.length-1].innerText:'')||''})()`) || '')
    if (lastAi.length > 5) break
  }
  await shot('H5-2-问答中')
  const st = JSON.parse(await ev(`JSON.stringify({
    ball: ${ball}, clicked: ${JSON.stringify(clicked)}, typing: ${JSON.stringify(typing)}, sent: ${JSON.stringify(sent)},
    rows: document.querySelectorAll('.svc-row').length,
    aiBubbles: document.querySelectorAll('.svc-bubble--ai').length,
    lastAi: ${JSON.stringify(lastAi)},
    chips: document.querySelectorAll('.svc-chip').length,
    text: (document.body.innerText||'').replace(/\\s+/g,' ').slice(0,300)
  })`) || '{}')
  results.push({ name: 'H5-聊天页交互', url: String(route), app: 9999, ...st, ...bucket })
  console.log(`[H5-聊天页交互] route=${route} rows=${st.rows} ai=${st.aiBubbles} 回复="${(st.lastAi || '').slice(0, 40)}" err=${bucket.errors.length} fail=${bucket.failed.length}`)
  results[results.length - 1].assert = {
    entered: String(route).includes('service-chat'),
    replied: (st.lastAi || '').length > 5 && st.aiBubbles >= 2
  }
}

// ========== 汇总 ==========
console.log('\n===== 问题页面 =====')
let bad = 0
for (const r of results) {
  const missing = r.assert ? Object.entries(r.assert).filter(([, v]) => !v).map(([k]) => k) : []
  if (!r.errors.length && !r.exceptions.length && !r.failed.length && r.app > 200 && !missing.length) continue
  bad++
  console.log(`\n### ${r.name} ${r.url}`)
  if (missing.length) console.log('  !! 断言未通过: ' + missing.join(', '))
  if (r.app <= 200) console.log('  !! 疑似白屏 app=' + r.app)
  r.exceptions.slice(0, 3).forEach(e => console.log('  EXC ' + e.replace(/\n/g, ' | ').slice(0, 220)))
  r.errors.slice(0, 4).forEach(e => console.log('  ERR ' + e.replace(/\n/g, ' | ').slice(0, 220)))
  r.failed.slice(0, 5).forEach(e => console.log('  NET ' + e))
}
console.log(bad === 0 ? '\nALL PASS' : `\n${bad} 处有问题`)
console.log('截图目录: ' + SHOTS)
chrome.kill(); process.exit(bad === 0 ? 0 : 1)
