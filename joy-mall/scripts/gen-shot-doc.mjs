import fs from 'node:fs'
import path from 'node:path'

const ROOT = 'D:/workspace/project'
const IMG_DIR = path.join(ROOT, 'docs/系统功能手册/images')
const OUT = path.join(ROOT, 'docs/悦集商城-Joy-Collection-Mall-项目截图.html')

const SECTIONS = [
  {
    no: '01', key: 'admin', title: '运营后台', en: 'Platform Console',
    desc: '平台侧全量运营能力：商品与类目、交易与售后、营销与会员、商家与资金、履约与支付、客服与触达。',
    groups: [
      { name: '登录与经营看板', items: [
        ['admin-01-login', '运营后台登录', '账号 + 密码 + 算术验证码，独立于商城前台的登录入口'],
        ['admin-02-dashboard', '经营看板', 'GMV、已支付订单、客单价、退款金额、待发货、待审核售后、新增会员、售后率与 Top5 商品'],
      ]},
      { name: '商品与内容', items: [
        ['admin-03-goods', '商品管理', '商品编码、类目、品牌、名称、副标题、主图与所属店铺'],
        ['admin-04-sku', '商品 SKU', '按商品名归属，独立维护价格与库存'],
        ['admin-05-category', '商品类目', '树形结构维护一级 / 二级类目'],
        ['admin-12-banner', '轮播图管理', '图片、跳转目标、排序与启用状态'],
      ]},
      { name: '交易与售后', items: [
        ['admin-06-order', '订单管理', '订单号、会员、商品总额、运费、优惠金额与操作列'],
        ['admin-07-order-log', '订单流转日志', '事件、操作方、时间，全链路可追溯'],
        ['admin-08-refund', '售后退款单', '售后单号、订单号、会员、类型（退款 / 退货 / 换货）、退款金额与状态'],
      ]},
      { name: '营销与会员', items: [
        ['admin-09-coupon', '优惠券模板', '面额、使用门槛、有效期与发放量'],
        ['admin-22-promotion', '促销活动', '「全场满 100 减 20」满减活动的规则、时间窗与优先级'],
        ['admin-23-member-level', '会员等级', '普通 / 银卡 / 金卡 / 黑卡四档：成长值门槛、折扣率、包邮次数与权益清单'],
        ['admin-24-seckill', '秒杀活动', '秒杀价、库存、限购与场次时间'],
        ['admin-25-group', '拼团活动', '团价、成团人数与成团时限'],
        ['admin-26-lottery', '抽奖活动', '活动配置与奖品池（积分 / 券 / 实物 / 谢谢参与）'],
        ['admin-27-distribution', '分销管理', '分销员台账（邀请码、团队、累计佣金）与佣金规则入口'],
      ]},
      { name: '商家与资金', items: [
        ['admin-14-merchant', '商家入驻', '店铺、执照主体、联系人、抽成费率与可提现余额'],
        ['admin-15-settlement', '商家结算', '结算单：期间、订单数、商家应收、平台抽成、退款冲减'],
        ['admin-16-withdrawal', '商家提现', '提现单：金额、方式、状态、审核人与拒绝原因'],
      ]},
      { name: '履约与支付', items: [
        ['admin-17-freight', '运费模板', '按件 / 按重量 / 满额包邮三种计价与地区规则'],
        ['admin-18-delivery', '发货工作台', '发货弹窗按订单号取待发明细，逐行填写本次发货数量'],
        ['admin-19-invoice', '发票管理', '开票队列（申请单号、抬头、税号、金额、状态）与红冲操作'],
        ['admin-20-payment', '支付流水', '支付单：渠道、金额、状态与渠道交易号'],
        ['admin-21-reconcile', '支付对账', '差异台账：账单日、渠道、渠道金额、本地金额与处置状态'],
      ]},
      { name: '会员与内容运营', items: [
        ['admin-10-member', '会员管理', '账号、昵称、手机号与积分'],
        ['admin-11-review', '评价管理', '评分星级、评价内容、晒图与商家回复'],
        ['admin-13-notice', '站内消息', '消息台账与触达对象'],
      ]},
      { name: '客服与触达', items: [
        ['admin-28-ticket', '客服工单', '工单号、标题、分类、关联订单与状态（待处理 / 处理中 / 已解决）'],
        ['admin-29-message', '消息触达', '触达记录（事件、渠道、接收人、发送状态）与失败重试'],
      ]},
    ],
  },
  {
    no: '02', key: 'merchant', title: '商家中心', en: 'Merchant Center',
    desc: '商家自有后台，独立账号体系。覆盖自助发品、订单发货、售后审核、结算提现与对账导出。',
    groups: [
      { name: '登录与概览', items: [
        ['merchant-01-login', '商家中心登录', '独立于平台后台的商家账号体系，登录态与平台端隔离'],
        ['merchant-02-overview', '经营概览', '统计卡 + 待办事项 + 资金概况（可提现、待结算、本月佣金）'],
      ]},
      { name: '经营作业', items: [
        ['merchant-03-goods', '商品管理', '本店商品列表与「发布商品」入口'],
        ['merchant-04-order', '订单与发货', '本店订单列表 + 发货操作'],
        ['merchant-05-refund', '售后审核', '本店售后单（待审核 / 处理中 / 已退款）'],
        ['merchant-06-fund', '结算与提现', '结算单列表 + 导出对账单 CSV + 申请提现'],
      ]},
    ],
  },
  {
    no: '03', key: 'pc', title: 'PC 商城', en: 'Desktop Storefront',
    desc: '桌面端购物主链路：逛、买、下单、支付、订单售后、会员权益与营销活动，另含跨店拆单场景。',
    groups: [
      { name: '逛与买', items: [
        ['pc-01-home', '首页', '顶部搜索、类目导航、轮播与商品推荐'],
        ['pc-02-list', '商品列表', '类目筛选 + 商品卡片（价格、销量、库存）'],
        ['pc-03-detail', '商品详情', '「悦集 X7 Pro 旗舰手机」三档规格，价格 ¥3999–4999'],
        ['pc-04-cart', '购物车', '全选、数量增减、金额小计与去结算'],
      ]},
      { name: '下单与支付', items: [
        ['pc-19-confirm', '确认订单（单店）', '满减 −¥20.00、促销优惠合计、已满 ¥99 免运费、应付 ¥3999'],
        ['pc-14-confirm', '确认订单（跨店）', '2 个店铺分组、拆单提示，优惠券跨店铺不可用'],
      ]},
      { name: '订单与售后', items: [
        ['pc-05-orders', '我的订单', '状态 Tab + 订单卡片 + 售后角标'],
        ['pc-06-order-detail', '订单详情', '订单信息、金额构成、状态时间线与售后入口'],
        ['pc-08-refund', '我的售后', '按状态筛选，展示售后单号、类型、金额与状态'],
        ['pc-09-refund-detail', '售后详情', '状态、金额、审核意见与事件时间线'],
      ]},
      { name: '会员权益与工具', items: [
        ['pc-07-coupon', '领券中心', '券面额、使用门槛与领取按钮'],
        ['pc-10-point', '我的积分', '余额卡片 + 流水明细（消费返 / 签到 / 抵扣 / 退款扣回）'],
        ['pc-11-notice', '消息中心', '系统通知、订单消息与售后进展'],
        ['pc-12-address', '收货地址管理', '地址簿增删改与默认地址设置'],
      ]},
      { name: '店铺与营销活动', items: [
        ['pc-13-shop', '店铺主页', '店铺头（编号、经营类目、在售数）+ 店内商品列表'],
        ['pc-15-seckill', '秒杀专场', '场次分组、秒杀价、倒计时与抢购按钮'],
        ['pc-16-group', '拼团专区', '团价、成团人数与开团入口'],
        ['pc-17-lottery', '幸运抽奖', '奖池展示与抽奖按钮'],
        ['pc-18-distribution', '分销中心', '分销状态、邀请码、佣金三栏与我的团队'],
      ]},
    ],
  },
  {
    no: '04', key: 'h5', title: '移动端 H5', en: 'Mobile Web',
    desc: '390×844 视口下的移动端体验：底部四 Tab 导航，覆盖下单、售后、签到积分、营销活动与评价追评。',
    groups: [
      { name: '逛与买', items: [
        ['h5-01-home', '首页', '底部四 Tab 导航（首页 / 分类 / 购物车 / 我的）'],
        ['h5-02-category', '分类页', '左侧一级类目、右侧二级类目'],
        ['h5-03-detail', '商品详情', '规格选择、促销信息与评价区（长图）'],
        ['h5-04-cart', '购物车', '卡片式商品 + 底部结算条'],
        ['h5-13-shop', '店铺主页', '店铺头 + 双列商品流，下拉分页加载'],
      ]},
      { name: '下单', items: [
        ['h5-14-confirm', '确认订单（跨店）', '商品按店铺分组展示，拆单提示'],
        ['h5-22-confirm', '确认订单（单店）', '「满减 全场满 100 减 20 −¥20.00」试算明细 + 促销后小计'],
      ]},
      { name: '订单与售后', items: [
        ['h5-06-orders', '订单列表', '顶部状态筛选，卡片式展示'],
        ['h5-07-refund', '我的售后', '与 PC 端售后数据同源'],
      ]},
      { name: '会员与工具', items: [
        ['h5-05-user', '个人中心', '昵称、手机号、积分与全部功能入口'],
        ['h5-08-signin', '每日签到', '连续天数与当日可得积分'],
        ['h5-09-point', '我的积分', '余额与流水分页'],
        ['h5-10-coupon', '领券中心', '可领券列表与使用门槛'],
        ['h5-11-notice', '消息中心', '系统通知与订单消息'],
        ['h5-12-favorite', '我的收藏', '收藏商品双列流'],
      ]},
      { name: '营销活动与评价', items: [
        ['h5-17-seckill', '秒杀页', '进行中 4 场 + 即将开始 1 场（V15 接真实数据）'],
        ['h5-18-group', '拼团页', '2 人团 / 3 人团卡片，右上角「我的拼团」入口'],
        ['h5-19-lottery', '抽奖页', '奖池（积分 / 券 / 实物）与每日限次'],
        ['h5-20-distribution', '分销中心', '邀请码、佣金汇总、团队 / 明细 / 提现入口'],
        ['h5-21-review-my', '我的评价', '评价 + 商家回复 + 追评条带，底部「追评」入口（长图）'],
      ]},
    ],
  },
]

function dims(file) {
  const b = fs.readFileSync(path.join(IMG_DIR, file + '.png'))
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) }
}

const LONG = new Set(['h5-03-detail', 'h5-21-review-my'])

let seq = 0
const totalPages = SECTIONS.reduce((n, s) => n + s.groups.reduce((m, g) => m + g.items.length, 0), 0)
const totalFiles = fs.readdirSync(IMG_DIR).filter(f => f.endsWith('.png')).length

// ---------- 概览表 ----------
const overview = SECTIONS.map(s => {
  const n = s.groups.reduce((m, g) => m + g.items.length, 0)
  return `        <tr>
          <td class="ov-no">${s.no}</td>
          <td class="ov-name">${s.title}<span>${s.en}</span></td>
          <td class="ov-num">${n}</td>
          <td class="ov-scope">${s.groups.map(g => g.name).join(' · ')}</td>
        </tr>`
}).join('\n')

// ---------- 正文 ----------
const body = SECTIONS.map(s => {
  const n = s.groups.reduce((m, g) => m + g.items.length, 0)
  const groups = s.groups.map((g, gi) => {
    const cards = g.items.map(([file, title, desc]) => {
      seq += 1
      const { w, h } = dims(file)
      const no = String(seq).padStart(2, '0')
      const cls = s.key === 'h5' ? 'shot is-mobile' : 'shot'
      const long = LONG.has(file) ? '<span class="badge-long">长图 · 点击查看完整</span>' : ''
      const num = `<span class="fig-no">图 ${no}</span>`
      return `        <figure class="card" data-src="系统功能手册/images/${file}.png" data-no="${no}" data-title="${title}" data-desc="${desc}">
          <div class="${cls}">
            <img src="系统功能手册/images/${file}.png" alt="${title}" width="${w}" height="${h}" loading="lazy">
            ${long}
            <span class="zoom-hint">点击放大</span>
          </div>
          <figcaption>
            ${num}<strong>${title}</strong>
            <p>${desc}</p>
          </figcaption>
        </figure>`
    }).join('\n')
    return `      <div class="group">
        <div class="group-head">
          <span class="group-idx">${s.no}.${gi + 1}</span>
          <h3>${g.name}</h3>
          <span class="group-rule"></span>
          <span class="group-count">${g.items.length} 张</span>
        </div>
        <div class="grid grid-${s.key}">
${cards}
        </div>
      </div>`
  }).join('\n\n')

  return `  <section class="sec" id="${s.key}">
    <div class="sec-head">
      <div class="sec-no">${s.no}</div>
      <div class="sec-meta">
        <div class="sec-eyebrow">${s.en}</div>
        <h2>${s.title}</h2>
        <p>${s.desc}</p>
        <div class="sec-tags"><span class="tag">${n} 张截图</span><span class="tag">${s.groups.length} 个模块分组</span></div>
      </div>
    </div>
${groups}
  </section>`
}).join('\n\n')

const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>悦集商城 Joy Collection Mall · 项目界面截图集</title>
<style>
  :root{
    --brand:#e1251b; --brand-dark:#b81a13; --brand-soft:#fdeceb;
    --ink:#16181d; --ink-2:#3f4550; --muted:#7b8290; --muted-2:#a2a8b4;
    --line:#e7e8ec; --line-2:#f0f1f4; --bg:#f5f6f8; --card:#fff;
    --mono:"SFMono-Regular",Consolas,"Liberation Mono",Menlo,monospace;
    --sans:"PingFang SC","Hiragino Sans GB","Microsoft YaHei",-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
  }
  *{box-sizing:border-box}
  html{scroll-behavior:smooth}
  body{margin:0;background:var(--bg);color:var(--ink);font-family:var(--sans);
       -webkit-font-smoothing:antialiased;line-height:1.7}

  /* ---------- 封面 ---------- */
  .cover{position:relative;min-height:100vh;display:flex;flex-direction:column;justify-content:center;
         padding:96px 48px 72px;overflow:hidden;background:#fff;border-bottom:1px solid var(--line)}
  .cover::before{content:"";position:absolute;inset:0;
    background-image:linear-gradient(var(--line-2) 1px,transparent 1px),linear-gradient(90deg,var(--line-2) 1px,transparent 1px);
    background-size:56px 56px;mask-image:radial-gradient(circle at 30% 30%,#000 0%,transparent 72%)}
  .cover::after{content:"";position:absolute;width:760px;height:760px;right:-260px;top:-260px;border-radius:50%;
    background:radial-gradient(circle,rgba(225,37,27,.13),rgba(225,37,27,0) 68%)}
  .cover-inner{position:relative;max-width:1180px;margin:0 auto;width:100%}
  .brand-line{display:flex;align-items:center;gap:14px;margin-bottom:44px}
  .brand-mark{width:46px;height:46px;border-radius:11px;background:var(--brand);color:#fff;
    display:grid;place-items:center;font-weight:800;font-size:22px;letter-spacing:0;
    box-shadow:0 8px 24px rgba(225,37,27,.28)}
  .brand-text{font-weight:700;letter-spacing:.14em;font-size:13px;color:var(--brand)}
  .brand-text span{display:block;color:var(--muted-2);letter-spacing:.22em;font-size:10px;margin-top:2px}
  h1{font-size:clamp(52px,8vw,92px);line-height:1.02;margin:0 0 12px;letter-spacing:-.02em;font-weight:800}
  .cover-en{font-size:clamp(20px,2.6vw,32px);font-weight:300;letter-spacing:.12em;color:var(--brand);
            margin:0 0 30px;text-transform:none}
  .cover-rule{width:72px;height:4px;background:var(--brand);border-radius:2px;margin:0 0 30px}
  .cover-sub{font-size:17px;color:var(--ink-2);max-width:640px;margin:0 0 56px}
  .cover-sub em{font-style:normal;color:var(--ink);font-weight:600}
  .cover-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:0;border-top:1px solid var(--line);
               border-bottom:1px solid var(--line);max-width:860px}
  .cover-stats div{padding:22px 26px;border-right:1px solid var(--line)}
  .cover-stats div:last-child{border-right:none}
  .cover-stats b{display:block;font-size:34px;font-weight:800;letter-spacing:-.01em;line-height:1.1}
  .cover-stats b i{font-style:normal;font-size:15px;font-weight:600;color:var(--muted);margin-left:4px}
  .cover-stats small{display:block;font-size:11px;letter-spacing:.18em;color:var(--muted-2);margin-top:8px;text-transform:uppercase}
  .cover-foot{position:relative;max-width:1180px;margin:64px auto 0;width:100%;
    display:flex;justify-content:space-between;align-items:flex-end;gap:24px;font-size:12px;color:var(--muted)}
  .cover-foot .hash{font-family:var(--mono);color:var(--muted-2)}
  .scroll-hint{display:flex;align-items:center;gap:8px;color:var(--brand);font-size:12px;letter-spacing:.1em}
  .scroll-hint::after{content:"";width:1px;height:26px;background:linear-gradient(var(--brand),transparent);animation:drop 1.8s infinite}
  @keyframes drop{0%{opacity:0;transform:translateY(-6px)}50%{opacity:1}100%{opacity:0;transform:translateY(6px)}}

  /* ---------- 顶栏 ---------- */
  .topbar{position:sticky;top:0;z-index:50;background:rgba(255,255,255,.9);backdrop-filter:blur(14px);
    border-bottom:1px solid var(--line)}
  .topbar-in{max-width:1180px;margin:0 auto;padding:0 48px;height:58px;display:flex;align-items:center;gap:28px}
  .tb-brand{display:flex;align-items:center;gap:10px;font-weight:700;font-size:14px;letter-spacing:.01em;white-space:nowrap}
  .tb-brand .dot{width:22px;height:22px;border-radius:6px;background:var(--brand);color:#fff;display:grid;
    place-items:center;font-size:12px;font-weight:800}
  .tb-brand small{color:var(--muted-2);font-weight:400;font-size:11px;letter-spacing:.14em;margin-left:2px}
  .tb-nav{display:flex;gap:26px;margin-left:auto;font-size:13px}
  .tb-nav a{color:var(--ink-2);text-decoration:none;padding:4px 0;border-bottom:2px solid transparent;transition:.18s}
  .tb-nav a:hover{color:var(--brand);border-color:var(--brand)}
  .tb-nav a i{font-style:normal;color:var(--muted-2);font-family:var(--mono);font-size:11px;margin-right:5px}
  .tb-count{font-family:var(--mono);font-size:11px;color:var(--muted);border-left:1px solid var(--line);
    padding-left:22px;white-space:nowrap}

  /* ---------- 概览 ---------- */
  .wrap{max-width:1180px;margin:0 auto;padding:0 48px}
  .intro{padding:76px 0 20px}
  .intro h2{font-size:13px;letter-spacing:.24em;color:var(--brand);margin:0 0 18px;text-transform:uppercase;font-weight:700}
  .intro h3{font-size:30px;line-height:1.4;margin:0 0 16px;font-weight:800;letter-spacing:-.01em}
  .intro p{color:var(--ink-2);font-size:15px;max-width:760px;margin:0 0 40px}
  .intro p b{color:var(--ink)}
  table.ov{width:100%;border-collapse:collapse;background:#fff;border:1px solid var(--line);border-radius:12px;overflow:hidden;font-size:13.5px}
  table.ov th{text-align:left;font-size:11px;letter-spacing:.16em;color:var(--muted-2);font-weight:600;
    padding:14px 20px;background:#fafbfc;border-bottom:1px solid var(--line);text-transform:uppercase}
  table.ov td{padding:18px 20px;border-bottom:1px solid var(--line-2);vertical-align:middle}
  table.ov tr:last-child td{border-bottom:none}
  .ov-no{font-family:var(--mono);color:var(--brand);font-weight:700;width:52px}
  .ov-name{font-weight:700;width:190px}
  .ov-name span{display:block;font-weight:400;font-size:11px;color:var(--muted-2);letter-spacing:.1em;margin-top:2px}
  .ov-num{font-family:var(--mono);font-weight:700;width:80px}
  .ov-num::after{content:" 张";font-family:var(--sans);font-weight:400;font-size:11px;color:var(--muted-2)}
  .ov-scope{color:var(--muted);font-size:12.5px;line-height:1.9}
  .hint{margin:26px 0 0;padding:16px 20px;background:#fff;border:1px solid var(--line);border-left:3px solid var(--brand);
    border-radius:0 8px 8px 0;font-size:13px;color:var(--ink-2)}
  .hint b{color:var(--ink)}
  kbd{font-family:var(--mono);font-size:11px;background:#f2f3f5;border:1px solid var(--line);border-bottom-width:2px;
    border-radius:4px;padding:1px 6px;color:var(--ink-2)}

  /* ---------- 章节 ---------- */
  .sec{padding:88px 0 24px;border-top:1px solid var(--line);margin-top:64px}
  .sec:first-of-type{margin-top:48px}
  .sec-head{display:flex;gap:36px;align-items:flex-start;margin-bottom:56px}
  .sec-no{font-family:var(--mono);font-size:84px;line-height:.9;font-weight:700;color:transparent;
    -webkit-text-stroke:1.5px var(--brand);opacity:.5;min-width:110px;padding-top:6px}
  .sec-eyebrow{font-size:11px;letter-spacing:.24em;color:var(--brand);text-transform:uppercase;margin-bottom:10px;font-weight:600}
  .sec-meta h2{font-size:40px;margin:0 0 14px;letter-spacing:-.02em;font-weight:800;line-height:1.1}
  .sec-meta p{color:var(--ink-2);font-size:14.5px;margin:0 0 18px;max-width:720px}
  .sec-tags{display:flex;gap:8px;flex-wrap:wrap}
  .tag{font-size:11.5px;padding:4px 11px;border-radius:999px;background:#fff;border:1px solid var(--line);color:var(--muted)}
  .tag:first-child{background:var(--brand-soft);border-color:#f8cdca;color:var(--brand-dark);font-weight:600}

  /* ---------- 分组 ---------- */
  .group{margin-bottom:64px}
  .group-head{display:flex;align-items:center;gap:14px;margin-bottom:22px}
  .group-idx{font-family:var(--mono);font-size:11px;font-weight:700;color:#fff;background:var(--ink);
    padding:3px 8px;border-radius:5px;letter-spacing:.04em}
  .group-head h3{font-size:19px;margin:0;font-weight:700;letter-spacing:-.01em}
  .group-rule{flex:1;height:1px;background:var(--line)}
  .group-count{font-family:var(--mono);font-size:11px;color:var(--muted-2)}

  .grid{display:grid;gap:26px}
  .grid-admin,.grid-pc,.grid-merchant{grid-template-columns:repeat(2,1fr)}
  .grid-h5{grid-template-columns:repeat(4,1fr);gap:20px}

  /* ---------- 卡片 ---------- */
  .card{margin:0;background:#fff;border:1px solid var(--line);border-radius:12px;overflow:hidden;
    cursor:zoom-in;transition:transform .2s ease,box-shadow .2s ease,border-color .2s ease}
  .card:hover{transform:translateY(-2px);box-shadow:0 14px 34px rgba(22,24,29,.09);border-color:#dcdee4}
  .shot{position:relative;background:#f0f1f4;border-bottom:1px solid var(--line-2);overflow:hidden;line-height:0}
  .shot img{width:100%;height:auto;display:block}
  .shot.is-mobile{background:#e9eaee;display:flex;justify-content:center;padding:14px 0 0}
  .shot.is-mobile img{width:86%;border-radius:14px 14px 0 0;box-shadow:0 6px 18px rgba(0,0,0,.1);
    max-height:560px;object-fit:cover;object-position:top center}
  .shot.is-mobile::before{content:"";position:absolute;top:6px;left:50%;transform:translateX(-50%);
    width:52px;height:4px;border-radius:2px;background:rgba(0,0,0,.14);z-index:2}
  .zoom-hint{position:absolute;right:10px;bottom:10px;font-size:10.5px;letter-spacing:.08em;color:#fff;
    background:rgba(22,24,29,.62);backdrop-filter:blur(4px);padding:3px 9px;border-radius:999px;
    opacity:0;transform:translateY(4px);transition:.2s}
  .card:hover .zoom-hint{opacity:1;transform:none}
  .badge-long{position:absolute;left:10px;top:10px;font-size:10.5px;letter-spacing:.06em;color:#fff;
    background:var(--brand);padding:3px 9px;border-radius:999px;z-index:2}
  figcaption{padding:15px 17px 17px;background:#fff}
  .fig-no{display:inline-block;font-family:var(--mono);font-size:10.5px;font-weight:700;color:var(--brand);
    background:var(--brand-soft);padding:1px 7px;border-radius:4px;margin-right:9px;vertical-align:1px}
  figcaption strong{font-size:14.5px;font-weight:700;letter-spacing:-.01em}
  figcaption p{margin:7px 0 0;font-size:12.5px;line-height:1.65;color:var(--muted)}
  .grid-h5 figcaption{padding:13px 14px 15px}
  .grid-h5 figcaption strong{font-size:13.5px}
  .grid-h5 figcaption p{font-size:11.5px;margin-top:6px;
    display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}

  /* ---------- 附录 ---------- */
  .appendix{margin-top:80px;border-top:1px solid var(--line);padding:64px 0 96px}
  .appendix h2{font-size:13px;letter-spacing:.24em;color:var(--brand);margin:0 0 26px;font-weight:700}
  .meta-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:1px;background:var(--line);
    border:1px solid var(--line);border-radius:12px;overflow:hidden}
  .meta-grid div{background:#fff;padding:20px 22px}
  .meta-grid dt{font-size:11px;letter-spacing:.14em;color:var(--muted-2);margin-bottom:6px}
  .meta-grid dd{margin:0;font-size:13.5px;color:var(--ink);font-weight:600}
  .meta-grid dd code{font-family:var(--mono);font-size:12px;font-weight:400;color:var(--ink-2)}
  .doc-foot{margin-top:36px;display:flex;justify-content:space-between;gap:20px;font-size:12px;color:var(--muted-2);
    flex-wrap:wrap}
  .doc-foot a{color:var(--brand);text-decoration:none;border-bottom:1px solid #f3c3c0}

  /* ---------- 灯箱 ---------- */
  .lb{position:fixed;inset:0;z-index:99;background:rgba(12,13,16,.93);display:none;
    flex-direction:column;opacity:0;transition:opacity .2s}
  .lb.on{display:flex;opacity:1}
  .lb-bar{display:flex;align-items:center;justify-content:space-between;padding:16px 24px;color:#fff;gap:20px;
    border-bottom:1px solid rgba(255,255,255,.1)}
  .lb-title{font-size:14px;font-weight:600}
  .lb-title span{font-family:var(--mono);color:var(--brand);margin-right:10px}
  .lb-ctrl{display:flex;align-items:center;gap:10px}
  .lb-ctrl button{background:rgba(255,255,255,.1);color:#fff;border:none;border-radius:7px;
    padding:6px 13px;font-size:12px;cursor:pointer;font-family:var(--sans);transition:.15s}
  .lb-ctrl button:hover{background:var(--brand)}
  .lb-idx{font-family:var(--mono);font-size:12px;color:rgba(255,255,255,.55);margin-right:6px}
  .lb-stage{flex:1;display:flex;align-items:center;justify-content:center;padding:26px;overflow:auto}
  .lb-stage img{max-width:100%;max-height:100%;border-radius:8px;box-shadow:0 24px 60px rgba(0,0,0,.5)}
  .lb-desc{padding:0 24px 22px;color:rgba(255,255,255,.62);font-size:12.5px;text-align:center}

  @media(max-width:1024px){
    .grid-admin,.grid-pc,.grid-merchant{grid-template-columns:1fr}
    .grid-h5{grid-template-columns:repeat(3,1fr)}
    .sec-head{gap:20px}.sec-no{font-size:56px;min-width:74px}
    .sec-meta h2{font-size:30px}
    .topbar-in,.wrap{padding:0 26px}
    .cover{padding:72px 26px 56px}
    .cover-stats{grid-template-columns:repeat(2,1fr)}
    .cover-stats div:nth-child(2){border-right:none}
    .meta-grid{grid-template-columns:1fr 1fr}
    .tb-count{display:none}
  }
  @media(max-width:640px){
    .grid-h5{grid-template-columns:repeat(2,1fr);gap:12px}
    .tb-nav{display:none}
    h1{font-size:44px}
    .cover-stats b{font-size:26px}
    .ov-scope{display:none}
    .meta-grid{grid-template-columns:1fr}
  }
  @media print{
    body{background:#fff}
    .topbar,.lb,.scroll-hint,.zoom-hint{display:none!important}
    .cover{min-height:auto;page-break-after:always}
    .sec{page-break-before:always;margin-top:0;padding-top:24px}
    .card{break-inside:avoid;box-shadow:none}
    .shot.is-mobile img{max-height:none;object-fit:contain}
  }
</style>
</head>
<body>

<header class="cover">
  <div class="cover-inner">
    <div class="brand-line">
      <div class="brand-mark">悦</div>
      <div class="brand-text">悦集商城<span>JOY COLLECTION MALL</span></div>
    </div>
    <h1>悦集商城</h1>
    <p class="cover-en">Joy&nbsp;&nbsp;Collection&nbsp;&nbsp;Mall</p>
    <div class="cover-rule"></div>
    <p class="cover-sub">项目界面截图集 —— 从 <em>运营后台</em>、<em>商家中心</em> 到 <em>PC 商城</em> 与 <em>移动端 H5</em>，
      四端 ${totalPages} 张实拍截图，完整覆盖商品、交易、营销、履约、售后与客服全链路。</p>
    <div class="cover-stats">
      <div><b>${totalPages}<i>张</i></b><small>实拍截图</small></div>
      <div><b>4<i>端</i></b><small>终端覆盖</small></div>
      <div><b>9<i>个</i></b><small>微服务</small></div>
      <div><b>V15</b><small>当前版本</small></div>
    </div>
  </div>
  <div class="cover-foot">
    <div>采集环境：本机全栈实跑 · MySQL 8 + Spring Cloud + Vue<br>
      采集日期：2026-11-02 · 截图设备：Chromium（PC 1440×900 / H5 390×844 @2x）</div>
    <div class="scroll-hint">向下浏览</div>
  </div>
</header>

<nav class="topbar">
  <div class="topbar-in">
    <div class="tb-brand"><span class="dot">悦</span>悦集商城<small>Joy Collection Mall</small></div>
    <div class="tb-nav">
      ${SECTIONS.map(s => `<a href="#${s.key}"><i>${s.no}</i>${s.title}</a>`).join('\n      ')}
    </div>
    <div class="tb-count">${totalPages} / ${totalFiles} 张</div>
  </div>
</nav>

<main class="wrap">
  <section class="intro">
    <h2>Catalog Overview</h2>
    <h3>一份可直接对外展示的界面档案</h3>
    <p>全部截图来自 <b>本机运行中的完整系统</b>（10 个后端服务 + 3 个前端工程），非设计稿、非拼图。
      数据为演示库真实数据：商品、订单、售后、工单、秒杀场次均有真实状态分布，
      因此页面上的金额、状态标签与时间线都可作为功能佐证。</p>
    <table class="ov">
      <thead><tr><th>编号</th><th>终端</th><th>截图</th><th>覆盖模块</th></tr></thead>
      <tbody>
${overview}
      </tbody>
    </table>
    <div class="hint"><b>查看方式：</b>点击任意截图进入灯箱查看原图，<kbd>←</kbd> <kbd>→</kbd> 切换、
      <kbd>Esc</kbd> 关闭；移动端长图（商品详情、我的评价）在卡片中被裁切，灯箱内可看完整版。</div>
  </section>

${body}

  <section class="appendix">
    <h2>Document Info</h2>
    <dl class="meta-grid">
      <div><dt>文档名称</dt><dd>悦集商城 Joy Collection Mall · 项目界面截图集</dd></div>
      <div><dt>对应版本</dt><dd>V15（含 V8/V9 三端页面、V10/V14 商家中心）</dd></div>
      <div><dt>截图数量</dt><dd>${totalPages} 张（后台 29 / 商家 6 / PC 19 / H5 20）</dd></div>
      <div><dt>采集脚本</dt><dd><code>scripts/capture-manual-shots.mjs</code></dd></div>
      <div><dt>技术栈</dt><dd>Spring Cloud · MyBatis-Plus · MySQL 8 · Vue 2/3 · Element UI · Vant</dd></div>
      <div><dt>关联文档</dt><dd>功能操作手册（逐页操作说明与状态机）</dd></div>
    </dl>
    <div class="doc-foot">
      <div>图 01–${totalPages} 与《悦集商城-功能操作手册》同源，编号按本文件独立排列。</div>
      <div><a href="系统功能手册/悦集商城-功能操作手册.html">→ 打开功能操作手册</a></div>
    </div>
  </section>
</main>

<div class="lb" id="lb">
  <div class="lb-bar">
    <div class="lb-title"><span id="lbNo">图 01</span><span id="lbTitle" style="color:#fff;font-family:var(--sans)"></span></div>
    <div class="lb-ctrl">
      <span class="lb-idx" id="lbIdx">1 / ${totalPages}</span>
      <button id="lbPrev">← 上一张</button>
      <button id="lbNext">下一张 →</button>
      <button id="lbClose">关闭 ✕</button>
    </div>
  </div>
  <div class="lb-stage"><img id="lbImg" alt=""></div>
  <div class="lb-desc" id="lbDesc"></div>
</div>

<script>
(function(){
  var cards=[].slice.call(document.querySelectorAll('.card'));
  var lb=document.getElementById('lb'),img=document.getElementById('lbImg');
  var t=document.getElementById('lbTitle'),d=document.getElementById('lbDesc');
  var n=document.getElementById('lbNo'),ix=document.getElementById('lbIdx');
  var cur=0;
  function show(i){
    if(i<0)i=cards.length-1; if(i>=cards.length)i=0; cur=i;
    var c=cards[i];
    img.src=c.dataset.src; img.alt=c.dataset.title;
    n.textContent='图 '+c.dataset.no; t.textContent=c.dataset.title; d.textContent=c.dataset.desc;
    ix.textContent=(i+1)+' / '+cards.length;
    lb.classList.add('on'); document.body.style.overflow='hidden';
    img.style.maxHeight=(window.innerHeight-170)+'px';
  }
  function hide(){ lb.classList.remove('on'); document.body.style.overflow=''; }
  cards.forEach(function(c,i){ c.addEventListener('click',function(){ show(i); }); });
  document.getElementById('lbClose').onclick=hide;
  document.getElementById('lbPrev').onclick=function(e){ e.stopPropagation(); show(cur-1); };
  document.getElementById('lbNext').onclick=function(e){ e.stopPropagation(); show(cur+1); };
  lb.addEventListener('click',function(e){ if(e.target===lb||e.target.className==='lb-stage') hide(); });
  document.addEventListener('keydown',function(e){
    if(!lb.classList.contains('on'))return;
    if(e.key==='Escape')hide();
    if(e.key==='ArrowLeft')show(cur-1);
    if(e.key==='ArrowRight')show(cur+1);
  });
  window.addEventListener('resize',function(){
    if(lb.classList.contains('on')) img.style.maxHeight=(window.innerHeight-170)+'px';
  });
})();
</script>
</body>
</html>
`

fs.writeFileSync(OUT, html, 'utf8')
console.log('written', OUT, Math.round(Buffer.byteLength(html) / 1024) + 'KB', 'figures', seq, 'total', totalPages)
