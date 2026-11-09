// 自回报诊断服务：用户浏览器打开 http://localhost:9442 即自动把诊断结果
// POST 回 /report 落盘，供本侧读取。纯静态 HTML + 原生 JS，无任何依赖。
import http from 'node:http'
import { writeFileSync, appendFileSync, existsSync } from 'node:fs'

const REPORT = 'D:/workspace/project/artifacts/browser-diag.jsonl'
if (!existsSync('D:/workspace/project/artifacts')) { try { mkdirSync('D:/workspace/project/artifacts') } catch {} }

const PAGE = `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>浏览器诊断</title>
<style>body{font:14px/1.7 Consolas,monospace;max-width:860px;margin:24px auto;padding:0 16px}
table{border-collapse:collapse;width:100%}td,th{border:1px solid #ccc;padding:4px 10px;text-align:left}
.ok{color:#0a0}.bad{color:#c00}.warn{color:#b60}</style></head><body>
<h2>悦集商城 · 浏览器环境诊断</h2>
<p>正在自动检测，请勿关闭本页……检测结果会自动回传给开发者，页面下方也会同步显示。</p>
<table id="t"></table>
<pre id="extra"></pre>
<script>
const rows = []
function add(k, v, cls) { rows.push({ k, v: String(v) }); const t = document.getElementById('t')
  const tr = t.insertRow(); tr.innerHTML = '<td>' + k + '</td><td class="' + (cls||'') + '">' + String(v).replace(/</g,'&lt;') + '</td>' }
async function tryFetch(name, url) {
  const t0 = Date.now()
  try {
    const r = await fetch(url, { cache: 'no-store' })
    const txt = await r.text()
    add(name, 'HTTP ' + r.status + '，' + txt.length + ' 字节，' + (Date.now()-t0) + 'ms', r.ok ? 'ok' : 'bad')
    return { status: r.status, len: txt.length, head: txt.slice(0, 120) }
  } catch (e) { add(name, '失败: ' + e.message, 'bad'); return { error: e.message } }
}
(async () => {
  add('userAgent', navigator.userAgent)
  add('language', navigator.language)
  add('ES模块支持', typeof Symbol !== 'undefined' && (function(){ try { new Function('return import("")') ; return 'dynamic-import OK' } catch(e){ return '不支持: ' + e.message } })(), 'ok')
  add('ES2020可选链', (() => { try { new Function('const a={b:{c:1}};return a?.b?.c??0'); return 'OK' } catch(e){ return '不支持' } })(), 'ok')

  const r1 = await tryFetch('GET localhost:9441（后台生产包）', 'http://localhost:9441/')
  const r2 = await tryFetch('GET 127.0.0.1:9441', 'http://127.0.0.1:9441/')
  const r3 = await tryFetch('GET localhost:9431（后台dev）', 'http://localhost:9431/')
  const r4 = await tryFetch('GET localhost:9432（PC商城）', 'http://localhost:9432/')
  const r5 = await tryFetch('GET localhost:9531（H5）', 'http://localhost:9531/')
  const r6 = await tryFetch('GET 9441生产接口/captcha', 'http://localhost:9441/prod-api/api/mall/auth/captcha')

  // iframe 实测：能否真的渲染出 9441 页面
  const f = document.createElement('iframe')
  f.style.cssText = 'width:600px;height:200px;border:1px solid #999'
  document.getElementById('extra').before(f)
  f.src = 'http://localhost:9441/login'
  const frameOk = await new Promise(res => {
    f.onload = () => setTimeout(() => {
      try { res(f.contentDocument && f.contentDocument.body.innerText.length > 0 ? 'iframe 内有内容 ' + f.contentDocument.body.innerText.slice(0,40) : 'iframe 已加载但 body 为空') }
      catch (e) { res('iframe 跨域不可读（已加载）') }
    }, 2500)
    setTimeout(() => res('iframe 5秒未触发 onload'), 5000)
  })
  add('iframe 加载 9441/login', frameOk, frameOk.includes('不可读') || frameOk.includes('有内容') ? 'ok' : 'bad')

  const payload = { ua: navigator.userAgent, time: new Date().toISOString(), r1, r2, r3, r4, r5, r6, frameOk }
  document.getElementById('extra').textContent = '诊断数据已自动回传，谢谢！以上表格就是你的浏览器看到的真实情况。'
  await fetch('/report', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
})()
</script></body></html>`

const server = http.createServer((req, res) => {
  if (req.url === '/canary') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
    res.end(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>金丝雀</title></head>
<body style="background:#ffff00">
<h1 style="color:#f00;font-size:48px">✅ 如果你能看到红字黄底，说明这个浏览器渲染完全正常</h1>
<p style="font-size:24px">当前时间：<span id="t"></span>（每秒跳动 = JS 正常）</p>
<script>setInterval(function(){document.getElementById('t').textContent = new Date().toLocaleTimeString()},1000)</script>
</body></html>`)
    return
  }
  if (req.method === 'POST' && req.url === '/report') {
    let body = ''
    req.on('data', c => body += c)
    req.on('end', () => {
      appendFileSync(REPORT, body + String.fromCharCode(10))
      console.log('[report received] ' + body.slice(0, 200))
      res.writeHead(200); res.end('ok')
    })
    return
  }
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
  res.end(PAGE)
})
server.listen(9442, () => console.log('diag server on http://localhost:9442'))
