#!/usr/bin/env node
// V6 T-04 版本验收命令：一条命令跑完「接口冒烟 + 浏览器走查」两层
//
//   node scripts/verify-e2e.mjs            # 跑两层，任一失败退出码非 0
//   node scripts/verify-e2e.mjs --smoke    # 只跑接口层（CI 快速反馈）
//   node scripts/verify-e2e.mjs --ui       # 只跑浏览器层
//
// 依赖环境（三端 devServer + 网关 + 6 个服务已起）：
//   MALL_WEB_URL / MALL_PC_URL / MALL_H5_URL        三端地址（默认 9431/9432/9531）
//   MALL_MEMBER_PHONE / MALL_MEMBER_PASSWORD        会员账号
//   MALL_ADMIN_USERNAME / MALL_ADMIN_PASSWORD       后台账号
//   AUTH_CAPTCHA_EXPOSE_ANSWER=true                 目标环境允许脚本自动过验证码
//   MALL_ADMIN_TOKEN / MALL_MEMBER_TOKEN            也可直接给现成令牌（人工兜底）
//
// 退出码：0 全通过 / 1 有断言失败 / 2 环境不满足（服务没起、账号不通）
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '..')

const args = new Set(process.argv.slice(2))
const runSmoke = !args.has('--ui')
const runUi = !args.has('--smoke')

function runScript(file) {
  return new Promise((done) => {
    const path = resolve(root, file)
    if (!existsSync(path)) {
      console.error(`✗ 找不到脚本：${path}`)
      done(2)
      return
    }
    const child = spawn(process.execPath, [path], { cwd: root, stdio: 'inherit', env: process.env })
    child.on('exit', code => done(code === null ? 2 : code))
    child.on('error', err => {
      console.error(`✗ 启动失败 ${file}: ${err.message}`)
      done(2)
    })
  })
}

const results = []
const startedAt = Date.now()

if (runSmoke) {
  console.log('\n════════ 第一层：接口冒烟（走三端 devServer 代理，即前端真实请求路径）════════')
  const code = await runScript('mall-cloud-fe-smoke.mjs')
  results.push({ name: '接口冒烟', code })
}

if (runUi) {
  console.log('\n════════ 第二层：浏览器走查（36 页：渲染/文案/图片/console/请求）════════')
  const code = await runScript('mall-cloud-ui-check.mjs')
  results.push({ name: '浏览器走查', code })
}

const seconds = ((Date.now() - startedAt) / 1000).toFixed(1)
console.log('\n════════ 验收结果 ════════')
for (const r of results) {
  const label = r.code === 0 ? 'PASS' : r.code === 2 ? 'ENV ' : 'FAIL'
  console.log(`  ${label}  ${r.name}（exit ${r.code}）`)
}
console.log(`  用时 ${seconds}s`)

const worst = results.some(r => r.code === 1) ? 1 : results.some(r => r.code === 2) ? 2 : 0
process.exit(worst)
