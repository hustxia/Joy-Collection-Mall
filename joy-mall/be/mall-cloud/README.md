# mall-cloud —— 越际商城微服务化拆分工程

原单体后端（`be/mall`，单进程、单端口 8080）按**实际业务域**拆成 6 个可独立部署的服务 + 1 个网关 + 1 个公共库。
**数据库仍为单库 `yueji_mall`**，按表归属做逻辑拆分（不跨服务写别人的表）。

## 1. 域划分与端口

| 模块 | 业务域 | 端口 | 光表的归属（写权限） |
|---|---|---|---|
| `mall-system` | 平台支撑：登录鉴权、用户/角色/菜单、字典、文件/上传、工作流、看板 | 8201 | `sys_*`、`gen_*`、`flow_*` |
| `mall-goods` | 商品域：SPU/SKU、分类、品牌、轮播 | 8202 | `mall_goods`、`mall_sku`、`mall_category`、`mall_brand`、`mall_banner` |
| `mall-member` | 会员域：会员、地址、购物车、足迹 | 8203 | `mall_member`、`mall_address`、`mall_cart`、`mall_footprint` |
| `mall-order` | 交易域：下单、订单/明细/日志、支付回调、超时关单 | 8204 | `mall_order`、`mall_order_item`、`mall_order_log` |
| `mall-marketing` | 营销域：优惠券模板、领券记录、核销/释放 | 8205 | `mall_coupon`、`mall_coupon_record` |
| `mall-refund` | 售后域：退款单、售后状态机、库存回补发起 | 8206 | `mall_refund`、`mall_refund_log` |
| `mall-gateway` | Spring Cloud Gateway 统一入口 | 8080 | —（只转发） |
| `mall-common` | 公共库（非可执行）：`Result`、`AuthContext`、`LoginUser`、`AuthContextFilter`、`GlobalExceptionHandler`、`MybatisPlusConfig`、`LogAspect`、`DictTexts`、**Feign 契约与 DTO** | — | — |

端口分配原则：820x 顺序对应前端菜单顺序，网关独占 8080，与单体时代保持同一对外入口，前端零改动。

## 2. 工程结构

```
mall-cloud/
├── pom.xml                  父 POM（packaging=pom），统一 Spring Boot 3.2.5 + Spring Cloud 2023.0.3
├── mall-common/             普通 jar（不 repackage），被 6 个服务依赖
│   └── com/mall/common/
│       ├── feign/           ★ 跨域契约：XxxInnerClient + feign/dto/*Brief
│       ├── auth/            AuthContext / AuthContextFilter / TokenStore
│       ├── config/          MybatisPlusConfig / FeignConfig
│       └── ...
├── mall-system/  mall-goods/  mall-member/
├── mall-order/   mall-marketing/  mall-refund/
│   └── com/mall/
│       ├── <domain>/XxxApplication.java   ← 启动类（scanBasePackages = "com.mall"）
│       ├── controller/                     ← 沿用单体包名，公共控制器零改动搬入
│       ├── controller/inner/InnerXxxController.java  ★ 仅供内部 RPC
│       ├── controller/shop/ShopXxxController.java    ← C 端接口（改写为 Feign 编排）
│       ├── service/  mapper/  entity/  vo/
│       └── resources/application.yml      ← 含 feign.clients 静态地址
└── mall-gateway/
    └── resources/application.yml          ← 按真实 @RequestMapping 前缀路由
```

## 3. 服务间调用约定

### 3.1 Feign 契约住在 `mall-common`
调用方只依赖 `com.mall.common.feign.XxxInnerClient` 接口 + `feign/dto` 里的 `*Brief`，**不 import 任何别的服务域代码**（拆分验收的硬标准）。

```java
@FeignClient(name = "mall-goods", url = "${feign.clients.mall-goods:http://localhost:8202}")
public interface GoodsInnerClient {
    @GetMapping("/inner/sku/by-ids")
    List<SkuBrief> skuByIds(@RequestParam("ids") Collection<Long> ids);
    ...
}
```

- 地址走配置，不引注册中心：`application.yml` 里 `feign.clients.mall-goods: http://localhost:8202`。
  未来接 Nacos 只需删 `url` 属性，接口签名不动。
- 现存的调用方向（8 条，均已实测打通）：

| 调用方 | 被调方 | 场景 |
|---|---|---|
| order | goods | 下单校验 SKU/商品、扣库存、取消/关单回补、支付加销量 |
| order | member | 收货地址、勾选的购物车、会员信息、清购物车 |
| order | marketing | 可用券列表、锁券、核销、释放 |
| order | refund | 订单详情展示最新退款单 |
| refund | order | 售后前校验订单状态、读明细、写订单日志 |
| refund | member | 售后列表批量补会员昵称/手机号 |
| refund | goods | 同意退货后回补库存、回退销量 |
| member | goods | 购物车列表补商品名/图/价/库存/上下架状态 |

### 3.2 `/inner/**` 内部端点清单

| 服务 | 端点 | 说明 |
|---|---|---|
| goods | `GET /inner/sku/by-ids` `GET /inner/goods/by-ids` | 批量查，返回 `List<SkuBrief>` / `List<GoodsBrief>` |
| goods | `POST /inner/sku/deduct` `POST /inner/sku/restore` `POST /inner/sku/sales` `POST /inner/goods/sales` | 扣减/回补库存、加销量（销量用 `GREATEST(x+?,0)`，负数可用于退款回退） |
| member | `GET /inner/address/{id}` `GET /inner/cart` `GET /inner/member/{id}` `GET /inner/member/by-ids` `POST /inner/cart/clear` | 地址、勾选购物车、会员、批量会员、清购物车 |
| marketing | `GET /inner/coupon-record` `GET /inner/coupon/by-ids` `POST /inner/coupon-record/lock` `POST /inner/coupon-record/use-by-order` `POST /inner/coupon-record/release-by-order` | 可用券、券模板、锁券（条件 UPDATE 抢占）、核销、按订单释放 |
| order | `GET /inner/order/{id}` `GET /inner/order-item` `GET /inner/order-log` `POST /inner/order-log` | 订单/明细/日志读，日志追加 |
| refund | `GET /inner/by-order-ids` `GET /inner/latest-by-order/{orderId}` | 批量退款单、订单最新退款单 |

**`/inner` 的三条硬规则**（都是踩过的坑，改动前先读）：

1. **必须返回裸对象**，不能包 `Result<T>`。Feign 接口声明的是 `SkuBrief`，服务端返 `Result` 会直接反序列化 500。
2. **`AuthContextFilter` 已白名单 `/inner/**`**，`FeignConfig` 在 `template.path()` 以 `/inner` 开头时**不转发 `Authorization`**。
   否则 C 端会员 token 会被商品服务的鉴权拦成 403（会员 token 不属于后台用户域）。
3. **网关不暴露 `/inner/**`**，它只允许集群内网访问。

## 4. 构建与运行

```bash
# 构建（9 模块 reactor 一次出 8 个 jar；mall-common 不 repackage）
cd mall-cloud
mvn -o -DskipTests clean package

# 起服务（顺序无关，Feign 是延迟连接；但业务联调建议 system → goods/member/marketing → order → refund）
java -jar mall-system/target/mall-system-1.0.0.jar
java -jar mall-goods/target/mall-goods-1.0.0.jar
java -jar mall-member/target/mall-member-1.0.0.jar
java -jar mall-marketing/target/mall-marketing-1.0.0.jar
java -jar mall-order/target/mall-order-1.0.0.jar
java -jar mall-refund/target/mall-refund-1.0.0.jar
java -jar mall-gateway/target/mall-gateway-1.0.0.jar

# 前端仍打 8080，无需改动
curl http://localhost:8080/shop/goods?page=1&size=2
```

## 5. 环境与编译约束（不满足会运行时炸）

| 项 | 要求 | 原因 |
|---|---|---|
| JDK | 21 | Spring Boot 3.2.5 基线 |
| 父 POM 编译插件 | 必须 `maven-compiler-plugin:3.13.0` + `<parameters>true</parameters>` **且** `<compilerArgs><arg>-parameters</arg></compilerArgs>` | 本工程**未继承** `spring-boot-starter-parent`，缺 `-parameters` 时 `@RequestParam("x")` 之外的参数名解析失败：`IllegalArgumentException: Name for argument of type [long] not specified` |
| 启动类扫描 | `@SpringBootApplication(scanBasePackages = "com.mall")` | 公共 Bean 在 `com.mall.common.*`、控制器在 `com.mall.controller.*`，只扫 `com.mall.<domain>` 会导致 `/inner/*` 404 |
| Feign 客户端扫描 | `@EnableFeignClients(basePackages = "com.mall.common.feign")` | 契约接口不在启动类所在包下 |
| 数据库 | MySQL `yueji_mall`，`root/root`，3306 | 单库逻辑拆分，各服务同一数据源 |
| 鉴权 | `TokenStore` 落库 `sys_session`（60s 内存缓存） | 会员 token 由 member 签发、order 校验，必须跨进程可见 |

## 6. 头表 / 事务边界说明

- 跨域操作**没有分布式事务**，采用「本地事务 + 显式补偿」：
  - 下单：order 本地写单 → Feign 扣库存/锁券；扣减失败抛异常回滚本地事务，已扣部分按顺序 `restoreStock` / `releaseByOrder` 反向补偿。
  - 支付：order 改状态成功后依次 `incrementSkuSales`、`incrementGoodsSales`、`useByOrder`，每步幂等或可容忍重复（销量用增量、券用条件 UPDATE）。
  - 售后：refund 改售后单 → `restoreStock` → 回退销量 → `appendLog` 到 order。
- 这是**有意的取舍**：目标是先拿到「服务边界清晰 + 可独立部署」，一致性升级（本地消息表 / Seata / 事务消息）留到下一阶段。

## 7. 与单体工程的关系

| | 单体 `be/mall` | 微服务 `be/mall-cloud` |
|---|---|---|
| 端口 | 8080（Tomcat 直出） | 8080 网关 + 8201~8206 六服务 |
| 前端 | 打 8080 | 不变，仍打 8080 |
| 跨域调用 | 同进程 Spring 注入 | OpenFeign + `/inner` REST |
| 事务 | 单 `@Transactional` | 本地事务 + 补偿 |
| 状态 | 保留可回退的参照实现 | 主开发线 |

单体的 `ShopOrderController`（934 行、一方法跨 6 张域表）在微服务版被拆开：读别人的数据一律走 Feign，`JdbcTemplate` 只用于改自己的 `mall_order` 状态。

## 8. 已验证的端到端链路（实测记录）

网关 8080 + 6 服务全起、MySQL 3306：

- `GET /shop/goods`、`/shop/banners`、`/shop/categories` → 200 真实数据
- 注册会员 → `/shop/order/confirm`（Feign 解析 SKU/商品、可用券为空）→ 建地址 → `/shop/order/submit`（库存 490→488）
- `/shop/pay/mock` → 订单状态 20，销量 goods 9930→9932 / sku 6210→6212
- `/shop/order/page` + 详情 → 明细/日志正确（`提交订单，实付 39800 分`、`支付成功（mock 渠道）…`）
- 第二单 → `/shop/order/cancel` → 库存回补 487→488，日志 `取消订单，库存已回补`，状态 50
- `/shop/refund/order/{id}` → `canApply:false`（refund→order Feign 生效）
- `/shop/cart/add` + `/shop/cart/list` → 商品名/图/价/库存/状态齐全（member→goods Feign 生效）

## 9. 前端联调（三端，2026-10-31 实测）

三端 devServer 代理目标已改为环境变量可覆盖（**默认已指网关 8080**，单体已删除）：

```bash
# 后台 9431 / PC 前台 9432 / H5 9531
cd fe/mall-web && MALL_API_TARGET=http://127.0.0.1:8080 npm run dev
cd fe/mall-pc  && MALL_API_TARGET=http://127.0.0.1:8080 npm run dev
cd fe/mall-shop && MALL_API_TARGET=http://127.0.0.1:8080 npm run dev   # vite 只绑 ::1，探活用 localhost
```

代理改动点：`mall-web/vue.config.js`、`mall-pc/vue.config.js` 用 `MALL_API_TARGET || VUE_APP_PROXY_TARGET || 8080`；`mall-shop/vite.config.js` 用 `MALL_API_TARGET || 8080`。前端其余代码零改动。

**上传目录**：默认 `${FILE_UPLOAD_DIR:./uploads}`，在 `be/mall-cloud` 目录下启动即为 `be/mall-cloud/uploads`（图片与 `.thumbs` 缓存都在这里，已从旧单体迁入）。**务必在 `be/mall-cloud` 目录下启动**，否则相对路径落到别处会让 `/uploads/**`、`/thumb` 全部 404（见坑库「前端三端联调」#3）：

```bash
cd be/mall-cloud
java -jar mall-system/target/mall-system-1.0.0.jar --server.port=8201
```

**联调冒烟脚本**（mall 根目录，走 devServer 代理 = 前端真实请求路径）：

```bash
# API 链路 51 断言：登录→商品→购物车→下单→发货→收货→售后→审核→退款→后台 12 台账+看板
node mall-cloud-fe-smoke.mjs <adminToken>

# 浏览器走查 136 断言：后台 14 页 + PC 6 页 + H5 6 页，console error / 失败请求 / 破图 / 截图
node mall-cloud-ui-check.mjs <adminToken> <memberToken>
```

admin token 可直插 `sys_session` 生成；member token 用演示账号 `13800000001 / 123456` 登录 `/shop/auth/login` 获取。

**联调当轮发现并修复的问题**（均已验证）：

| 问题 | 修复 |
|---|---|
| 后台/PC/H5 共 9 处模板调用 `thumb()` 报 `_vm.thumb is not a function`，商品列表整表渲染为空 | 三端入口挂 `Vue.prototype.thumb` / `app.config.globalProperties.thumb`；单页显式挂 methods |
| 后台首页 4 张指标卡恒为 0 | `toPageData` 包装后取 `d.data.total`（原代码取 `d.total`） |
| 购物车第二次删除同一 SKU 500（`uk_member_sku` 含 `deleted`，软删撞唯一键） | 购物车改物理删除（单行 + 批量清空两路），单体同款缺陷一并修复 |
| 微服务下历史上传图片全破图（200+JSON 伪 200） | `file.upload-dir` 支持 `FILE_UPLOAD_DIR` 覆盖，联调期指向单体共享目录 |
| 后台首页「连通检测」`/health` 404 | 网关 system 路由加 `/health/**` + 新增 `HealthController` |

**实测结果**：API 冒烟 51/51、浏览器走查 136/136（截图在 `shots/mall-cloud/`）；真实登录（算术验证码 `/auth/captcha` + `/auth/login`）、两端文件上传（`/file/upload`、`/shop/upload`）与回读均通过。
