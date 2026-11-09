// V15C 端到端：分销全链路（对共享网关 http://127.0.0.1:8080）
// 流程：后台登录 → 会员 A 登录 → GET /shop/distributor/info
//   → 未申请则 POST /shop/distributor/apply；status=0（待审）则后台 POST /mall_distributor/audit 通过
//   → 断言 A 已启用（status=1）并持有邀请码
//   → 注册会员 B → POST /shop/distributor/bind 用 A 的邀请码绑定 → A 的 GET /shop/distributor/team 断言含 B
//   → GET /shop/distributor/commission 断言 records 结构
//   → POST /shop/distributor/withdraw：有可提现余额则真实提现，否则断言业务 400 文案
//   → 后台 POST /mall_commission/page、POST /mall_withdrawal/page 断言 A 的台账可见
// 契约：id 全字符串、金额分、业务错误 HTTP 200 + body code != 200。
// 退出码：0 通过；1 断言失败；2 环境前置不满足；3 缺业务前置数据（如无已结算佣金）——明确打印，不静默通过。
const GATEWAY = process.env.MALL_GATEWAY || 'http://127.0.0.1:8080'
const MEMBER_PHONE = process.env.MALL_MEMBER_PHONE || '13800000001'
const MEMBER_PASSWORD = process.env.MALL_MEMBER_PASSWORD || '123456'
const ADMIN_USERNAME = process.env.MALL_ADMIN_USERNAME || 'admin'
const ADMIN_PASSWORD = process.env.MALL_ADMIN_PASSWORD || 'admin123'

let pass = 0, fail = 0
const fails = []
const ok = (cond, name, extra = '') => {
  if (cond) { pass++; console.log(`  OK   ${name}${extra ? '  [' + extra + ']' : ''}`) }
  else { fail++; fails.push(name); console.log(`  FAIL ${name}${extra ? '  [' + extra + ']' : ''}`) }
}
const step = t => console.log(`\n── ${t}`)

async function req(path, { method = 'GET', body, token } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers['Authorization'] = 'Bearer ' + token
  const res = await fetch(GATEWAY + path, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let json
  try { json = JSON.parse(text) } catch { json = { raw: text.slice(0, 200) } }
  return { status: res.status, json, text }
}

async function loginAdmin() {
  const cap = await req('/auth/captcha')
  const answer = cap.json && cap.json.data && cap.json.data.answer
  if (!answer) {
    console.error('环境前置不满足：/auth/captcha 未开放验证码答案（需 AUTH_CAPTCHA_EXPOSE_ANSWER=true）')
    process.exit(2)
  }
  const login = await req('/auth/login', {
    method: 'POST',
    body: {
      username: ADMIN_USERNAME, password: ADMIN_PASSWORD,
      captchaId: cap.json.data.captchaId, captchaAnswer: String(answer),
    },
  })
  const token = login.json && login.json.data && login.json.data.token
  if (!token) {
    console.error('后台登录失败：' + JSON.stringify(login.json).slice(0, 200))
    process.exit(2)
  }
  return token
}

// ───────────── 1. 登录 ─────────────
step('1. 后台与会员 A 登录')
const adminToken = await loginAdmin()
ok(!!adminToken, '后台 admin 登录')

const loginA = await req('/shop/auth/login', {
  method: 'POST', body: { phone: MEMBER_PHONE, password: MEMBER_PASSWORD },
})
const tokenA = loginA.json && loginA.json.data && loginA.json.data.token
ok(!!tokenA, '会员 A 登录', MEMBER_PHONE)
const profileA = await req('/shop/auth/profile', { token: tokenA })
const memberAId = String((profileA.json.data || {}).id || '')
ok(memberAId.length > 0, '会员 A id（字符串）', memberAId)

// ───────────── 2. 分销员资格：info → apply → 审核通过 ─────────────
step('2. 会员 A 的分销员资格')
let infoRes = await req('/shop/distributor/info', { token: tokenA })
let info = infoRes.json.data
ok(infoRes.json.code === 200, 'GET /shop/distributor/info 可查', info ? '已有记录' : 'null（未申请）')

if (!info || !info.id) {
  const applyRes = await req('/shop/distributor/apply', { method: 'POST', token: tokenA })
  ok(applyRes.json.code === 200, 'POST /shop/distributor/apply 申请', applyRes.json.msg)
  infoRes = await req('/shop/distributor/info', { token: tokenA })
  info = infoRes.json.data
  ok(!!(info && info.id), '申请后 info 有记录')
}
const distributorId = String(info.id)
ok(distributorId.length > 0, '分销员 id 为字符串', distributorId)
ok(!!info.inviteCode, '持有邀请码', info.inviteCode)

if (Number(info.status) === 0) {
  step('2.1 待审核 → 后台审核通过')
  const auditRes = await req('/mall_distributor/audit', {
    method: 'POST', token: adminToken,
    body: { id: info.id, pass: true, rejectReason: null },
  })
  ok(auditRes.json.code === 200, '后台 POST /mall_distributor/audit 通过', auditRes.json.msg)
  infoRes = await req('/shop/distributor/info', { token: tokenA })
  info = infoRes.json.data
} else if (Number(info.status) === 3) {
  step('2.1 已被驳回 → 重新申请并审核通过')
  await req('/shop/distributor/apply', { method: 'POST', token: tokenA })
  const auditRes = await req('/mall_distributor/audit', {
    method: 'POST', token: adminToken,
    body: { id: info.id, pass: true, rejectReason: null },
  })
  ok(auditRes.json.code === 200, '后台审核通过', auditRes.json.msg)
  infoRes = await req('/shop/distributor/info', { token: tokenA })
  info = infoRes.json.data
} else if (Number(info.status) === 2) {
  step('2.1 已停用 → 后台启用')
  const enableRes = await req(`/mall_distributor/enable/${info.id}`, { method: 'POST', token: adminToken })
  ok(enableRes.json.code === 200, '后台 POST /mall_distributor/enable', enableRes.json.msg)
  infoRes = await req('/shop/distributor/info', { token: tokenA })
  info = infoRes.json.data
}
ok(Number(info.status) === 1, '分销员已启用（status=1）', String(info.status))
const inviteCode = info.inviteCode
ok(!!inviteCode, '邀请码可用', inviteCode)

// ───────────── 3. 会员 B 注册并用 A 的邀请码绑定 ─────────────
step('3. 下级绑定（邀请码）')
const phoneB = '139' + String(Date.now()).slice(-8)
const regB = await req('/shop/auth/register', {
  method: 'POST', body: { phone: phoneB, password: 'v15c123456', nickname: 'V15C下级B', agreed: true },
})
const tokenB = regB.json && regB.json.data && regB.json.data.token
ok(regB.json.code === 200 && !!tokenB, '注册会员 B', regB.json.msg || phoneB)
const profileB = await req('/shop/auth/profile', { token: tokenB })
const memberBId = String((profileB.json.data || {}).id || '')
ok(memberBId.length > 0, '会员 B id', memberBId)

const bindRes = await req('/shop/distributor/bind', {
  method: 'POST', token: tokenB,
  body: { inviteCode, channel: 'v15c-e2e' },
})
// 全新手机号必然未绑定过；若仍报已绑定则视为环境脏数据失败
ok(bindRes.json.code === 200, 'B 用 A 的邀请码绑定', bindRes.json.msg)

const teamRes = await req('/shop/distributor/team', { token: tokenA })
const team = teamRes.json.data || []
ok(teamRes.json.code === 200 && Array.isArray(team), 'A 的团队列表可查', `${team.length} 人`)
ok(team.some(t => String(t.inviteeId) === memberBId), '团队中包含 B', `inviteeId=${memberBId}`)

// ───────────── 4. 佣金明细（结构断言） ─────────────
step('4. 佣金明细')
const commRes = await req('/shop/distributor/commission?pageNum=1&pageSize=20', { token: tokenA })
const commData = commRes.json.data || {}
const records = commData.records
ok(commRes.json.code === 200 && Array.isArray(records), 'GET /shop/distributor/commission 返回 records 数组',
  `${(records || []).length} 条 / total=${commData.total}`)
ok(commData.total !== undefined && Number.isFinite(Number(commData.total)), 'total 字段存在', String(commData.total))
if (records && records.length) {
  const r = records[0]
  ok(r.id !== undefined && r.amount !== undefined && r.status !== undefined,
    '佣金记录含 id/amount/status（id 字符串、金额分）', `id=${String(r.id)} amount=${String(r.amount)} status=${String(r.status)}`)
} else {
  console.log('  WARN 会员 A 名下暂无佣金明细记录（结构断言已通过，金额字段断言跳过）')
}

// ───────────── 5. 提现 ─────────────
step('5. 提现')
const withdrawable = Number(info.withdrawable || 0)
let withdrawReal = false
if (withdrawable > 0) {
  const amount = Math.min(withdrawable, 1) // 金额分：最小提现 1 分即可验证链路
  const wRes = await req('/shop/distributor/withdraw', {
    method: 'POST', token: tokenA, body: { amount, method: 0 },
  })
  ok(wRes.json.code === 200, `提现申请成功（${amount} 分）`, wRes.json.msg)
  withdrawReal = wRes.json.code === 200
} else {
  console.log('  缺前置数据：会员 A 可提现余额为 0（无已结算佣金），仅验证未启用/余额不足的业务 400 文案')
  const wRes = await req('/shop/distributor/withdraw', {
    method: 'POST', token: tokenA, body: { amount: 100, method: 0 },
  })
  ok(wRes.status === 200 && wRes.json.code !== 200, '提现被拒：业务 400（HTTP 200 + code!=200）',
    `code=${wRes.json.code} msg=${wRes.json.msg}`)
  ok(/余额不足|未启用|非法/.test(String(wRes.json.msg || '')), '文案命中预期', wRes.json.msg)
}

// ───────────── 6. 后台账账 ─────────────
step('6. 后台账账断言')
const commPageRes = await req('/mall_commission/page', {
  method: 'POST', token: adminToken,
  body: { pageNum: 1, pageSize: 20, distributorId: Number(distributorId) },
})
const commPage = (commPageRes.json.data && commPageRes.json.data.records) || []
ok(commPageRes.json.code === 200 && Array.isArray(commPage), '后台 POST /mall_commission/page 可查')
if (records && records.length) {
  ok(commPage.some(r => String(r.distributorId) === distributorId), '后台佣金台账可见 A 的记录',
    `${commPage.length} 条`)
} else {
  console.log('  WARN A 无佣金明细记录，台账存在性断言跳过（结构断言已通过）')
}

const wdPageRes = await req('/mall_withdrawal/page', {
  method: 'POST', token: adminToken,
  body: { pageNum: 1, pageSize: 50 },
})
const wdRecords = (wdPageRes.json.data && wdPageRes.json.data.records) || []
ok(wdPageRes.json.code === 200 && Array.isArray(wdRecords), '后台 POST /mall_withdrawal/page 可查')
if (withdrawReal) {
  ok(wdRecords.some(r => String(r.distributorId) === distributorId), '后台提现台账可见 A 的申请',
    `${wdRecords.length} 条`)
} else {
  console.log('  WARN 未发生真实提现（无可提现余额），提现台账可见性断言不适用')
}

// ───────────── 汇总 ─────────────
console.log(`\n======== 结果：${pass} 通过 / ${fail} 失败 ========`)
if (fail > 0) {
  console.log('失败项：\n  - ' + fails.join('\n  - '))
  process.exit(1)
}
if (!withdrawReal) {
  // 明确退出码：链路断言全过，但存在业务前置缺失（无可提现佣金），不静默当全绿
  console.log('NOTE 缺前置数据：无可提现佣金，真实提现与提现台账断言未执行（退出码 3）')
  process.exit(3)
}
process.exit(0)
