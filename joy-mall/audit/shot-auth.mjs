#!/usr/bin/env node
// shot-auth.mjs —— 打开后台 → 注入 localStorage token → 跳转目标页 → 截图
import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const origin = process.argv[2]            // 如 http://localhost:9431
const route = process.argv[3]            // 如 mall_goods（不带前导斜杠，Git Bash 会转义 / 开头参数）
const out = process.argv[4] || 'shot.png'
const width = Number(process.argv[5] || 1440)
const height = Number(process.argv[6] || 900)
const path = '/' + String(route || '').replace(/^\/+/, '')
const token = (readFileSync('C:/tmp/mall/audit/admin-token.env', 'utf8').match(/TOKEN=(\S+)/) || [])[1]
if (!token) { console.error('no token'); process.exit(2) }

const chromePaths = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'
]
const exe = chromePaths.find(p => existsSync(p))
const dir = mkdtempSync(tmpdir() + '/shota-')
const cp = spawn(exe, [
  `--user-data-dir=${dir}`, '--headless=new', '--remote-debugging-port=9779',
  '--no-first-run', '--no-default-browser-check', `--window-size=${width},${height}`, 'about:blank'
], { stdio: 'ignore' })
await new Promise(r => setTimeout(r, 2500))
const list = await (await fetch('http://127.0.0.1:9779/json/list')).json()
const page = list.find(t => t.type === 'page') || list[0]
const ws = new WebSocket(page.webSocketDebuggerUrl)
await new Promise(r => { ws.onopen = r })
let seq = 0
const call = (method, params) => new Promise(res => {
  const mid = ++seq
  const h = ev => {
    const m = JSON.parse(ev.data)
    if (m.id === mid) { ws.removeEventListener('message', h); res(m.result) }
  }
  ws.addEventListener('message', h)
  ws.send(JSON.stringify({ id: mid, method, params }))
})
await call('Page.enable', {})
await call('Runtime.enable', {})
const errors = []
ws.addEventListener('message', ev => {
  const m = JSON.parse(ev.data)
  if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') {
    errors.push(m.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 300))
  }
  if (m.method === 'Runtime.exceptionThrown') {
    errors.push((m.params.exceptionDetails?.exception?.description || m.params.exceptionDetails?.text || '').slice(0, 300))
  }
})
await call('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false })
// 1. 先到同源页面注入 token
await call('Page.navigate', { url: origin + '/' })
await new Promise(r => setTimeout(r, 3000))
await call('Runtime.evaluate', {
  expression: `localStorage.setItem('token', ${JSON.stringify(token)}); localStorage.setItem('username', 'admin');`
})
// 2. 跳转目标页
await call('Page.navigate', { url: origin + '/#' + path })
await new Promise(r => setTimeout(r, 5000))
const state = await call('Runtime.evaluate', {
  expression: 'JSON.stringify({url: location.href, rows: document.querySelectorAll(".el-table__row").length})', returnByValue: true
})
const shot = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
writeFileSync(out, Buffer.from(shot.data, 'base64'))
console.log('SAVED', out, 'STATE', state.result?.value)
if (errors.length) console.log('CONSOLE ERRORS:', errors.slice(0, 5))
cp.kill()
process.exit(0)
