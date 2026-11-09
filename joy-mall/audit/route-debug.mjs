#!/usr/bin/env node
// 诊断：打开 /goods/1001，打印最终 URL / 路由 / 页面标题 / app 内容摘要
import { spawn } from 'node:child_process'

const port = process.argv[2] || '9433'
const chromePaths = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'
]
const { existsSync, mkdtempSync } = await import('node:fs')
const { tmpdir } = await import('node:os')
const exe = chromePaths.find(p => existsSync(p))
if (!exe) { console.error('no chrome'); process.exit(2) }
const dir = mkdtempSync(tmpdir() + '/uidbg-')
const cp = spawn(exe, [
  `--user-data-dir=${dir}`, '--headless=new', '--remote-debugging-port=9777',
  '--no-first-run', '--no-default-browser-check', 'about:blank'
], { stdio: 'ignore' })
await new Promise(r => setTimeout(r, 2500))

const list = await (await fetch('http://127.0.0.1:9777/json/list')).json()
const page = list.find(t => t.type === 'page') || list[0]
const ws = new WebSocket(page.webSocketDebuggerUrl)
await new Promise(r => { ws.onopen = r })
let id = 0
const call = (method, params) => new Promise(res => {
  const mid = ++id
  const h = ev => {
    const m = JSON.parse(ev.data)
    if (m.id === mid) { ws.removeEventListener('message', h); res(m.result) }
  }
  ws.addEventListener('message', h)
  ws.send(JSON.stringify({ id: mid, method, params }))
})
await call('Page.enable', {})
await call('Runtime.enable', {})
const evs = []
ws.addEventListener('message', ev => {
  const m = JSON.parse(ev.data)
  if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') {
    evs.push(m.params.args.map(a => a.value || a.description || '').join(' '))
  }
})
await call('Page.navigate', { url: `http://localhost:${port}/goods/1001` })
await new Promise(r => setTimeout(r, 6000))
const r1 = await call('Runtime.evaluate', { expression: 'JSON.stringify({url: location.href, title: document.title, appLen: (document.getElementById("app")||{}).innerHTML?.length, hasSkuTable: !!document.querySelector(".sku-table, .sku-row"), hasGoodsFlow: !!document.querySelector(".goods-flow")})' })
console.log('STATE:', r1.result.value)
console.log('CONSOLE ERRORS:', evs.length ? evs : '(none)')
cp.kill()
process.exit(0)
