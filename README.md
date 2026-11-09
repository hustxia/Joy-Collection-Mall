# 悦集商城 Joy Collection Mall

> 前后端分离、三端一体（H5 商城 / PC 商城 / 运营后台）的电商演示系统，后端为**按业务域拆分的 Spring Cloud 微服务**架构。

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.3-6DB33F)
![Vue](https://img.shields.io/badge/Vue-3.5-42b883)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)
![License](https://img.shields.io/badge/license-MIT-blue)

## 项目简介

悦集商城是一套功能完整的电商演示系统，支持从商品浏览、加购、下单、支付到售后的完整购物闭环。系统采用微服务架构，后端按业务域拆分为多个独立服务，前端同时支持 H5 移动端、PC 端和运营管理后台。

### 核心特性

- **微服务架构**：10 个业务服务 + 网关 + 公共库，服务间通过 Feign 通信
- **三端一体**：H5 商城（9531）、PC 商城（9432）、运营后台（9431），另有独立商家中心（后台内）
- **完整业务流程**：商品管理、会员系统、订单处理、营销活动、售后服务、商家入驻、客服工单、消息触达
- **营销能力**：优惠券、秒杀、拼团、抽奖、分销推广、促销引擎
- **搜索增强**：支持拼音、同义词、错字纠错、热搜联想

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2.5 / Spring Cloud 2023.0.3 |
| Java 版本 | JDK 21 |
| 微服务组件 | OpenFeign（静态地址配置，预留注册中心）、Gateway |
| 数据库 | MySQL 8.0 |
| ORM | MyBatis-Plus |
| 前端框架 | Vue 3.5（三端统一，Vite 构建，Element Plus / Vant 4）|
| 构建工具 | Maven 3.6+ / Node 18+ |

## 系统架构

```
mall-shop(H5)      mall-pc(PC)      mall-web(后台)
   9531               9432              9431
    └─────────────────┼─────────────────┘
                      ▼  HTTP + JWT
              mall-gateway :8080
   ┌────────┬────────┼────────┬─────────┬────────┬────────┬────────┬─────────┐
   ▼        ▼        ▼        ▼         ▼        ▼        ▼        ▼         ▼
 system   goods   member    order   marketing  refund  merchant  support  message
  :8201   :8202   :8203    :8204     :8205    :8206    :8207    :8208    :8209
 鉴权/用户 SPU/SKU 会员/地址 下单/支付 优惠券/营销 售后状态机 商家/店铺  客服工单  消息触达
```

### 服务说明

| 服务 | 端口 | 业务域 |
|------|------|--------|
| mall-gateway | 8080 | 统一入口、路由、鉴权（平台/会员/商家三方令牌分流） |
| mall-system | 8201 | 登录鉴权、用户/角色/菜单、字典、文件 |
| mall-goods | 8202 | SPU/SKU、商品分类、品牌、轮播图、运费模板 |
| mall-member | 8203 | 会员、地址、购物车、足迹、积分、签到、收藏 |
| mall-order | 8204 | 下单、订单、支付、发货、物流、发票 |
| mall-marketing | 8205 | 优惠券、秒杀、拼团、抽奖、分销、促销引擎 |
| mall-refund | 8206 | 退款单、售后状态机（退款/退货/换货/平台仲裁） |
| mall-merchant | 8207 | 商家入驻、店铺、结算、提现、商家中心 |
| mall-support | 8208 | 客服工单（48h 超时仲裁） |
| mall-message | 8209 | 消息触达（短信/站内信 SPI + 失败降级） |

## 快速开始

### 环境要求

- JDK 21
- Maven 3.6+
- Node 18+
- MySQL 8.0

### 1. 创建数据库并导入数据

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS yueji_mall DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
mysql -uroot -p --default-character-set=utf8mb4 yueji_mall < audit/yueji_mall-dump.sql
```

### 2. 构建后端

```bash
cd be/mall-cloud
mvn -o -DskipTests package
```

### 3. 启动后端服务

```bash
# 启动网关（必需）
java -jar mall-gateway/target/mall-gateway-1.0.0.jar --server.port=8080

# 启动各个业务服务
java -jar mall-system/target/mall-system-1.0.0.jar --server.port=8201
java -jar mall-goods/target/mall-goods-1.0.0.jar --server.port=8202
java -jar mall-member/target/mall-member-1.0.0.jar --server.port=8203
java -jar mall-order/target/mall-order-1.0.0.jar --server.port=8204
java -jar mall-marketing/target/mall-marketing-1.0.0.jar --server.port=8205
java -jar mall-refund/target/mall-refund-1.0.0.jar --server.port=8206
java -jar mall-merchant/target/mall-merchant-1.0.0.jar --server.port=8207
java -jar mall-support/target/mall-support-1.0.0.jar --server.port=8208
java -jar mall-message/target/mall-message-1.0.0.jar --server.port=8209
```

> 一键脚本：`be/mall-cloud/start-local.sh`（10 服务）+ `fe/start-fe.sh`（三端）；后台调试登录需 `-DAUTH_CAPTCHA_EXPOSE_ANSWER=true`。

### 4. 启动前端

```bash
# 运营后台
cd fe/mall-web && npm install && npm run dev

# PC 商城
cd fe/mall-pc && npm install && npm run dev

# H5 商城
cd fe/mall-shop && npm install && npm run dev
```

## 目录结构

```
├── be/mall-cloud/          # 后端微服务工程
│   ├── mall-common/        # 公共库 + Feign 契约
│   ├── mall-gateway/       # 网关 8080
│   ├── mall-system/        # 系统域 8201
│   ├── mall-goods/         # 商品域 8202
│   ├── mall-member/        # 会员域 8203
│   ├── mall-order/         # 订单域 8204
│   ├── mall-marketing/     # 营销域 8205
│   ├── mall-refund/        # 售后域 8206
│   ├── mall-merchant/      # 商家域 8207
│   ├── mall-support/       # 客服工单 8208
│   ├── mall-message/       # 消息触达 8209
│   └── uploads/            # 上传文件存储
├── fe/                     # 前端项目
│   ├── mall-web/          # 运营后台 9431
│   ├── mall-pc/           # PC 商城 9432
│   └── mall-shop/         # H5 商城 9531
├── audit/                  # 数据库脚本
│   └── yueji_mall-dump.sql # 完整数据快照
├── docs/                   # 文档
└── README.md
```

## 接口与约定

- **统一入口**：网关 8080 统一对外服务
- **后台接口**：`/auth/**`、`/system/**`、`/mall_dashboard/**` 等
- **C 端接口**：`/shop/**`
- **内部接口**：`/inner/**` 仅供服务间调用，不对外暴露
- **会话管理**：Token 存储在 `sys_session` 表，支持跨服务验证

## 演示账号

| 端 | 账号 | 密码 |
|---|------|------|
| C 端会员 | 13800000001 ~ 13800000006 | 123456 |
| 运营后台 | admin | admin123 |
| 商家中心 | merchant | 123456 |

## 业务模块

### 商品模块 (mall-goods)
- SPU/SKU 管理
- 商品分类与品牌
- 轮播图配置
- 运费模板与计费规则
- 商品评价

### 会员模块 (mall-member)
- 会员注册登录
- 收货地址管理
- 购物车
- 收藏与足迹
- 积分与签到
- 会员等级与权益
- 站内消息

### 订单模块 (mall-order)
- 下单与支付
- 订单状态管理
- 超时关单
- 发货与物流
- 发票管理

### 营销模块 (mall-marketing)
- 优惠券领取与使用
- 秒杀活动
- 拼团活动
- 抽奖活动
- 分销推广
- 促销引擎（满减/满折/限时折扣等）

### 售后模块 (mall-refund)
- 退款申请
- 售后状态机
- 库存回补

### 商家模块 (mall-merchant)
- 商家入驻申请
- 店铺管理
- 结算与提现
- 商家后台运营

### 客服模块 (mall-support)
- 工单创建与回复
- 状态流转与超时仲裁

### 消息模块 (mall-message)
- 站内信 / 短信触达
- 模板管理与失败重试

## 版本历程

- **V3**：商城主链路（商品 / SKU / 购物车 / 下单 / 模拟支付 / 优惠券 / 会员 / 地址）+ 运营后台 12 模块
- **V4**：售后闭环（七态状态机、条件 UPDATE 幂等、库存自动回补）+ 经营看板 + 超时关单
- **V5**：单体拆分为微服务，跨域零代码依赖，`/inner` 内部契约，本地事务 + 显式补偿
- **V6**：生产就绪 —— 契约测试、Feign 超时熔断、网关统一鉴权限流、traceId 与监控、Docker Compose、CI
- **V7**：C 端营销玩法 —— 商品评价、收藏、积分签到、站内消息中心
- **V8**：交易型营销 —— 秒杀（预扣 + Redis 可选降级）、拼团、抽奖，`order_type` 区分订单类型
- **V9**：分销裂变 —— 分销员、邀请绑定、佣金规则与流水、佣金提现
- **V10**：商户入驻（B2B2C）—— 商家独立账号与数据隔离、按商家拆单、平台抽成结算与提现
- **V12**：交易真实化 —— 支付渠道化 SPI、运费模板、发货工作台（部分发货防超发）、发票与红冲、支付对账
- **V14**：能力补齐 —— 商家中心闭环、合规（协议留痕/注销冷静期）、促销引擎（六类玩法）、会员等级、购物车营销、搜索增强、客服工单、消息触达
- **V15**：评价增强（追评/有用数/标签云/晒单墙）、售后扩展（换货/部分退款/平台仲裁）、促销 C 端展示（角标/活动价/倒计时/试算明细）
- **V16**（当前）：在线 AI 客服 —— 新增 `mall-ai`(8210)：ChatOrchestrator 编排（意图→取证→生成→守护）、LlmProvider SPI（openai 兼容/本地 Ollama/mock 三级降级链）、FAQ 检索兜底、只读工具取证（订单/物流/售后经现有 `/inner`）、SSE 流式、确定性转人工（自动建工单+站内信）、后台会话/FAQ/用量看板三页；服务端确定性取数 + LLM 只做话术组织，幻觉与注入双重闸门

## 项目效果展示

四端全部 **74 张实拍截图**（运营后台 / 商家中心 / PC 商城 / 移动端 H5）整理为
[悦集商城 Joy Collection Mall · 项目界面截图集](docs/悦集商城-Joy-Collection-Mall-项目截图.html)，
支持灯箱放大；逐页操作说明见 [功能操作手册](docs/系统功能手册/悦集商城-功能操作手册.html)。

| 经营看板 | 订单列表 | 订单日志 |
| --- | --- | --- |
| ![经营看板](admin-经营看板.png) | ![订单列表](admin-订单列表.png) | ![订单日志](admin-订单日志.png) |

## 测试

项目包含完整的端到端测试：

```bash
# API 冒烟测试
node mall-cloud-fe-smoke.mjs <adminToken>

# 浏览器走查
node mall-cloud-ui-check.mjs <adminToken> <memberToken>
```

## 许可证

MIT License

---

更多详细文档请参阅 [docs/](docs/) 目录。
