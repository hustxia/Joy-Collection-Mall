import { spawn } from 'node:child_process'
import { writeFileSync, existsSync } from 'node:fs'
const exe = ['C:/Program Files/Google/Chrome/Application/chrome.exe'].find(existsSync)
const cp = spawn(exe, ['--user-data-dir=C:/tmp/mall/manual-shots/.c2','--headless=new','--remote-debugging-port=9781','--window-size=1400,1200','about:blank'],{stdio:'ignore'})
await new Promise(r=>setTimeout(r,2500))
const list = await (await fetch('http://127.0.0.1:9781/json/list')).json()
const page = list.find(t=>t.type==='page')
const ws = new WebSocket(page.webSocketDebuggerUrl)
await new Promise(r=>{ws.onopen=r})
let seq=0
const call=(m,p)=>new Promise(res=>{const id=++seq;const h=e=>{const x=JSON.parse(e.data);if(x.id===id){ws.removeEventListener('message',h);res(x.result)}};ws.addEventListener('message',h);ws.send(JSON.stringify({id,method:m,params:p}))})
await call('Page.enable',{}); await call('Runtime.enable',{})
await call('Emulation.setDeviceMetricsOverride',{width:1400,height:1200,deviceScaleFactor:1,mobile:false})
const url='file:///C:/Users/xiayuhong/WorkBuddy/2026-10-28-18-21-18/%E6%82%A6%E9%9B%86%E5%95%86%E5%9F%8E-%E6%93%8D%E4%BD%9C%E6%89%8B%E5%86%8C/%E6%82%A6%E9%9B%86%E5%95%86%E5%9F%8E-%E5%8A%9F%E8%83%BD%E6%93%8D%E4%BD%9C%E6%89%8B%E5%86%8C.html'
await call('Page.navigate',{url})
await new Promise(r=>setTimeout(r,3500))
// 统计图片加载状态
const st = await call('Runtime.evaluate',{expression:`JSON.stringify({total:document.images.length,broken:[...document.images].filter(i=>!i.complete||i.naturalWidth===0).map(i=>i.src.split('/').pop()),h:document.body.scrollHeight})`,returnByValue:true})
console.log(st.result.value)
for (const [sel,out] of [['#s2-3','render-s2.png'],['#s3-5','render-s3.png']]) {
  await call('Runtime.evaluate',{expression:`document.querySelector('${sel}').scrollIntoView()`})
  await new Promise(r=>setTimeout(r,1200))
  const s = await call('Page.captureScreenshot',{format:'png'})
  writeFileSync(out, Buffer.from(s.data,'base64'))
}
cp.kill()
