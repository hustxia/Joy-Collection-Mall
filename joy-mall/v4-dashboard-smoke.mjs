// V4 P0-2 经营看板冒烟测试
// 校验 /mall_dashboard/overview 三种时间范围（today/7d/30d）的返回结构与数值合理性
//
// 鉴权：后台端点需有效 token。脚本自己用 mysql 直插 sys_session 生成 admin 令牌
// （与项目「直插 sys_session」既有做法一致），也可通过命令行参数传入现成 token：
//   node v4-dashboard-smoke.mjs <adminToken>

import { execFileSync } from 'node:child_process'

const BASE = 'http://127.0.0.1:8080'   // 走网关（单体 8431 已下线）
const MYSQL = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'

let pass = 0, fail = 0
const ok = (cond, name, extra = '') => {
  if (cond) { pass++; console.log(`  PASS  ${name}`) }
  else { fail++; console.log(`  FAIL  ${name}  ${extra}`) }
}

// ---- 获取 admin token：命令行优先，否则直插 sys_session ----
function adminToken() {
  if (process.argv[2]) return process.argv[2]
  const token = 'smk_' + Math.random().toString(36).slice(2) + Date.now().toString(36)
  const sql = `INSERT INTO sys_session (token, username, login_ip, expire_time, deleted)
    VALUES ('${token}','admin','127.0.0.1', DATE_ADD(NOW(), INTERVAL 8 HOUR), 0)
    ON DUPLICATE KEY UPDATE expire_time = DATE_ADD(NOW(), INTERVAL 8 HOUR);`
  execFileSync(MYSQL, ['-uroot', '-proot', '-h127.0.0.1', 'yueji_mall',
    '--default-character-set=utf8mb4', '-e', sql], { stdio: 'ignore' })
  return token
}

const req = async (path, token) => {
  const r = await fetch(BASE + path, { headers: { Authorization: 'Bearer ' + token } })
  let j = null
  try { j = await r.json() } catch (e) { /* 非 JSON */ }
  return { status: r.status, json: j }
}

const biz = (j) => (j && (j.code || j.status)) || (j ? 200 : 0)

function assertOverview(j, range) {
  ok(biz(j) === 200, `overview(${range}) 成功`, JSON.stringify(j).slice(0, 160))
  const d = j?.data
  ok(d && typeof d === 'object', `overview(${range}) 含 data`)
  if (!d) return
  ok(typeof d.gmv === 'number' && d.gmv >= 0, `gmv 数值>=0 (${d.gmv})`)
  ok(typeof d.orderCount === 'number' && d.orderCount >= 0, `orderCount 数值>=0 (${d.orderCount})`)
  ok(typeof d.avgOrderAmount === 'number' && d.avgOrderAmount >= 0, `avgOrderAmount 数值>=0 (${d.avgOrderAmount})`)
  // 客单价一致性：整数除法 gmv/orderCount
  if (d.orderCount > 0) {
    ok(d.avgOrderAmount === Math.floor(d.gmv / d.orderCount),
      `avgOrderAmount == floor(gmv/orderCount)`, `${d.avgOrderAmount} vs ${Math.floor(d.gmv / d.orderCount)}`)
  }
  ok(typeof d.refundAmount === 'number' && d.refundAmount >= 0, `refundAmount 数值>=0 (${d.refundAmount})`)
  ok(typeof d.refundRate === 'number' && d.refundRate >= 0 && d.refundRate <= 1, `refundRate 在 [0,1] (${d.refundRate})`)
  ok(typeof d.pendingShipment === 'number' && d.pendingShipment >= 0, `pendingShipment>=0 (${d.pendingShipment})`)
  ok(typeof d.pendingRefundAudit === 'number' && d.pendingRefundAudit >= 0, `pendingRefundAudit>=0 (${d.pendingRefundAudit})`)
  ok(typeof d.newMembers === 'number' && d.newMembers >= 0, `newMembers>=0 (${d.newMembers})`)
  ok(Array.isArray(d.topGoods) && d.topGoods.length <= 5, `topGoods 数组且<=5 (len=${d.topGoods?.length})`)
  if (Array.isArray(d.topGoods)) {
    const sorted = [...d.topGoods].every((it, i, a) => i === 0 || a[i - 1].quantity >= it.quantity)
    ok(sorted, `topGoods 按成交量降序`)
    ok(d.topGoods.every(it => typeof it.goodsName === 'string' && typeof it.quantity === 'number'),
      `topGoods 字段完整`)
  }
}

(async () => {
  console.log('== V4 P0-2 经营看板冒烟 ==')
  const token = adminToken()
  console.log('  token:', token.slice(0, 20) + '...')

  for (const range of ['today', '7d', '30d']) {
    const r = await req(`/mall_dashboard/overview?range=${range}`, token)
    assertOverview(r.json, range)
  }

  // 异常 range 回落 30d（不应 500）
  const bad = await req('/mall_dashboard/overview?range=999d', token)
  ok(biz(bad.json) === 200, '异常 range 回落 30d 不报错', JSON.stringify(bad.json).slice(0, 120))

  console.log(`\n== 结果：${pass} 通过 / ${fail} 失败 ==`)
  process.exit(fail === 0 ? 0 : 1)
})()
