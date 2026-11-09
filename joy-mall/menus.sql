-- ============================================================
-- 代码生成器产出的菜单注册 SQL（三级结构：系统 → 业务域 → 页面）
-- 目标工程需存在 sys_menu / sys_role_menu（字段：parent_id,name,path,component,perms,type,sort,status,deleted）
-- 生成时间：2026-10-27 13:06:18
-- 父菜单 ID：0    授权角色 ID：1
-- ============================================================

START TRANSACTION;

-- 【一级】商城后台
SET @dir_sys_mall = (SELECT id FROM sys_menu WHERE parent_id = 0 AND name = '商城后台' AND type = 1 AND deleted = 0 LIMIT 1);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
SELECT 0, '商城后台', '/mall', NULL, NULL, 1, 1, 1, 0 FROM DUAL
WHERE @dir_sys_mall IS NULL;
SET @dir_sys_mall = COALESCE(@dir_sys_mall, LAST_INSERT_ID());

-- 【二级】产品管理
SET @dir_grp_product = (SELECT id FROM sys_menu WHERE parent_id = @dir_sys_mall AND name = '产品管理' AND type = 1 AND deleted = 0 LIMIT 1);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
SELECT @dir_sys_mall, '产品管理', '/mall/product', NULL, NULL, 1, 1, 1, 0 FROM DUAL
WHERE @dir_grp_product IS NULL;
SET @dir_grp_product = COALESCE(@dir_grp_product, LAST_INSERT_ID());

-- 【二级】其他
SET @dir_grp_other = (SELECT id FROM sys_menu WHERE parent_id = @dir_sys_mall AND name = '其他' AND type = 1 AND deleted = 0 LIMIT 1);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
SELECT @dir_sys_mall, '其他', '/mall/other', NULL, NULL, 1, 1, 1, 0 FROM DUAL
WHERE @dir_grp_other IS NULL;
SET @dir_grp_other = COALESCE(@dir_grp_other, LAST_INSERT_ID());

-- 【二级】入库管理
SET @dir_grp_inbound = (SELECT id FROM sys_menu WHERE parent_id = @dir_sys_mall AND name = '入库管理' AND type = 1 AND deleted = 0 LIMIT 1);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
SELECT @dir_sys_mall, '入库管理', '/mall/inbound', NULL, NULL, 1, 1, 1, 0 FROM DUAL
WHERE @dir_grp_inbound IS NULL;
SET @dir_grp_inbound = COALESCE(@dir_grp_inbound, LAST_INSERT_ID());

-- 【二级】订单管理
SET @dir_grp_order = (SELECT id FROM sys_menu WHERE parent_id = @dir_sys_mall AND name = '订单管理' AND type = 1 AND deleted = 0 LIMIT 1);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
SELECT @dir_sys_mall, '订单管理', '/mall/order', NULL, NULL, 1, 1, 1, 0 FROM DUAL
WHERE @dir_grp_order IS NULL;
SET @dir_grp_order = COALESCE(@dir_grp_order, LAST_INSERT_ID());

-- ===== 三级：页面 =====
-- 商城后台 / 产品管理 / 商品类目（mall_category）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_product, '商品类目', '/mall/product/mall_category/list', 'mall_category/index', 'mall_mall_category:list', 2, 1, 1, 0);
SET @menu_mall_category = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_category, '新增', NULL, NULL, 'mall_mall_category:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_category, '修改', NULL, NULL, 'mall_mall_category:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_category, '删除', NULL, NULL, 'mall_mall_category:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_category, 0);

-- 商城后台 / 其他 / 品牌（mall_brand）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_other, '品牌', '/mall/other/mall_brand/list', 'mall_brand/index', 'mall_mall_brand:list', 2, 1, 1, 0);
SET @menu_mall_brand = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_brand, '新增', NULL, NULL, 'mall_mall_brand:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_brand, '修改', NULL, NULL, 'mall_mall_brand:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_brand, '删除', NULL, NULL, 'mall_mall_brand:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_brand, 0);

-- 商城后台 / 产品管理 / 商品（mall_goods）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_product, '商品', '/mall/product/mall_goods/list', 'mall_goods/index', 'mall_mall_goods:list', 2, 1, 1, 0);
SET @menu_mall_goods = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_goods, '新增', NULL, NULL, 'mall_mall_goods:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_goods, '修改', NULL, NULL, 'mall_mall_goods:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_goods, '删除', NULL, NULL, 'mall_mall_goods:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_goods, 0);

-- 商城后台 / 产品管理 / 商品SKU（mall_sku）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_product, '商品SKU', '/mall/product/mall_sku/list', 'mall_sku/index', 'mall_mall_sku:list', 2, 1, 1, 0);
SET @menu_mall_sku = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_sku, '新增', NULL, NULL, 'mall_mall_sku:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_sku, '修改', NULL, NULL, 'mall_mall_sku:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_sku, '删除', NULL, NULL, 'mall_mall_sku:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_sku, 0);

-- 商城后台 / 其他 / 轮播图（mall_banner）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_other, '轮播图', '/mall/other/mall_banner/list', 'mall_banner/index', 'mall_mall_banner:list', 2, 1, 1, 0);
SET @menu_mall_banner = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_banner, '新增', NULL, NULL, 'mall_mall_banner:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_banner, '修改', NULL, NULL, 'mall_mall_banner:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_banner, '删除', NULL, NULL, 'mall_mall_banner:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_banner, 0);

-- 商城后台 / 其他 / 会员（mall_member）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_other, '会员', '/mall/other/mall_member/list', 'mall_member/index', 'mall_mall_member:list', 2, 1, 1, 0);
SET @menu_mall_member = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_member, '新增', NULL, NULL, 'mall_mall_member:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_member, '修改', NULL, NULL, 'mall_mall_member:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_member, '删除', NULL, NULL, 'mall_mall_member:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_member, 0);

-- 商城后台 / 入库管理 / 收货地址（mall_address）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_inbound, '收货地址', '/mall/inbound/mall_address/list', 'mall_address/index', 'mall_mall_address:list', 2, 1, 1, 0);
SET @menu_mall_address = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_address, '新增', NULL, NULL, 'mall_mall_address:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_address, '修改', NULL, NULL, 'mall_mall_address:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_address, '删除', NULL, NULL, 'mall_mall_address:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_address, 0);

-- 商城后台 / 订单管理 / 订单（mall_order）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_order, '订单', '/mall/order/mall_order/list', 'mall_order/index', 'mall_mall_order:list', 2, 1, 1, 0);
SET @menu_mall_order = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_order, '新增', NULL, NULL, 'mall_mall_order:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_order, '修改', NULL, NULL, 'mall_mall_order:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_order, '删除', NULL, NULL, 'mall_mall_order:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_order, 0);

-- 商城后台 / 订单管理 / 订单流转日志（mall_order_log）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_order, '订单流转日志', '/mall/order/mall_order_log/list', 'mall_order_log/index', 'mall_mall_order_log:list', 2, 1, 1, 0);
SET @menu_mall_order_log = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_order_log, '新增', NULL, NULL, 'mall_mall_order_log:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_order_log, '修改', NULL, NULL, 'mall_mall_order_log:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_order_log, '删除', NULL, NULL, 'mall_mall_order_log:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_order_log, 0);

-- 商城后台 / 其他 / 售后退款单（mall_refund）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_other, '售后退款单', '/mall/other/mall_refund/list', 'mall_refund/index', 'mall_mall_refund:list', 2, 1, 1, 0);
SET @menu_mall_refund = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_refund, '新增', NULL, NULL, 'mall_mall_refund:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_refund, '修改', NULL, NULL, 'mall_mall_refund:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_refund, '删除', NULL, NULL, 'mall_mall_refund:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_refund, 0);

-- 商城后台 / 其他 / 优惠券（mall_coupon）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_other, '优惠券', '/mall/other/mall_coupon/list', 'mall_coupon/index', 'mall_mall_coupon:list', 2, 1, 1, 0);
SET @menu_mall_coupon = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_coupon, '新增', NULL, NULL, 'mall_mall_coupon:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_coupon, '修改', NULL, NULL, 'mall_mall_coupon:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_coupon, '删除', NULL, NULL, 'mall_mall_coupon:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_coupon, 0);

-- 商城后台 / 其他 / 领券记录（mall_coupon_record）
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@dir_grp_other, '领券记录', '/mall/other/mall_coupon_record/list', 'mall_coupon_record/index', 'mall_mall_coupon_record:list', 2, 1, 1, 0);
SET @menu_mall_coupon_record = LAST_INSERT_ID();
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_coupon_record, '新增', NULL, NULL, 'mall_mall_coupon_record:add', 3, 1, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_coupon_record, '修改', NULL, NULL, 'mall_mall_coupon_record:edit', 3, 2, 1, 0);
INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (@menu_mall_coupon_record, '删除', NULL, NULL, 'mall_mall_coupon_record:remove', 3, 3, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (1, @menu_mall_coupon_record, 0);

COMMIT;
