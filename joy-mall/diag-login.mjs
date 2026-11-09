// PC / H5 登录页真实交互复现：填表 → 点登录 → 抓 toast / console / 失败请求 / 最终地址
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const PC = 'http://localhost:9432'
const H5 = 'http://localhost:9531'
const OUT = 'C:/tmp/mall/artifacts/login'
mkdirSync(OUT, { recursive: true })
const sleep = ms => new Promise(r => setTimeout(r, ms))

const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'].find(p => existsSync(p))
const dir = mkdtempSync(tmpdir() + '/login-')
const port = 9815
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
const evaluate = async expr => {
  const r = await call('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
  return r.result && r.result.value
}
await call('Page.enable'); await call('Runtime.enable'); await call('Network.enable'); await call('Log.enable')

let bucket
function resetBucket() { bucket = { errors: [], failed: [], exceptions: [], reqs: [] } }
resetBucket()
listeners.push(m => {
  if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') bucket.errors.push(m.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 400))
  if (m.method === 'Runtime.exceptionThrown') bucket.exceptions.push((m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text || '').slice(0, 400))
  if (m.method === 'Network.responseReceived') {
    const r = m.params.response
    if (r.url.indexOf('/shop/') >= 0 || r.url.indexOf('/api/') >= 0) bucket.reqs.push(r.status + ' ' + r.url.slice(0, 130))
    if (r.status >= 400) bucket.failed.push(r.status + ' ' + r.url.replace(PC, '').replace(H5, '').slice(0, 150))
  }
})

async function shot(name) {
  const r = await call('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`${OUT}/${name}.png`, Buffer.from(r.data, 'base64'))
}

// 填值：触发 input/change，让 v-model 真正更新
const FILL = `(sel, val) => {
  const el = document.querySelectorAll(sel)
  return el.length
}`

async function run(name, base, phoneSel, passSel, btnSel) {
  resetBucket()
  await call('Page.navigate', { url: base + '/login' })
  await sleep(2500)
  await evaluate(`localStorage.clear(); sessionStorage.clear(); 1`)
  await call('Page.reload'); await sleep(2500)

  const filled = await evaluate(`(() => {
    const inputs = [...document.querySelectorAll('.login-card input, .login-form input, form input')]
    const phone = inputs.find(i => (i.placeholder||'').indexOf('手机') >= 0 || i.type === 'tel' || i.maxLength === 11)
    const pass = inputs.find(i => i.type === 'password')
    const setVal = (el, v) => {
      el.focus(); el.value = v
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
    }
    if (!phone || !pass) return 'NO_INPUTS:' + inputs.map(i => i.type + '/' + i.placeholder).join(',')
    setVal(phone, '13800000001'); setVal(pass, '123456')
    return 'ok:' + phone.value + '/' + pass.value
  })()`)
  console.log(`\n[${name}] 填表: ${filled}`)
  await shot(name + '-1-form')

  const clicked = await evaluate(`(() => {
    const btns = [...document.querySelectorAll('button')]
    const b = btns.find(x => x.innerText.replace(/\\s/g,'').indexOf('登') >= 0)
    if (!b) return 'NO_BTN:' + btns.map(x=>x.innerText.replace(/\\s/g,'')).join('|').slice(0,120)
    b.click(); return 'clicked:' + b.innerText.replace(/\\s/g,'')
  })()`)
  console.log(`[${name}] 点击: ${clicked}`)
  await sleep(3000)
  await shot(name + '-2-after')

  const state = await evaluate(`JSON.stringify({
    url: location.href,
    toast: [...document.querySelectorAll('.van-toast__text, .el-message__content, .el-message-box__message')].map(e=>e.innerText).join(' | '),
    bodyText: (document.body.innerText||'').replace(/\\s+/g,' ').slice(0, 200),
    token: !!(localStorage.getItem('mall-pc-token') || localStorage.getItem('mall-shop-token') || localStorage.getItem('token'))
  })`)
  console.log(`[${name}] 结果: ${state}`)
  console.log(`[${name}] 错误 console=${bucket.errors.length} exc=${bucket.exceptions.length} fail=${bucket.failed.length}`)
  bucket.errors.slice(0, 4).forEach(e => console.log('   ERR ' + e.replace(/\n/g, ' | ').slice(0, 250)))
  bucket.exceptions.slice(0, 3).forEach(e => console.log('   EXC ' + e.replace(/\n/g, ' | ').slice(0, 250)))
  bucket.failed.slice(0, 5).forEach(e => console.log('   NET ' + e))
  const shopReqs = bucket.reqs.filter(r => r.indexOf('/shop/') >= 0 || r.indexOf('/api/') >= 0)
  shopReqs.slice(0, 6).forEach(r => console.log('   REQ ' + r))
}

await run('pc', PC)
await run('h5', H5)

chrome.kill()
process.exit(0)
