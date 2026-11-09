# 商城（mall）V3 规划——C 端商城 H5（完整购物链路）

> 2026-10-29 起草。与《商城-V2-交易闭环-规划.md》并列，V2 文档继续有效。
> 关键背景：V2 规划的后端交易能力（支付/状态机/库存）至今未实施，后端仍是纯 CRUD；
> 10/28 已起步 C 端只读接口（ShopController：banners/categories/goods）。
> V3 决策：直接做 C 端 H5 商城并含交易，V2 的后端交易核心能力并入本版本一并实现。

---

## 一、现状核对（2026-10-29 实测）

| 项 | 实测 | 结论 |
|---|---|---|
| 后端交易 | MallOrderController 仅通用 CRUD；无状态机/支付/库存逻辑 | V2 未实施，V3 必须连带补齐 |
| C 端接口 | ShopController（10/28 新增）：/shop/banners、/shop/categories、/shop/goods、/shop/goods/{id} | 只读浏览已起步，无购物车/下单/认证 |
| C 端前端 | 不存在（fe 下只有 mall-web 后台端） | 需新建独立工程 |
| 会员认证 | mall_member 表无密码列；TokenStore 只服务后台 sys_user | C 端认证从零建 |
| 购物车 | 无 mall_cart 表 | 需新建表 |
| 订单字段 | mall_order 有 status/pay_status/pay_type/pay_order_no/pay_time/deliver_time/finish_time | 字段齐，mock 支付可不新建 pay 表 |
| 库存 | mall_sku.stock/sales 有列无扣减逻辑 | 下单/支付需补条件更新 |
| 走查遗留 | 0 个测试类；checkRole 空实现（A-05，有意排除）；mall_order/index.vue 891 行 | 本版本顺带为交易核心补最小测试 |

**一句话**：V2 的「字段齐全、流程断裂」未变，10/28 浏览接口只是开了个头——V3 把 C 端从浏览到支付一次走通。

## 二、主题与理由

**主题：C 端商城 H5（浏览 + 注册登录 + 购物车 + 下单 + mock 支付 + 我的订单）**

理由：
1. 商城作为代码生成器平台样板工程，当前只能演示后台 CRUD；C 端完整购物链路是「生成系统能长成真业务」的最强证明
2. C 端浏览接口已起步，顺势推进比重起炉灶成本低
3. V2 后端交易能力（状态机/库存/mock 支付）与 C 端强耦合，拆开做会被前端反复等待，合并进 V3 一次交付

## 三、与 V2 的关系

| V2 项 | V3 处置 |
|---|---|
| F-02 支付打通 / F-03 状态机 / F-04 库存 | **并入 V3 P0 后端**，按本文档口径实现（mock 渠道，不依赖平台支付能力包 scaffold） |
| F-01 开支付能力包重 scaffold | **取消**：V3 在 mall-server 手写交易端点，等平台包成熟再回迁（避免重 scaffold 覆盖手改文件的风险） |
| F-05 订单日志 | 并入 V3：状态机每步写 mall_order_log |
| F-06 退款审核 / F-07 看板 | 后置 V4（见「明确不做」） |
| F-08 优惠券核销 | 进 V3 P1（C 端领券 + 下单用券） |
| 平台 V6.6 前置项（P0-a/b/c） | 与 V3 解耦，平台侧自行排期 |

## 四、范围

### P0：C 端购物链路（必须）

| # | 功能 | 要点 | 验收 |
|---|---|---|---|
| F-01 | 新建 H5 工程 fe/mall-shop | Vue3 + Vite + Vant 4 + Pinia + vue-router；端口 9531；代理 /api → 8431；底部 tab：首页/分类/购物车/我的 | 工程可起，四 tab 可切，首页真数据渲染 |
| F-02 | 浏览页 | 首页（banner 轮播 + 分类入口 + 商品流）、分类页（两级联动）、商品详情（图文 + SKU 选择弹层，价格/库存随 SKU 切换） | 复用现有 /shop/banners、/shop/categories、/shop/goods 接口，缺字段则后端补 |
| F-03 | C 端会员认证 | mall_member 加 `password` 列（BCrypt，遵循 mysql-ddl-standard 改表规范）；/shop/auth/register、/shop/auth/login 发 member token（复用 TokenStore，key 前缀与后台区分）；/shop/** 写接口经 ShopAuthInterceptor 校验 | 注册→登录→带 token 访问 /shop/cart 成功；无 token 401；错误密码 400 |
| F-04 | 购物车 | 新建 mall_cart 表（member_id/sku_id/num，唯一键 member_id+sku_id 带 deleted）；加购/改数量/删除/列表（联表出商品名/图/价/库存）；数量超库存提示 | 加购→列表→改量→删除全通；刷新后数据在（服务端持久化） |
| F-05 | 下单 + 状态机 | POST /shop/order/submit（地址 + 勾选项）：后端校验登录/地址归属/库存，条件 UPDATE 扣库存（WHERE stock>=num 防超卖），生成 mall_order（status=0 待支付）+ order_item + order_log；状态机：0 待支付→1 待发货→2 已发货→3 已完成；0 可取消（4，回补库存） | 下单成功跳收银台；库存不足报明确错误；并发同 SKU 不超卖；非法迁移后端 400 |
| F-06 | mock 支付 | POST /shop/pay/mock（orderNo）：校验归属与 status=0，置 pay_status=已支付/status=1/pay_type=mock/pay_time，写 log；幂等：重复支付返回「已完成」不重复扣库存 | 支付后订单变「待发货」；重放第二次幂等提示；他人订单 403 |
| F-07 | 我的订单 | 订单列表（按状态 tab 筛选）、详情（商品/金额/地址/状态时间线）、操作：去支付/取消（待支付）、确认收货（已发货→已完成，写 finish_time + log） | 全状态操作按钮按状态显隐，时间线与 mall_order_log 一致 |
| F-08 | 地址管理（C 端） | 复用 mall_address 表：C 端增删改查 + 默认地址；下单页选地址 | 只能操作本人地址（member_id 隔离） |

### P1：交易闭环最小配套（应该）

| # | 功能 | 要点 | 验收 |
|---|---|---|---|
| F-09 | 后台发货 | MallOrderController 加 /deliver（快递公司+单号，仅 status=1 可操作）：status→2 + deliver_time + log。C 端演示闭环的最后一环 | 后台发货后 C 端出现「确认收货」；非法状态 400 |
| F-10 | 优惠券 | C 端领券中心（领券写 coupon_record）+ 下单页选券（校验有效期/门槛，discount_amount 联动，支付后券置已用） | 用券下单金额正确；已用券不可复用 |
| F-11 | 交易核心最小测试 | 为库存扣减（不超卖/回补）、状态机（合法/非法迁移）、支付幂等补 JUnit 用例（打破 0 测试类现状，只覆盖交易核心，不追求全量） | mvn test 绿，用例含并发扣库存场景 |

### P2：可选（看余量）

| # | 功能 | 说明 |
|---|---|---|
| F-12 | 会员中心增强 | 头像/昵称编辑、优惠券列表、浏览数统计 |
| F-13 | 商品搜索 | C 端按关键字搜商品（LIKE 即可，不上 ES） |

## 五、明确不做（本版本）

- **退款/售后**：C 端申请 + 后台审核流（V2 F-06）后置 V4，等交易主链路稳定
- **销售看板**（V2 F-07）：后置 V4
- **真实微信/支付宝**：只跑 mock 渠道协议
- **微信小程序**：H5 先行，uni-app 迁移看后续
- **后台订单全操作细化**（改价/备注/关闭）：只做发货这一最小动作
- **秒杀/拼团等营销玩法**

## 六、排期建议

| 步骤 | 内容 | 量级 |
|---|---|---|
| 1 | mall_member 加列 + mall_cart 建表 + C 端认证（F-03） | 0.5 天 |
| 2 | fe/mall-shop 脚手架 + 浏览页（F-01/F-02） | 1 天 |
| 3 | 购物车 + 地址（F-04/F-08） | 0.5 天 |
| 4 | 下单/状态机/库存/mock 支付（F-05/F-06，后端为主） | 1.5 天 |
| 5 | 我的订单 + 后台发货（F-07/F-09） | 0.5 天 |
| 6 | 优惠券 + 最小测试（F-10/F-11） | 0.5~1 天 |
| 7 | 全链路冒烟 + 并发超卖验证 + 沉淀提交 | 0.5 天 |

## 七、验收清单

- [ ] 新客全链路：注册 → 浏览 → 加购 → 下单 → mock 支付 → 后台发货 → C 端确认收货，全程无手改数据库
- [ ] 库存：支付前扣减、取消回补、并发下单同一 SKU 不超卖（多线程冒烟）
- [ ] 幂等：重复支付不重复扣库存/不改状态；越权访问他人订单/地址被拒
- [ ] 状态机：合法迁移全通、非法迁移 400、每步 mall_order_log 有据
- [ ] 测试：交易核心用例 mvn test 绿（本仓首批测试类）
- [ ] 编译零错；C 端页面移动端视口正常；后端接口带统一错误码

## 八、备选主题（本次未选）

1. **回头先实施 V2 后台交易闭环**——与 C 端强耦合，拆开做前端等后端，合并进 V3
2. **质量加固版本**（生成器真源修复 + 拆 mall_order/index.vue + 全量测试）——价值后置，F-11 已顺带覆盖最关键部分
3. **直接微信小程序**——调试链路长，H5 先行

## 九、风险

| 风险 | 应对 |
|---|---|
| V3 吞下 V2 后端能力，范围偏大 | P0 只做链路必需项；F-10/F-11 放 P1 可视进度裁剪 |
| 手写 mock 支付与平台支付能力包将来重复 | 文档已声明回迁策略；mock 端点收敛在 /shop/pay 一处，便于替换 |
| C 端认证与后台认证共存引入过滤器冲突 | member token 加前缀区分，ShopAuthInterceptor 只拦 /shop/** 写接口，浏览接口保持匿名 |
| 首个 Vue3 工程，团队栈从 Vue2 切换 | 仅限 fe/mall-shop 新工程，mall-web 不动 |
| mall_member 加 password 列涉及存量数据 | 按 mysql-ddl-standard 改表：列允许 NULL，存量会员首次登录引导设密码（或后台重置） |

---

## 修订记录

| 日期 | 版本 | 内容 |
|---|---|---|
| 2026-10-29 | R1 | 追加「十、功能设计（S3）」「十一、接口设计（S5）」。**口径修正**：F-05 规划期状态码写的 0/1/2/3/4，实现沿用库表与实体既定口径 **10/20/30/40/50/60**（MallOrder.status 注释与字典同源），以本章为准。 |

---

## 十、功能设计（S3）

### 10.1 订单状态机（定稿口径）

| 状态值 | 含义 | 可迁移到 | 触发方 | 副作用 |
|---|---|---|---|---|
| 10 | 待付款 | 20 / 50 | 会员支付 / 会员取消 | 迁移 20：写 pay_*、sku.sales 累加；迁移 50：回补 sku.stock |
| 20 | 待发货 | 30 | 管理员发货 | 写 express_company/express_no/deliver_time |
| 30 | 已发货 | 40 | 会员确认收货 | 写 finish_time |
| 40 | 已完成 | — | 终态 | — |
| 50 | 已取消 | — | 终态 | — |
| 60 | 已关闭 | — | 终态（系统关单，本版本不实现定时关单，仅保留枚举） | — |

- 每次迁移写一行 mall_order_log（operator_type：1 会员 / 2 管理员 / 3 系统）。
- 非法迁移一律 400（如待发货取消、重复确认收货）。
- 迁移全部走**条件 UPDATE**（`WHERE id=? AND status=<前置>`），受影响行数=0 即视为并发/非法，返回明确错误——天然幂等。

### 10.2 库存与金额规则

| 规则 | 定稿 |
|---|---|
| 扣库存时机 | **下单即扣**（支付前锁定），取消回补；sales 在支付成功时累加 |
| 防超卖 | `UPDATE mall_sku SET stock=stock-? WHERE id=? AND stock>=? AND deleted=0`，受影响=0 → 回滚报「库存不足」 |
| 回补 | 取消订单：`stock=stock+?`（无条件，仅取消分支调用） |
| 金额公式 | total_amount=Σ(price×num)；freight_amount=0（本版本免运费）；discount_amount=券面额（满减券）；pay_amount=total+freight−discount，下限 0；单位统一**分**（bigint） |
| 订单号 | `M` + yyyyMMddHHmmss + 6 位随机数 |
| mock 支付单号 | `MOCK` + yyyyMMddHHmmss + 6 位随机数，写入 pay_order_no；pay_type=9（mock，避开 1 微信/2 支付宝口径） |

### 10.3 认证与鉴权设计（AuthContextFilter 收窄）

| 项 | 定稿 |
|---|---|
| member token | 复用 TokenStore.create，username 存 `m:{memberId}`，sys_session 落盘、滑动续期与后台一致 |
| 白名单（收窄后） | 匿名：`POST /shop/auth/register`、`POST /shop/auth/login`、`GET /shop/banners`、`GET /shop/categories`、`GET /shop/goods`、`GET /shop/goods/{id}`。**移除整个 "/shop" 前缀放行** |
| 会员分支 | username 以 `m:` 开头 → 跳过 PermissionChecker（sys 权限体系与会员无关）；仅允许访问 `/shop/**`，访问其他路径 403；UserContext.setUserId(memberId)、setUsername("m:"+id) |
| 后台 token 访问 /shop 写接口 | 非会员 token 命中非白名单 /shop/** → 401（引导用会员身份），职责隔离不混用 |
| 会员数据隔离 | 所有 /shop 写/个人读接口，memberId 只从 UserContext 取，**入参传 memberId 一律忽略**（防越权） |

### 10.4 分模块功能设计表

| # | 模块 | 功能 | 业务规则 | 异常分支 | 涉及表 |
|---|---|---|---|---|---|
| A1 | 认证 | 注册 | 手机号 11 位校验；uk_phone 唯一；密码 6~20 位 BCrypt；source=2(H5)、status=1；注册成功直接发 token | 手机号已注册 400；参数非法 400 | mall_member |
| A2 | 认证 | 登录 | 手机号+密码，BCrypt 比对；更新 last_login_time；返回 token+会员摘要 | 手机号未注册/密码错误统一 400「手机号或密码错误」；status=0 禁用 403 | mall_member、sys_session |
| A3 | 认证 | 登出/我的资料 | 登出删 sys_session；资料读改（昵称/头像/性别） | 未登录 401 | mall_member、sys_session |
| B1 | 购物车 | 加购 | sku 须 status=1 且所属商品上架；同 member+sku 已存在则数量累加（uk_member_sku）；累加后超库存 400 | sku 不存在/下架 400；超库存 400 | mall_cart、mall_sku、mall_goods |
| B2 | 购物车 | 列表/改量/勾选/删除 | 列表联查出商品名、图、SKU 价、实时库存、勾选态；改量须 ≥1 且 ≤库存；下架商品行标记不可用不删除 | 行不属于本人 403；改量超库存 400 | mall_cart、mall_sku、mall_goods |
| C1 | 地址 | 增删改查 | member_id 隔离；默认地址唯一（设默认时同事务清其他）；首条自动设默认 | 行不属于本人 403 | mall_address |
| D1 | 下单 | 确认页预览 | 取购物车 checked=1 行（或立即购买 skuId+num）；实时重算价格/库存，返回不可购买行原因；返回可用券列表 | 无勾选项 400 | mall_cart、mall_sku、mall_goods、mall_coupon、mall_coupon_record |
| D2 | 下单 | 提交订单 | 事务：校验地址归属→按行条件扣库存→券校验+锁定（P1）→写 order(status=10)+item 快照+log(operator_type=1)→删除已下单购物车行 | 地址非本人 403；任一 SKU 库存不足整体回滚 400；券不可用 400 | mall_order、mall_order_item、mall_order_log、mall_sku、mall_cart、mall_coupon_record |
| D3 | 下单 | 取消 | 仅 status=10，条件 UPDATE→50；回补库存；券解锁回未用；log | 非本人 403；状态不符 400 | mall_order、mall_sku、mall_coupon_record、mall_order_log |
| E1 | 支付 | mock 支付 | 仅本人+status=10；条件 UPDATE 置 pay_status=1/status=20/pay_type=9/pay_time/pay_order_no；sku.sales 累加；券置已用；log | 非本人 403；重复支付幂等返回「订单已支付」不重复扣/记；状态不符 400 | mall_order、mall_sku、mall_coupon_record、mall_order_log |
| F1 | 我的订单 | 列表 | 按状态 tab（全部/10/20/30/40）；每单携 items 摘要（前 N 图）；member_id 隔离 | — | mall_order、mall_order_item |
| F2 | 我的订单 | 详情 | 订单+items 快照+地址快照+状态时间线（mall_order_log 升序） | 非本人 403 | mall_order、mall_order_item、mall_order_log |
| F3 | 我的订单 | 确认收货 | 仅 status=30→40；写 finish_time；log | 非本人 403；状态不符 400 | mall_order、mall_order_log |
| G1 | 后台 | 发货 | 仅 status=20→30；快递公司+单号必填；写 deliver_time；log(operator_type=2)；权限点 mall_mall_order:edit | 状态不符 400；无权限 403 | mall_order、mall_order_log |
| H1 | 优惠券(P1) | 领券中心 | 出 status=1 且在有效期、received_count<total_count 的券；标注每人已领数 | — | mall_coupon、mall_coupon_record |
| H2 | 优惠券(P1) | 领券 | per_limit 校验；received_count 条件更新（<total_count 才 +1）防超发；写 record(status=1) | 超限领/已抢光/不在有效期 400 | mall_coupon、mall_coupon_record |
| H3 | 优惠券(P1) | 用券 | 下单时校验：本人、未使用、有效期内、订单总额达门槛；满减券 discount=面额；支付成功置已用（used_time+order_id）；取消订单解锁 | 券不可用 400 | mall_coupon、mall_coupon_record |

---

## 十一、接口设计（S5）

统一约定：响应 `Result{code,msg,data}`；错误码 400 参数/业务、401 未登录、403 越权/无权限、500 异常；金额单位分；ID 以字符串下发（雪花）；除注明「匿名」外均需 `Authorization: Bearer <member token>`。

### 11.1 认证 /shop/auth

| 方法 | 路径 | 鉴权 | 入参 | 返回 data | 说明 |
|---|---|---|---|---|---|
| POST | /shop/auth/register | 匿名 | `{phone, password, nickname?}` | `{token, member:{id,nickname,phone,avatar}}` | 见 A1 |
| POST | /shop/auth/login | 匿名 | `{phone, password}` | 同上 | 见 A2 |
| POST | /shop/auth/logout | member | — | null | 删会话 |
| GET | /shop/auth/profile | member | — | `{id,nickname,phone,avatar,gender,points}` | 当前会员 |
| PUT | /shop/auth/profile | member | `{nickname?, avatar?, gender?}` | null | 改资料 |

### 11.2 浏览（已有，保持不变）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | /shop/banners | 匿名 | 已有 |
| GET | /shop/categories | 匿名 | 已有 |
| GET | /shop/goods | 匿名 | 已有（keyword/categoryId/sort/分页） |
| GET | /shop/goods/{id} | 匿名 | 已有（含 SKU 列表） |

### 11.3 购物车 /shop/cart

| 方法 | 路径 | 鉴权 | 入参 | 返回 data |
|---|---|---|---|---|
| GET | /shop/cart/list | member | — | `[{id, skuId, goodsId, goodsName, skuImage, specJson, price, stock, num, checked, goodsStatus, skuStatus}]`（不可用行照返，前端置灰） |
| POST | /shop/cart/add | member | `{skuId, num}` | null |
| PUT | /shop/cart/update | member | `{id, num?, checked?}` | null |
| DELETE | /shop/cart/{id} | member | — | null |

### 11.4 地址 /shop/address

| 方法 | 路径 | 鉴权 | 入参 | 返回 data |
|---|---|---|---|---|
| GET | /shop/address/list | member | — | `[{id, receiverName, receiverPhone, province, city, district, detail, isDefault}]` |
| POST | /shop/address | member | `{receiverName, receiverPhone, province, city, district, detail, isDefault?}` | null |
| PUT | /shop/address | member | `{id, ...同上}` | null |
| DELETE | /shop/address/{id} | member | — | null |
| PUT | /shop/address/{id}/default | member | — | null |

### 11.5 订单 /shop/order

| 方法 | 路径 | 鉴权 | 入参 | 返回 data | 说明 |
|---|---|---|---|---|---|
| POST | /shop/order/confirm | member | `{skuId?, num?}`（空=购物车勾选模式） | `{items:[{skuId,goodsId,goodsName,skuImage,specJson,price,num,stock,buyable,reason}], totalAmount, discountAmount, payAmount, usableCoupons:[...]}` | 预览不落库，见 D1 |
| POST | /shop/order/submit | member | `{addressId, skuId?, num?, couponRecordId?, buyerRemark?}` | `{orderId, orderNo, payAmount}` | 事务，见 D2 |
| GET | /shop/order/page | member | `status?, pageNum, pageSize` | `Page<{id, orderNo, status, payAmount, createTime, items:[{skuImage,goodsName,price,quantity}]}>` | 见 F1 |
| GET | /shop/order/{id} | member | — | `{order 全字段, items:[...], logs:[{content, operator, operatorType, createTime}]}` | 见 F2 |
| POST | /shop/order/cancel | member | `{orderId}` | null | 见 D3 |
| POST | /shop/order/confirm-receipt | member | `{orderId}` | null | 见 F3 |

### 11.6 支付 /shop/pay

| 方法 | 路径 | 鉴权 | 入参 | 返回 data | 说明 |
|---|---|---|---|---|---|
| POST | /shop/pay/mock | member | `{orderNo}` | `{orderNo, status, payTime, replay}` | 见 E1；`replay=true` 表示幂等重放 |

### 11.7 优惠券 /shop/coupon（P1）

| 方法 | 路径 | 鉴权 | 入参 | 返回 data |
|---|---|---|---|---|
| GET | /shop/coupon/center | member | — | `[{id, name, type, amount, thresholdAmount, startTime, endTime, perLimit, received:本人已领数, left:剩余}]` |
| POST | /shop/coupon/receive | member | `{couponId}` | null |
| GET | /shop/coupon/my | member | `status?`（1未用2已用3过期） | `[{recordId, couponId, name, amount, thresholdAmount, endTime, status}]` |

### 11.8 后台发货 /mall_order（P1，写入手写区）

| 方法 | 路径 | 鉴权 | 入参 | 返回 data | 说明 |
|---|---|---|---|---|---|
| POST | /mall_order/deliver | 后台 token + 权限点 `mall_mall_order:edit` | `{id, expressCompany, expressNo}` | null | 见 G1；端点注册进 MallOrderApiPerms |
