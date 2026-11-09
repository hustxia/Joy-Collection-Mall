# 商城（mall）V7 规划——C 端营销玩法

> 2026-11-01 起草。与《商城-V2-交易闭环-规划.md》《商城-V3-C端商城-规划.md》《商城-V4-售后与经营-规划.md》《商城-V6-生产就绪-规划.md》并列，前四版文档继续有效。
> V5 交付：单体拆微服务（六服务 + 网关）。V6 交付：生产就绪（测试体系 65 例、网关鉴权限流、traceId/Prometheus、Docker Compose、GitHub Actions CI、配置收敛 mall-config）。
> V7 回到**功能面**：评价、收藏、积分、签到、站内消息——都是三端可见的商城玩法。

## 一、目标

让商城从「能买」变成「愿意逛」：买过的能评价、逛中的能收藏、消费/签到有积分、订单动态有消息。
延续 V6 的工程口径：每个功能都带单测 + fe-smoke 断言 + ui-check 页面走查，不留「能跑但没验证」的口子。

## 二、现状盘点（2026-11-01 实测）

| 项 | 现状 | 结论 |
|---|---|---|
| 商品评价 | 不存在（无表/无接口/无页面） | **进 V7 P0** |
| 商品收藏 | 不存在；会员域已有足迹（footprint）可参照 | **进 V7 P0** |
| 会员积分 | 不存在；`mall_member` 无 point 列 | **进 V7 P0**（消费返 + 流水 + 余额） |
| 每日签到 | 不存在 | **进 V7 P1**（积分的主要来源） |
| 站内消息 | 不存在；订单/售后状态机已有日志可挂触发点 | **进 V7 P1** |
| 积分抵扣下单 | 依赖积分体系 | **进 V7 P2** |
| V6 P2 遗留 | X-01 分布式锁 / X-02 文档聚合 / X-03 存储抽象 / X-04 lb:// | X-02 顺手进 V7 P2，其余继续挂起 |
| 文件上传 | mall-system 已有 `/shop/upload`，评价图片直接复用 | 不重做 |
| 表结构规范 | `mysql-ddl-standard` skill（六件套/幂等/注释） | 本版 DDL 全部照此执行 |

## 三、范围

### P0：评价 / 收藏 / 积分

| 编号 | 功能 | 归属服务 | 要点 | 验收 |
|---|---|---|---|---|
| F-01 | 商品评价 | **mall-goods**（读多，随商品详情聚合） | 已完成订单的订单项才可评、每项限一次；评分 1~5 + 文字 + 图片（复用上传）；商品详情返回均分/总数/分页列表；后台可回复/隐藏 | 重复评价 400；未购评价 400；隐藏后列表不可见 |
| F-02 | 商品收藏 | **mall-member**（与足迹/地址/购物车同域） | 收藏/取消一个接口幂等切换；详情页返回收藏态；我的收藏分页 | 重复切换幂等；未登录 401 |
| F-03 | 会员积分 | **mall-member** | `mall_member` 加 `point` 余额列 + `mall_member_point_log` 流水；支付成功由 order 调 member `/inner` 发放（1 元 = 1 分）；流水页可查 | 支付后余额/流水一致；余额永不为负 |

- F-01 依赖 order 服务补一个内部契约：`GET /inner/order-item/can-review?orderItemId=&memberId=`（校验本人 + 已完成 + 未评过）。
- F-03 发放走 order 支付成功回调里的 Feign 调用；失败只记日志不阻断支付（营销性动作，降级优先）。

### P1：签到 / 站内消息

| 编号 | 功能 | 归属服务 | 要点 | 验收 |
|---|---|---|---|---|
| F-04 | 每日签到 | mall-member | 每天一次，连续签到加成（第 N 天送 N 分，7 天封顶循环）；签到日历按月查询 | 重复签到 400；连续天数断签重置 |
| F-05 | 站内消息 | mall-member | 订单支付/发货/收货、售后审核/退款完成触发；C 端消息中心 + 未读角标 + 已读；触发方（order/refund）调 member `/inner` 推送 | 下单→支付→发货各产生一条；未读数准确 |

### P2：可选（看余量）

| 编号 | 功能 | 说明 |
|---|---|---|
| F-06 | 积分抵扣下单 | 结算页勾选积分抵现（100 分 = 1 元，单笔上限订单金额 20%）；order 调 member 扣减，退款时扣回发放积分 |
| F-07 | X-02 API 文档聚合 | 网关 `/v3/api-docs` 聚合 6 个服务的 springdoc，一个地址看全量 |

### 明确不做

- 秒杀 / 拼团 / 满减（独立大版本再议）
- 积分商城（积分换商品）
- 短信 / 邮件 / App 推送（站内信即止）
- 评价标签体系、评价图片审核
- 注册中心 / MQ（V6 已定：静态地址够用）

## 四、数据模型（db/v7-engagement.sql，compose 挂载 06 号位）

DDL 全部遵循 `mysql-ddl-standard`（`IF NOT EXISTS` 幂等、审计六件套、每列 COMMENT、utf8mb4、唯一键带 deleted）。

```sql
-- 商品评价（mall-goods）
CREATE TABLE IF NOT EXISTS mall_review (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id bigint NOT NULL COMMENT '订单ID',
  order_item_id bigint NOT NULL COMMENT '订单项ID',
  goods_id bigint NOT NULL COMMENT '商品ID',
  sku_id bigint NOT NULL COMMENT 'SKU ID',
  member_id bigint NOT NULL COMMENT '会员ID',
  member_nickname varchar(64) NOT NULL DEFAULT '' COMMENT '会员昵称（冗余，免联查）',
  rating tinyint NOT NULL COMMENT '评分：1~5',
  content varchar(500) NOT NULL DEFAULT '' COMMENT '评价内容',
  images varchar(1000) NOT NULL DEFAULT '' COMMENT '评价图片，逗号分隔（复用 /shop/upload）',
  status tinyint NOT NULL DEFAULT 0 COMMENT '状态：0正常1后台隐藏',
  reply varchar(500) NULL COMMENT '后台回复',
  reply_time datetime NULL COMMENT '回复时间',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by bigint NULL COMMENT '创建人',
  update_by bigint NULL COMMENT '更新人',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_item (order_item_id, deleted),
  KEY idx_review_goods (goods_id, status),
  KEY idx_review_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价';

-- 商品收藏（mall-member）
CREATE TABLE IF NOT EXISTS mall_favorite (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  member_id bigint NOT NULL COMMENT '会员ID',
  goods_id bigint NOT NULL COMMENT '商品ID',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by bigint NULL COMMENT '创建人',
  update_by bigint NULL COMMENT '更新人',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite_member_goods (member_id, goods_id, deleted),
  KEY idx_favorite_goods (goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏';

-- 积分流水（mall-member）；余额在 mall_member.point 冗余，以流水为准可对账
CREATE TABLE IF NOT EXISTS mall_member_point_log (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  member_id bigint NOT NULL COMMENT '会员ID',
  change_type tinyint NOT NULL COMMENT '变动类型：1消费返2签到3下单抵扣4退款扣回',
  points int NOT NULL COMMENT '变动积分：正加负减',
  balance_after int NOT NULL COMMENT '变动后余额',
  order_id bigint NULL COMMENT '关联订单ID（消费返/抵扣/扣回时有值）',
  remark varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by bigint NULL COMMENT '创建人',
  update_by bigint NULL COMMENT '更新人',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_point_log_member (member_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员积分流水';

-- 每日签到（mall-member）
CREATE TABLE IF NOT EXISTS mall_sign_in (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  member_id bigint NOT NULL COMMENT '会员ID',
  sign_date date NOT NULL COMMENT '签到日期',
  continuous_days int NOT NULL DEFAULT 1 COMMENT '当日连续天数',
  points int NOT NULL COMMENT '本次赠送积分',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by bigint NULL COMMENT '创建人',
  update_by bigint NULL COMMENT '更新人',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sign_member_date (member_id, sign_date, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员每日签到';

-- 站内消息（mall-member）
CREATE TABLE IF NOT EXISTS mall_notice (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  member_id bigint NOT NULL COMMENT '会员ID',
  type tinyint NOT NULL COMMENT '类型：1订单2售后3营销',
  title varchar(64) NOT NULL COMMENT '标题',
  content varchar(255) NOT NULL DEFAULT '' COMMENT '内容',
  biz_id varchar(64) NOT NULL DEFAULT '' COMMENT '关联业务号（订单号/售后单号）',
  read_status tinyint NOT NULL DEFAULT 0 COMMENT '读取状态：0未读1已读',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by bigint NULL COMMENT '创建人',
  update_by bigint NULL COMMENT '更新人',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_notice_member_read (member_id, read_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

-- 会员表加积分余额列（改表前需确认影响面：仅新增列，不动存量数据）
ALTER TABLE mall_member
  ADD COLUMN point int NOT NULL DEFAULT 0 COMMENT '积分余额' AFTER `status`;
```

同步动作：
- `mall-test-support/schema-test.sql` 追加同 5 表 + 加列（测试库自动建表保持一致）；
- `docker-compose.yml` mysql 卷追加 `./db/v7-engagement.sql:/docker-entrypoint-initdb.d/06-v7-engagement.sql:ro`；
- 种子：给演示会员 `13800000001` 预置 2 条已签收订单的评价 + 100 积分 + 3 条消息，`INSERT IGNORE` 幂等。

## 五、接口设计

### C 端（网关路由新增）

| 服务 | 路由 | 接口 |
|---|---|---|
| goods | `/shop/review/**` | `POST /shop/review` 提交 · `GET /shop/review/goods/{goodsId}` 分页（返回均分+总数+列表） · `GET /shop/review/my` 我的评价 |
| member | `/shop/favorite/**` | `POST /shop/favorite/{goodsId}` 幂等切换 · `GET /shop/favorite/list` 分页 · `GET /shop/favorite/check/{goodsId}` |
| member | `/shop/point/**` | `GET /shop/point/balance` · `GET /shop/point/logs` 分页 |
| member | `/shop/signin/**` | `POST /shop/signin` · `GET /shop/signin/calendar?month=yyyy-MM` |
| member | `/shop/notice/**` | `GET /shop/notice/list` 分页 · `GET /shop/notice/unread-count` · `POST /shop/notice/read/{id}` · `POST /shop/notice/read-all` |

商品详情 `GET /shop/goods/{id}` 返回体追加 `ratingAvg` / `ratingCount` / `favorited`（登录态时）。

### 后台（网关路由新增）

| 服务 | 路由 | 接口 |
|---|---|---|
| goods | `/mall_review/**` | `POST /mall_review/page` · `POST /mall_review/reply` · `POST /mall_review/hide` |
| member | `/mall_notice/**` | `POST /mall_notice/page`（只读台账） |

### 内部契约（/inner，不经网关）

| 提供方 | 接口 | 消费方 |
|---|---|---|
| order | `GET /inner/order-item/can-review` | goods（F-01 写路径校验） |
| member | `POST /inner/point/grant` | order（支付成功发积分，P0）/ member 自身签到复用 |
| member | `POST /inner/notice/push` | order、refund（状态机触发消息，P1） |

## 六、前端

| 端 | 页面/改动 |
|---|---|
| H5（Vant） | 商品详情：评价区（均分条+列表+图片九宫格）+ 收藏按钮；订单详情：已完成项「评价」入口（评分+文字+传图）；个人中心：我的收藏 / 我的积分（流水）/ 每日签到（日历）/ 消息中心（未读角标） |
| PC（Element） | 商品详情：评价区 + 收藏；个人中心：收藏/积分/消息入口 |
| 后台（Element） | 新增「评价管理」（列表/回复/隐藏）、「消息台账」（只读列表）；菜单走 `sys_menu` 配置 |

## 七、排期建议

| 步 | 内容 | 产出 |
|---|---|---|
| 1 | DDL + 种子 + schema-test 同步 + compose 挂载 | v7-engagement.sql 可重复执行 |
| 2 | member 域后端：收藏 + 积分 + 签到 + 消息（含 /inner 契约） | 单测绿 |
| 3 | goods 域后端：评价 + order can-review 契约 + 详情聚合字段 | 单测绿 |
| 4 | order/refund 触发点：支付发积分、状态机推消息 | 联调通 |
| 5 | 网关路由 5 条 C 端 + 2 条后台 | GatewayRouteTest 更新 |
| 6 | H5 页面 | 真机可走查 |
| 7 | PC + 后台页面 | ui-check 过 |
| 8 | fe-smoke 追加第 9 段（评价/收藏/积分/签到/消息断言） | 51 → 78 断言 |
| 9 | 全量验收 + 文档收尾（README 版本历程、本文件 R1） | 验收清单全勾 |

## 八、验收清单

- [x] `mvn verify` 全绿（新增单测 ≥ 20 例：评价幂等/未购拦截、收藏切换、积分余额一致性、签到重复与断签、消息未读数）—— 全量 92 例 0 失败
- [x] `node mall-cloud-fe-smoke.mjs` 全绿，新增段覆盖：支付→积分到账→评价→收藏→签到→消息 —— 78/78
- [x] `node mall-cloud-ui-check.mjs` 三端新页面零 console error —— 199/199（后台 16 页 + PC 9 页 + H5 11 页）
- [x] `v7-engagement.sql` 重复执行 2 次无报错、无重复数据（幂等）—— 复跑全部 rows=0
- [x] `/health/aggregate` 全 UP；新增 /inner 端点不经网关（直连验证 200、走网关 403）
- [x] 退款后库存回补不回补积分（P0 口径，注释写明；扣回进 P2 F-06）

## 九、修订记录

| 日期 | 版本 | 说明 |
|---|---|---|
| 2026-11-01 | R0 | 初稿。基于当日实盘（评价/收藏/积分/签到/消息均不存在，足迹已在 member 域）定 P0 三件套 + P1 两件套 + P2 抵扣与文档聚合；服务归属按「读多随商品、会员资产随 member」划分。 |
| 2026-11-01 | R1 | 收尾。六步 P0/P1 全部交付：goods 评价域（提交/均分聚合/后台回复隐藏）、member 收藏·积分·签到·消息、order 支付发积分与状态机推消息、网关 7 条路由、H5/PC/后台三端页面。验收：`mvn verify` 92 例全绿；fe-smoke 51→78 断言全通过；ui-check 136→199 全通过；`v7-engagement.sql`/`v7-menu.sql` 二次复跑 rows=0 幂等；`/health/aggregate` 7 服务全 UP，`/inner/**` 直连 200、走网关 403。 |
