#!/usr/bin/env node
// shot.mjs —— headless Chrome 打开指定 URL 并截图（不依赖 ui-check.mjs）
import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'

const url = process.argv[2]
const out = process.argv[3] || 'shot.png'
const width = Number(process.argv[4] || 1440)
const height = Number(process.argv[5] || 900)
const wait = Number(process.argv[6] || 5000)
if (!url) { console.error('usage: node shot.mjs <url> <out.png> [w] [h] [waitMs]'); process.exit(2) }

const chromePaths = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'
]
const exe = chromePaths.find(p => existsSync(p))
if (!exe) { console.error('no chrome/edge'); process.exit(2) }
const dir = mkdtempSync(tmpdir() + '/shot-')
const cp = spawn(exe, [
  `--user-data-dir=${dir}`, '--headless=new', '--remote-debugging-port=9778',
  '--no-first-run', '--no-default-browser-check', `--window-size=${width},${height}`, 'about:blank'
], { stdio: 'ignore' })
await new Promise(r => setTimeout(r, 2500))

const list = await (await fetch('http://127.0.0.1:9778/json/list')).json()
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
await call('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 500 })
await call('Page.navigate', { url })
await new Promise(r => setTimeout(r, wait))
const state = await call('Runtime.evaluate', {
  expression: 'JSON.stringify({url: location.href, title: document.title})', returnByValue: true
})
const shot = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
writeFileSync(out, Buffer.from(shot.data, 'base64'))
console.log('SAVED', out, 'STATE', state.result?.value)
cp.kill()
process.exit(0)
