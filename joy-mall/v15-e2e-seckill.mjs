// V15C 端到端：秒杀全链路（对共享网关 http://127.0.0.1:8080，服务端为含 V8/V9 的旧代码）
// 流程：后台登录 → 自建一场进行中的秒杀活动（perLimit=1，保证限购可复现）
//   → 会员登录 → 活动列表/详情断言 → 取/建收货地址 → 抢购成功
//   → 用 orderNo 验证订单已建 → 再次抢购断言限购业务 400 → 库存扣减断言
//   → 后台 /mall_seckill/page 断言活动可查
// 契约：id 全字符串、金额分、业务错误 HTTP 200 + body code != 200。
// 退出码：0 全部通过；1 存在断言失败；2 环境前置不满足（如验证码答案未开放）。
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

// 后台登录：验证码答案需环境开放（AUTH_CAPTCHA_EXPOSE_ANSWER=true）
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

const fmt = (d, pad = 0) => {
  const t = new Date(Date.now() + pad)
  const p = n => String(n).padStart(2, '0')
  return `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())} ${p(t.getHours())}:${p(t.getMinutes())}:${p(t.getSeconds())}`
}

// ───────────── 1. 后台登录 + 自建秒杀活动（确定性前置） ─────────────
step('1. 后台登录并创建进行中的秒杀活动（perLimit=1）')
const adminToken = await loginAdmin()
ok(!!adminToken, '后台 admin 登录')

const createRes = await req('/mall_seckill', {
  method: 'POST',
  token: adminToken,
  body: {
    goodsId: 1001, skuId: 2001,
    originPrice: 399900, seckillPrice: 9900,
    totalStock: 10, perLimit: 1, status: 1,
    startTime: fmt(Date.now(), -60_000),   // 1 分钟前开始
    endTime: fmt(Date.now(), 86_400_000),  // 1 天后结束
  },
})
ok(createRes.json.code === 200, '创建秒杀活动', createRes.json.msg)

// 自建活动无返回 id：从后台分页按 goodsId 取最新一条
const pageRes = await req('/mall_seckill/page', {
  method: 'POST', token: adminToken,
  body: { pageNum: 1, pageSize: 10, goodsId: 1001 },
})
const pageRecords = (pageRes.json.data && pageRes.json.data.records) || []
const mine = pageRecords.find(r => r.status === 1 && r.seckillPrice === 9900 && r.totalStock === 10)
ok(!!mine, '后台分页可查到新建活动', mine && mine.id)
if (!mine) {
  console.error('创建的活动在后台分页查不到，无法继续')
  process.exit(1)
}
const activityId = String(mine.id)
ok(typeof activityId === 'string' && activityId.length > 0, '活动 id 为字符串', activityId)

// ───────────── 2. 会员登录 + 活动列表/详情 ─────────────
step('2. 会员登录与秒杀活动列表/详情')
const login = await req('/shop/auth/login', {
  method: 'POST', body: { phone: MEMBER_PHONE, password: MEMBER_PASSWORD },
})
const memberToken = login.json && login.json.data && login.json.data.token
ok(!!memberToken, '会员登录', MEMBER_PHONE)

const listRes = await req('/shop/seckill/activities')
const listData = listRes.json.data || []
ok(listRes.json.code === 200 && Array.isArray(listData), '活动列表返回数组', `${listData.length} 场`)
ok(listData.some(a => String(a.id) === activityId), '列表包含本次新建活动')

const detailRes = await req(`/shop/seckill/${activityId}`)
const detail = detailRes.json.data || {}
ok(detailRes.json.code === 200 && String(detail.id) === activityId, '活动详情可查', detail.goodsName)
ok(String(detail.skuId) === '2001', '详情 skuId 为字符串且正确', String(detail.skuId))
ok(detail.onSale === true, '活动处于进行中')
ok(Number(detail.perLimit) === 1, '限购 1 件')
ok(typeof detail.seckillPrice === 'number' || typeof detail.seckillPrice === 'string', '秒杀价（分）', String(detail.seckillPrice))

// ───────────── 3. 收货地址：取一个，没有就建 ─────────────
step('3. 收货地址')
let addrRes = await req('/shop/address/list', { token: memberToken })
let addresses = (addrRes.json.data || [])
ok(addrRes.json.code === 200 && Array.isArray(addresses), '地址列表可查', `${addresses.length} 条`)
if (!addresses.length) {
  const addRes = await req('/shop/address', {
    method: 'POST', token: memberToken,
    body: {
      receiverName: 'V15C收货员', receiverPhone: '13800000001',
      province: '广东省', city: '深圳市', district: '南山区',
      detail: 'V15C e2e 自动化测试地址',
    },
  })
  ok(addRes.json.code === 200, '无地址时自动新建', addRes.json.msg)
  addrRes = await req('/shop/address/list', { token: memberToken })
  addresses = addrRes.json.data || []
}
const address = addresses[0]
ok(!!(address && address.id), '取得收货地址', address && String(address.id))

// ───────────── 4. 抢购成功 + 订单已建 ─────────────
step('4. 抢购')
const buyRes = await req(`/shop/seckill/${activityId}/buy`, {
  method: 'POST', token: memberToken, body: { addressId: address.id, buyerRemark: 'V15C e2e' },
})
const buyData = buyRes.json.data || {}
ok(buyRes.status === 200 && buyRes.json.code === 200, '抢购成功', buyRes.json.msg)
ok(typeof buyData.orderNo === 'string' && buyData.orderNo.length > 0, '返回 orderNo', buyData.orderNo)
ok(buyData.orderId !== undefined && String(buyData.orderId).length > 0, '返回 orderId（字符串）', String(buyData.orderId))

step('5. 验证订单已建')
const orderRes = await req(`/shop/order/${buyData.orderId}`, { token: memberToken })
const orderBody = orderRes.json.data || {}
ok(orderRes.json.code === 200 && orderBody.order, '订单详情可查（本人订单）')
ok(orderBody.order && String(orderBody.order.orderNo) === String(buyData.orderNo), '订单号与抢购返回一致',
  orderBody.order && orderBody.order.orderNo)
ok(orderBody.order && Number(orderBody.order.orderType) === 1, '订单类型为秒杀单 orderType=1',
  orderBody.order && String(orderBody.order.orderType))
ok(Array.isArray(orderBody.items) && orderBody.items.length === 1, '订单含 1 行商品明细')
ok(orderBody.order && Number(orderBody.order.payAmount) === 9900, '订单金额为秒杀价 9900 分',
  orderBody.order && String(orderBody.order.payAmount))

// ───────────── 6. 再次抢购：限购断言（业务 400） ─────────────
step('6. 再次抢购触发限购')
const buy2Res = await req(`/shop/seckill/${activityId}/buy`, {
  method: 'POST', token: memberToken, body: { addressId: address.id },
})
ok(buy2Res.status === 200, '业务错误以 HTTP 200 返回', `HTTP ${buy2Res.status}`)
ok(buy2Res.json.code !== 200, 'body code != 200', String(buy2Res.json.code))
ok(/限购|重复/.test(String(buy2Res.json.msg || '')), '文案命中限购/防重', buy2Res.json.msg)

// ───────────── 7. 库存扣减断言 ─────────────
step('7. 库存与记录一致性')
const detail2 = (await req(`/shop/seckill/${activityId}`, { token: memberToken })).json.data || {}
ok(Number(detail2.availableStock) === Number(detail.availableStock) - 1, '剩余库存 -1',
  `${detail.availableStock} → ${detail2.availableStock}`)
ok(Number(detail2.myCount) === 1, '我的已购数量为 1', String(detail2.myCount))

// ───────────── 8. 后台账账断言 ─────────────
step('8. 后台秒杀分页断言')
const adminPageRes = await req('/mall_seckill/page', {
  method: 'POST', token: adminToken, body: { pageNum: 1, pageSize: 20, goodsId: 1001 },
})
const adminRecords = (adminPageRes.json.data && adminPageRes.json.data.records) || []
const adminRow = adminRecords.find(r => String(r.id) === activityId)
ok(adminPageRes.json.code === 200 && !!adminRow, '后台分页可查到该活动', adminRow && adminRow.goodsName)
ok(adminRow && adminRow.goodsName, '后台行带商品名', adminRow && adminRow.goodsName)
ok(adminRow && Number(adminRow.availableStock) === Number(detail2.availableStock), '后台剩余库存一致',
  adminRow && String(adminRow.availableStock))

// ───────────── 汇总 ─────────────
console.log(`\n======== 结果：${pass} 通过 / ${fail} 失败 ========`)
if (fail > 0) {
  console.log('失败项：\n  - ' + fails.join('\n  - '))
  process.exit(1)
}
process.exit(0)
