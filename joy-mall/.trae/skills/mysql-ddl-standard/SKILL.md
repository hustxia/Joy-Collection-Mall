---
name: mysql-ddl-standard
description: MySQL 建表/改表 SQL 的统一规范与模板。当用户要求建表、写 DDL、CREATE TABLE、ALTER TABLE 加字段、设计表结构，或处于 S2 数据模型阶段需要产出可执行 SQL 时使用。触发词：建表、DDL、表结构、加字段、加索引、create table、alter table。适用于本机所有 Spring Boot + MySQL 项目（mall、CrossShop 等），WorkBuddy 与 Trae 共用同一份。
---

# MySQL 建表 SQL 规范

**一句话：每一张表都必须幂等、带审计列、带逻辑删除、每列有注释，照模板抄，不自由发挥。**

本规范从真实项目提炼（mall 骨架 `init_sys_user.sql`、CrossShop 44 张业务表、`alter-shop.sql` 补列实录），
项目特有差异（如表前缀）以项目内已有表为准，本文件只管通用骨架。

***

## 一、铁律（违反任何一条，DDL 不许交付）

1. **幂等可重入**：建表一律 `CREATE TABLE IF NOT EXISTS`，种子数据一律 `INSERT IGNORE`。同一脚本反复执行不许报错、不许产生重复数据。
2. **审计六件套**：带业务含义的表必须含 `create_time / update_time / create_by / update_by / deleted`，缺 `deleted` 是真实踩过的坑（平台生成器漏生成，7 张表手工补列）。
3. **每列必有 COMMENT**：枚举/状态列的取值必须写进注释（如 `'状态：1启用0停用'`），不许只写「状态」。
4. **表必有 COMMENT**：表选项 `COMMENT='中文表意'` 不许省略。
5. **统一表选项**：`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`，不写其他引擎、不写 utf8。
6. **改表是破坏性操作**：`ALTER TABLE` / `DROP` / 批量 UPDATE 执行前必须说明影响面并取得用户明确同意（与 fullstack-dev 铁律一致）。

## 二、表名与模块前缀

- 小写下划线，模块前缀 + 业务名：`<前缀>_<名词>`，名词用单数。
- 新表前缀**跟项目已有表走**，不许发明新前缀。已知项目前缀对照：

| 前缀      | 模块          | 示例                                                  |
| ------- | ----------- | --------------------------------------------------- |
| `sys_`  | 系统/平台基座     | sys\_user, sys\_role, sys\_menu, sys\_flow\_def     |
| `pms_`  | 商品          | pms\_spu, pms\_sku, pms\_brand, pms\_category       |
| `ums_`  | 会员          | ums\_member, ums\_member\_level                     |
| `oms_`  | 订单          | oms\_order, oms\_order\_item, oms\_order\_log       |
| `pay_`  | 支付          | pay\_payment, pay\_refund, pay\_settlement          |
| `mkt_`  | 营销          | mkt\_coupon, mkt\_activity                          |
| `inv_`  | 库存          | inv\_stock, inv\_stock\_log                         |
| `lgs_`  | 物流          | lgs\_waybill, lgs\_channel                          |
| `pur_`  | 采购          | pur\_supplier, pur\_order                           |
| `cb_`   | 跨境          | cb\_site, cb\_customs, cb\_tax\_rate                |
| `mall_` | mall 项目自有业务 | mall\_order, mall\_refund（mall 项目不用 oms\_/pay\_ 前缀） |

- 关联表：`<主表>_<从表>`，联合主键（如 `sys_user_role (user_id, role_id)`），关联表可不带审计列。

## 三、公共列模板（审计六件套）

```sql
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by bigint NULL COMMENT '创建人',
  update_by bigint NULL COMMENT '更新人',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
```

- 骨架自动赋值 id 的场景（如雪花 ID、流程表）允许 `bigint NOT NULL` 不自增，参照 `sys_flow_def`。
- 纯日志表（sys\_login\_log）允许只带 `create_time`；**业务表不允许省略六件套**。
- `deleted` 必须存在——实体继承 BaseEntity 的表都要，漏列会导致查询报错或脏数据（项目实录）。

## 四、列类型速查

| 用途       | 类型                           | 说明                                         |
| -------- | ---------------------------- | ------------------------------------------ |
| 主键/外键 ID | `bigint`                     | 不用 int、不用 varchar 存 ID                     |
| 状态/布尔/枚举 | `tinyint`                    | 注释列全取值：`1启用0停用`；不用 boolean、不用 varchar      |
| 金额       | `decimal(18,2)`              | **禁止 float/double**；大金额用 (18,2)，单价可 (10,2) |
| 数量       | `int`                        | 库存/次数等                                     |
| 短文本      | `varchar(64)`                | 名称类 64，编码类 64，备注 255，URI 128               |
| 长文本      | `text` / `mediumtext`        | 参数/结果用 text；XML/富文本用 mediumtext            |
| 时间       | `datetime`                   | 不用 timestamp（时区坑）；业务时间允许 NULL              |
| 逻辑删除     | `tinyint NOT NULL DEFAULT 0` | 固定写法                                       |

- 可空约定：业务上允许为空的列显式写 `NULL`；状态、删除标记、排序号必须 `NOT NULL DEFAULT`。
- 字符串长度按真实上限取 2 的幂附近值（16/32/64/128/255），不写 varchar(37) 这种随意值。

## 五、索引与约束命名

- 主键：`PRIMARY KEY (id)`。
- 唯一键：`UNIQUE KEY uk_<列> (<列>)`；**带逻辑删除的表，唯一键必须带上 deleted**，否则删过的记录挡住重建：
  `UNIQUE KEY uk_flow_code (flow_code, deleted)`（sys\_flow\_def 实录）。
- 普通索引：`KEY idx_<表意>_<列> (<列>)`，给高频查询条件建（时间、外键、状态），如 `idx_login_log_time (create_time)`。
- 组合索引遵守最左前缀；不为低基数列（status/deleted）单独建索引。
- **不建外键约束**，关联一致性由应用层保证。

## 六、标准模板

### 6.1 业务表

```sql
CREATE TABLE IF NOT EXISTS oms_order (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no varchar(64) NOT NULL COMMENT '订单号',
  member_id bigint NOT NULL COMMENT '会员ID',
  total_amount decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '订单总额',
  status tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待支付1待发货2已发货3已完成4已取消',
  remark varchar(255) NULL COMMENT '备注',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by bigint NULL COMMENT '创建人',
  update_by bigint NULL COMMENT '更新人',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no, deleted),
  KEY idx_order_member (member_id),
  KEY idx_order_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';
```

### 6.2 关联表

```sql
CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id bigint NOT NULL COMMENT '用户ID',
  role_id bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';
```

### 6.3 改表（补列必须带 COMMENT + AFTER 定位）

```sql
ALTER TABLE `oms_order`
  ADD COLUMN `create_by` BIGINT NULL COMMENT '创建人' AFTER `update_time`,
  ADD COLUMN `update_by` BIGINT NULL COMMENT '更新人' AFTER `create_by`;
```

### 6.4 种子数据

```sql
INSERT IGNORE INTO sys_role (id, role_name, role_key, remark)
VALUES (1, '超级管理员', 'admin', '内置角色：接口权限点直通');
```

- 种子必须能被 `INSERT IGNORE` 或唯一键挡住重复；依赖其他表的种子用 `SELECT ... WHERE` 幂等写法，参照 init\_sys\_user.sql 末尾。

## 七、交付前自查清单（逐项过，打勾才算完）

- [ ] 所有 `CREATE TABLE` 带 `IF NOT EXISTS`，脚本可重复执行
- [ ] 业务表审计六件套齐全（重点核对 `deleted`，生成器产物最容易漏）
- [ ] 每列有 COMMENT；状态/枚举列注释列出全部取值
- [ ] 表有中文 COMMENT；ENGINE=InnoDB + utf8mb4
- [ ] 金额 decimal、状态 tinyint、时间 datetime、ID bigint——无 float/timestamp/int 主键
- [ ] 唯一键带 deleted；普通索引只建在高频查询列
- [ ] 字段与后端实体逐字段核对过（名称、类型、可空性一致）
- [ ] SQL 已在本地库真实执行过，`SHOW CREATE TABLE` / `COUNT(*)` 有输出作证据
- [ ] ALTER 类操作执行前已告知影响面并取得用户同意

## 八、禁忌

- 不许用 `DROP TABLE` 开头「保证干净」——数据丢失不可逆，测试库也不许。
- 不许不带 COMMENT 交付，「后面补注释」= 没做完。
- 不许发明新模块前缀、新公共列名（如 gmt\_create），与项目已有表不一致的命名一律驳回。
- 不许把建表 SQL 只贴在聊天里——落盘到项目 `db/` 或设计文档，随代码一起管理。
- 不许在业务表用物理删除约定，删除一律走 `deleted` 逻辑删除。

