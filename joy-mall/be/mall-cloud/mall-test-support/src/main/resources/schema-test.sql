-- 商城测试库结构（由 yueji_mall 结构导出，去掉库内注释以保持可读）
-- 用法：spring.sql.init.schema-locations=classpath:schema-test.sql，profile=test
-- 约定：只 CREATE TABLE IF NOT EXISTS，不 DROP、不 TRUNCATE，绝不触碰业务库（库名守卫见 TestDbNameGuard）

CREATE TABLE IF NOT EXISTS `mall_address` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `receiver_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `receiver_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `province` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `city` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `district` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `detail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2116359465985908739 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收货地址';
CREATE TABLE IF NOT EXISTS `mall_banner` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `link_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `position` tinyint NOT NULL DEFAULT '1',
  `sort` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='轮播图';
CREATE TABLE IF NOT EXISTS `mall_brand` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `intro` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sort` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='品牌';
CREATE TABLE IF NOT EXISTS `mall_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  `num` int NOT NULL DEFAULT '1',
  `checked` tinyint NOT NULL DEFAULT '1',
  `remark` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_sku` (`member_id`,`sku_id`,`deleted`),
  KEY `idx_member` (`member_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2116376470910685187 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车';
CREATE TABLE IF NOT EXISTS `mall_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT '0',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `level` tinyint NOT NULL DEFAULT '1',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sort` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=208 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品类目';
CREATE TABLE IF NOT EXISTS `mall_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `type` tinyint NOT NULL DEFAULT '1',
  `amount` bigint NOT NULL DEFAULT '0',
  `discount_rate` int DEFAULT NULL,
  `threshold_amount` bigint NOT NULL DEFAULT '0',
  `total_count` int NOT NULL DEFAULT '0',
  `received_count` int NOT NULL DEFAULT '0',
  `per_limit` int NOT NULL DEFAULT '1',
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='优惠券';
CREATE TABLE IF NOT EXISTS `mall_coupon_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `coupon_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `received_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `used_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_coupon` (`coupon_id`),
  KEY `idx_member` (`member_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2115642241289568259 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='领券记录';
CREATE TABLE IF NOT EXISTS `mall_goods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `category_id` bigint NOT NULL,
  `brand_id` bigint DEFAULT NULL,
  `merchant_id` bigint NOT NULL DEFAULT '1',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `sub_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `main_image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `slider_images` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  `price_min` bigint NOT NULL DEFAULT '0',
  `price_max` bigint NOT NULL DEFAULT '0',
  `sales` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_no` (`goods_no`),
  KEY `idx_category` (`category_id`),
  KEY `idx_brand` (`brand_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2116334914212270083 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品';
CREATE TABLE IF NOT EXISTS `mall_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `password` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `password_algo` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'bcrypt',
  `openid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `unionid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gender` tinyint NOT NULL DEFAULT '0',
  `source` tinyint NOT NULL DEFAULT '1',
  `points` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `last_login_time` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`,`deleted`),
  KEY `idx_phone` (`phone`),
  KEY `idx_openid` (`openid`)
) ENGINE=InnoDB AUTO_INCREMENT=2116357224474419203 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会员';
CREATE TABLE IF NOT EXISTS `mall_member_footprint` (
  `id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `goods_id` bigint NOT NULL,
  `view_time` datetime NOT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_goods` (`member_id`,`goods_id`),
  KEY `idx_member_view` (`member_id`,`view_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员商品足迹';
CREATE TABLE IF NOT EXISTS `mall_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `member_id` bigint NOT NULL,
  `merchant_id` bigint NOT NULL DEFAULT '1',
  `total_amount` bigint NOT NULL DEFAULT '0',
  `freight_amount` bigint NOT NULL DEFAULT '0',
  `discount_amount` bigint NOT NULL DEFAULT '0',
  `pay_amount` bigint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '10',
  `pay_status` tinyint NOT NULL DEFAULT '0',
  `pay_type` tinyint DEFAULT NULL,
  `pay_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `pay_time` datetime DEFAULT NULL,
  `receiver_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `receiver_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `receiver_address` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `express_company` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `express_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `deliver_time` datetime DEFAULT NULL,
  `finish_time` datetime DEFAULT NULL,
  `buyer_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `close_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_member` (`member_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2116376485607567363 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单';
CREATE TABLE IF NOT EXISTS `mall_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `merchant_id` bigint NOT NULL DEFAULT '1',
  `goods_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  `goods_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `sku_image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `spec_json` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `price` bigint NOT NULL DEFAULT '0',
  `quantity` int NOT NULL DEFAULT '1',
  `total_amount` bigint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2116376485678870530 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单明细';
CREATE TABLE IF NOT EXISTS `mall_order_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `operator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator_type` tinyint NOT NULL DEFAULT '3',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2116376498840596483 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单流转日志';
CREATE TABLE IF NOT EXISTS `mall_refund` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `refund_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `order_id` bigint NOT NULL,
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `member_id` bigint NOT NULL,
  `type` tinyint NOT NULL DEFAULT '1',
  `amount` bigint NOT NULL DEFAULT '0',
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `evidence_images` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `audit_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `refund_time` datetime DEFAULT NULL,
  `pay_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2116376496449822723 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='售后退款单';
CREATE TABLE IF NOT EXISTS `mall_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_id` bigint NOT NULL,
  `sku_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `spec_json` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `price` bigint NOT NULL DEFAULT '0',
  `stock` int NOT NULL DEFAULT '0',
  `sales` int NOT NULL DEFAULT '0',
  `image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_goods` (`goods_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2116334914279378949 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品SKU';
CREATE TABLE IF NOT EXISTS `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_type` varchar(64) NOT NULL,
  `label` varchar(100) NOT NULL,
  `value` varchar(100) NOT NULL,
  `sort` int NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统字典数据';
CREATE TABLE IF NOT EXISTS `sys_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `original_name` varchar(255) NOT NULL,
  `storage_name` varchar(128) NOT NULL,
  `relative_path` varchar(255) NOT NULL,
  `url` varchar(512) NOT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `file_size` bigint NOT NULL DEFAULT '0',
  `md5` char(32) DEFAULT NULL,
  `biz_type` varchar(64) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_relative_path` (`relative_path`)
) ENGINE=InnoDB AUTO_INCREMENT=2116369942392659971 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统文件';
CREATE TABLE IF NOT EXISTS `sys_flow_def` (
  `id` bigint NOT NULL,
  `flow_code` varchar(64) NOT NULL,
  `flow_name` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `status_col` varchar(64) NOT NULL DEFAULT 'status',
  `nodes` varchar(1024) NOT NULL,
  `bpmn_xml` mediumtext,
  `bpmn_hash` varchar(64) DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `remark` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_code` (`flow_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='娴佺▼瀹氫箟';
CREATE TABLE IF NOT EXISTS `sys_flow_task` (
  `id` bigint NOT NULL,
  `flow_code` varchar(64) NOT NULL,
  `biz_table` varchar(64) NOT NULL,
  `biz_id` bigint NOT NULL,
  `from_status` varchar(20) DEFAULT NULL,
  `to_status` varchar(20) DEFAULT NULL,
  `action` varchar(20) NOT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `oper_user_id` bigint DEFAULT NULL,
  `oper_name` varchar(64) DEFAULT NULL,
  `oper_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_flow_biz` (`flow_code`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='瀹℃壒娴佹按';
CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `region` varchar(128) DEFAULT NULL,
  `success` tinyint NOT NULL DEFAULT '0',
  `fail_reason` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_login_log_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐧诲綍鏃ュ織锛圴6.4锛';
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT '0',
  `name` varchar(64) NOT NULL,
  `path` varchar(128) DEFAULT NULL,
  `component` varchar(128) DEFAULT NULL,
  `perms` varchar(128) DEFAULT NULL,
  `type` tinyint NOT NULL DEFAULT '1',
  `sort` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=69 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鑿滃崟涓庢潈闄愮偣';
CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(64) DEFAULT NULL,
  `oper_type` varchar(32) DEFAULT NULL,
  `method` varchar(128) DEFAULT NULL,
  `request_uri` varchar(128) DEFAULT NULL,
  `request_method` varchar(16) DEFAULT NULL,
  `oper_param` text,
  `json_result` text,
  `status` tinyint DEFAULT NULL,
  `error_msg` varchar(512) DEFAULT NULL,
  `oper_user_id` bigint DEFAULT NULL,
  `oper_name` varchar(64) DEFAULT NULL,
  `oper_ip` varchar(64) DEFAULT NULL,
  `province` varchar(32) DEFAULT NULL,
  `city` varchar(32) DEFAULT NULL,
  `cost_ms` bigint DEFAULT NULL,
  `oper_time` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_oper_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鎿嶄綔鏃ュ織锛圴6.4 琛ラ綈绉嶅瓙锛';
-- V6.4 老库补列：sys_oper_log.oper_time（实体 OpeLog.operTime 与 LogAspect 写入均依赖）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log' AND COLUMN_NAME = 'oper_time');
SET @ddl := IF(@c = 0, 'ALTER TABLE `sys_oper_log` ADD COLUMN `oper_time` datetime DEFAULT NULL COMMENT ''操作时间'' AFTER `cost_ms`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(64) NOT NULL,
  `role_key` varchar(64) NOT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绯荤粺瑙掕壊';
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='瑙掕壊鑿滃崟锛坧erms 闅忚彍鍗曟巿浜堬級';
CREATE TABLE IF NOT EXISTS `sys_session` (
  `token` varchar(64) NOT NULL,
  `username` varchar(64) NOT NULL,
  `login_ip` varchar(64) DEFAULT NULL,
  `login_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expire_time` datetime NOT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`token`),
  KEY `idx_session_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐧诲綍浼氳瘽锛圴6.4 鎸佷箙鍖栵紝閲嶅惎涓嶆帀绾匡級';
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `password` varchar(72) NOT NULL,
  `password_algo` varchar(16) NOT NULL DEFAULT 'bcrypt',
  `fail_count` int NOT NULL DEFAULT '0',
  `lock_until` datetime DEFAULT NULL,
  `nickname` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绯荤粺鐢ㄦ埛';
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐢ㄦ埛瑙掕壊鍏宠仈';

-- ============================================================
-- V7 C 端营销玩法（与 db/v7-engagement.sql 保持一致；测试库只建表不灌种子）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_item_id` bigint NOT NULL COMMENT '订单项ID',
  `goods_id` bigint NOT NULL COMMENT '商品ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `member_nickname` varchar(64) NOT NULL DEFAULT '' COMMENT '会员昵称（冗余，免联查）',
  `rating` tinyint NOT NULL COMMENT '评分：1~5',
  `content` varchar(500) NOT NULL DEFAULT '' COMMENT '评价内容',
  `images` varchar(1000) NOT NULL DEFAULT '' COMMENT '评价图片，逗号分隔',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0正常1后台隐藏',
  `reply` varchar(500) NULL COMMENT '后台回复',
  `reply_time` datetime NULL COMMENT '回复时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_item` (`order_item_id`, `deleted`),
  KEY `idx_review_goods` (`goods_id`, `status`),
  KEY `idx_review_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价';

CREATE TABLE IF NOT EXISTS `mall_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `goods_id` bigint NOT NULL COMMENT '商品ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_member_goods` (`member_id`, `goods_id`, `deleted`),
  KEY `idx_favorite_goods` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏';

CREATE TABLE IF NOT EXISTS `mall_member_point_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `change_type` tinyint NOT NULL COMMENT '变动类型：1消费返2签到3下单抵扣4退款扣回',
  `points` int NOT NULL COMMENT '变动积分：正加负减',
  `balance_after` int NOT NULL COMMENT '变动后余额',
  `order_id` bigint NULL COMMENT '关联订单ID',
  `remark` varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_point_log_member` (`member_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员积分流水';

CREATE TABLE IF NOT EXISTS `mall_sign_in` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `sign_date` date NOT NULL COMMENT '签到日期',
  `continuous_days` int NOT NULL DEFAULT 1 COMMENT '当日连续天数',
  `points` int NOT NULL COMMENT '本次赠送积分',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sign_member_date` (`member_id`, `sign_date`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员每日签到';

CREATE TABLE IF NOT EXISTS `mall_notice` (
  `id` bigint NOT NULL COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `type` tinyint NOT NULL COMMENT '类型：1订单2售后3营销',
  `title` varchar(64) NOT NULL COMMENT '标题',
  `content` varchar(255) NOT NULL DEFAULT '' COMMENT '内容',
  `biz_id` varchar(64) NOT NULL DEFAULT '' COMMENT '关联业务号（订单号/售后单号）',
  `read_status` tinyint NOT NULL DEFAULT 0 COMMENT '读取状态：0未读1已读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_notice_member_read` (`member_id`, `read_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

-- ============================================================
-- V8 交易型营销玩法（与 db/v8-promo.sql 保持一致；测试库只建表不灌种子）
-- ============================================================
-- 订单类型标记（0普通1秒杀2拼团3抽奖实物）；H2 兼容写法，不存在才加
SET @col_cnt := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'mall_order' AND COLUMN_NAME = 'ORDER_TYPE');
SET @ddl := IF(@col_cnt = 0, 'ALTER TABLE `mall_order` ADD COLUMN `order_type` tinyint NOT NULL DEFAULT 0 COMMENT ''订单类型：0普通1秒杀2拼团3抽奖实物''', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE TABLE IF NOT EXISTS `mall_seckill_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `goods_id` bigint NOT NULL COMMENT '商品ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `origin_price` bigint NOT NULL COMMENT '原价（分）',
  `seckill_price` bigint NOT NULL COMMENT '秒杀价（分）',
  `total_stock` int NOT NULL COMMENT '秒杀总库存',
  `available_stock` int NOT NULL COMMENT '剩余库存（乐观锁扣减字段）',
  `per_limit` int NOT NULL DEFAULT 1 COMMENT '每人限购件数',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0未开始1进行中2已结束',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_seckill_goods` (`goods_id`, `status`),
  KEY `idx_seckill_time` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

CREATE TABLE IF NOT EXISTS `mall_seckill_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_id` bigint NOT NULL COMMENT '秒杀活动ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `order_id` bigint NULL COMMENT '关联订单ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_member_activity` (`member_id`, `activity_id`, `deleted`),
  KEY `idx_seckill_record_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀参与记录';

CREATE TABLE IF NOT EXISTS `mall_group_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `goods_id` bigint NOT NULL COMMENT '商品ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `origin_price` bigint NOT NULL COMMENT '原价（分）',
  `group_price` bigint NOT NULL COMMENT '拼团价（分）',
  `group_size` int NOT NULL COMMENT '成团人数',
  `valid_hours` int NOT NULL COMMENT '成团时限（小时）',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0未开始1进行中2已结束',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_group_goods` (`goods_id`, `status`),
  KEY `idx_group_time` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团活动';

CREATE TABLE IF NOT EXISTS `mall_group_team` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_id` bigint NOT NULL COMMENT '拼团活动ID',
  `leader_id` bigint NOT NULL COMMENT '团长会员ID',
  `current_size` int NOT NULL DEFAULT 1 COMMENT '当前人数（含团长）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0拼团中1已成团2已失败',
  `expire_time` datetime NOT NULL COMMENT '成团截止时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_group_team_activity` (`activity_id`, `status`),
  KEY `idx_group_team_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团';

CREATE TABLE IF NOT EXISTS `mall_group_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `team_id` bigint NOT NULL COMMENT '团ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `order_id` bigint NULL COMMENT '关联订单ID',
  `is_leader` tinyint NOT NULL DEFAULT 0 COMMENT '是否团长：0否1是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_team_member` (`team_id`, `member_id`, `deleted`),
  KEY `idx_group_member_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团成员';

CREATE TABLE IF NOT EXISTS `mall_lottery_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '活动名称',
  `daily_limit` int NOT NULL DEFAULT 1 COMMENT '每人每日可抽次数',
  `consume_points` int NOT NULL DEFAULT 0 COMMENT '每次抽奖消耗积分（0=免费）',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0未开始1进行中2已结束',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_lottery_time` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽奖活动';

CREATE TABLE IF NOT EXISTS `mall_lottery_prize` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_id` bigint NOT NULL COMMENT '抽奖活动ID',
  `prize_type` tinyint NOT NULL COMMENT '奖品类型：0积分1优惠券2实物3谢谢参与',
  `name` varchar(64) NOT NULL COMMENT '奖品名称',
  `ref_id` bigint NULL COMMENT '关联ID：积分=分值，优惠券=券ID，实物=商品ID，谢谢参与=NULL',
  `weight` int NOT NULL COMMENT '权重（越大越易中）',
  `stock` int NOT NULL DEFAULT -1 COMMENT '库存：-1不限量，>=0限量',
  `sort` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_lottery_prize_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽奖奖品';

CREATE TABLE IF NOT EXISTS `mall_lottery_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `activity_id` bigint NOT NULL COMMENT '抽奖活动ID',
  `prize_id` bigint NULL COMMENT '奖品ID',
  `is_win` tinyint NOT NULL DEFAULT 0 COMMENT '是否中奖：0否1是',
  `receive_status` tinyint NOT NULL DEFAULT 0 COMMENT '权益发放状态：0未发放1已发放',
  `order_id` bigint NULL COMMENT '实物奖品关联订单ID',
  `draw_date` date NOT NULL COMMENT '抽奖日期（日限统计维度）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_lottery_record_member` (`member_id`, `activity_id`),
  KEY `idx_lottery_record_daily` (`member_id`, `activity_id`, `draw_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽奖记录';

-- ============================================================
-- V9 分销裂变（与 db/v9-distribution.sql 保持一致；测试库只建表不灌种子）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_distributor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `invite_code` varchar(32) NOT NULL COMMENT '专属邀请码',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '分销员等级：默认1',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待审1启用2停用3驳回',
  `parent_id` bigint DEFAULT NULL COMMENT '上级分销员ID（一级为NULL）',
  `total_commission` bigint NOT NULL DEFAULT 0 COMMENT '累计佣金（分）',
  `settled` bigint NOT NULL DEFAULT 0 COMMENT '已结算佣金（分）',
  `withdrawable` bigint NOT NULL DEFAULT 0 COMMENT '可提现佣金（分）',
  `reject_reason` varchar(255) DEFAULT NULL COMMENT '驳回原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除:0正常1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_distributor_member` (`member_id`, `deleted`),
  UNIQUE KEY `uk_distributor_code` (`invite_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销员';

CREATE TABLE IF NOT EXISTS `mall_invitation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `inviter_id` bigint NOT NULL COMMENT '邀请人（分销员）ID',
  `invitee_id` bigint NOT NULL COMMENT '被邀请人（会员）ID',
  `channel` varchar(32) DEFAULT NULL COMMENT '来源渠道：link/code/poster',
  `bind_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  `order_count` int NOT NULL DEFAULT 0 COMMENT '贡献订单数',
  `contribution` bigint NOT NULL DEFAULT 0 COMMENT '贡献成交额（分）',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '第几级：1一级',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除:0正常1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invitation_invitee` (`invitee_id`, `deleted`),
  KEY `idx_invitation_inviter` (`inviter_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请关系';

CREATE TABLE IF NOT EXISTS `mall_promotion_goods` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `goods_id` bigint NOT NULL COMMENT '商品ID',
  `distributor_id` bigint NOT NULL COMMENT '推广该商品的分销员ID',
  `promo_price` bigint NOT NULL DEFAULT 0 COMMENT '推广价（分）',
  `commission_rate` int NOT NULL DEFAULT 0 COMMENT '佣金比例（‰）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用0停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除:0正常1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_promo_goods` (`goods_id`, `distributor_id`, `deleted`),
  KEY `idx_promo_distributor` (`distributor_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推广商品';

CREATE TABLE IF NOT EXISTS `mall_commission_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scope` tinyint NOT NULL DEFAULT 0 COMMENT '作用域：0全局1指定商品',
  `goods_id` bigint DEFAULT NULL COMMENT '指定商品ID（scope=1时必填）',
  `rate` int NOT NULL DEFAULT 0 COMMENT '佣金比例（‰）',
  `fixed` bigint NOT NULL DEFAULT 0 COMMENT '固定佣金（分），与rate二选一优先fixed',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '分销层级：默认1（仅一级分销）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用0停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除:0正常1删除',
  PRIMARY KEY (`id`),
  KEY `idx_commission_rule_goods` (`goods_id`, `deleted`),
  KEY `idx_commission_rule_scope` (`scope`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='佣金规则';

CREATE TABLE IF NOT EXISTS `mall_commission_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `distributor_id` bigint NOT NULL COMMENT '分销员ID',
  `order_id` bigint NOT NULL COMMENT '关联订单ID（幂等键）',
  `member_id` bigint NOT NULL COMMENT '下单会员ID',
  `goods_id` bigint NOT NULL COMMENT '商品ID',
  `amount` bigint NOT NULL DEFAULT 0 COMMENT '佣金金额（分）',
  `rate` int NOT NULL DEFAULT 0 COMMENT '佣金比例（‰，记录时快照）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待结算1已结算2失效',
  `settle_time` datetime DEFAULT NULL COMMENT '结算时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除:0正常1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_order` (`order_id`, `distributor_id`, `deleted`),
  KEY `idx_commission_distributor` (`distributor_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='佣金记录';

CREATE TABLE IF NOT EXISTS `mall_withdrawal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `distributor_id` bigint NOT NULL COMMENT '分销员ID',
  `amount` bigint NOT NULL DEFAULT 0 COMMENT '提现金额（分）',
  `method` tinyint NOT NULL DEFAULT 0 COMMENT '提现方式：0余额1微信2银行卡',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0申请1审核中2已打款3拒绝',
  `audit_by` bigint DEFAULT NULL COMMENT '审核人',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_remark` varchar(255) DEFAULT NULL COMMENT '审核备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除:0正常1删除',
  PRIMARY KEY (`id`),
  KEY `idx_withdrawal_distributor` (`distributor_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销员提现';

-- V9 订单归因 + 会员佣金余额（运行库在 db/v9-distribution.sql 已 ALTER，此处保证测试库一致；幂等）
SET @v9_oc := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order' AND COLUMN_NAME = 'distributor_id');
SET @v9_oc_ddl := IF(@v9_oc = 0, 'ALTER TABLE `mall_order` ADD COLUMN `distributor_id` bigint DEFAULT NULL COMMENT ''分销归因：带来此单的分销员ID'' AFTER `status`', 'SELECT 1');
PREPARE v9_oc_stmt FROM @v9_oc_ddl;
EXECUTE v9_oc_stmt;
DEALLOCATE PREPARE v9_oc_stmt;

SET @v9_mc := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_member' AND COLUMN_NAME = 'commission_balance');
SET @v9_mc_ddl := IF(@v9_mc = 0, 'ALTER TABLE `mall_member` ADD COLUMN `commission_balance` bigint NOT NULL DEFAULT 0 COMMENT ''佣金余额（分）''', 'SELECT 1');
PREPARE v9_mc_stmt FROM @v9_mc_ddl;
EXECUTE v9_mc_stmt;
DEALLOCATE PREPARE v9_mc_stmt;

-- ============================================================
-- V10 商家域（见 db/v10-merchant.sql）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_merchant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `merchant_no` varchar(32) NOT NULL,
  `name` varchar(64) NOT NULL,
  `legal_name` varchar(64) DEFAULT NULL,
  `license_no` varchar(64) DEFAULT NULL,
  `license_img` varchar(500) DEFAULT NULL,
  `contact_name` varchar(32) DEFAULT NULL,
  `contact_phone` varchar(20) DEFAULT NULL,
  `category` varchar(64) DEFAULT NULL,
  `logo` varchar(500) DEFAULT NULL,
  `intro` varchar(500) DEFAULT NULL,
  `settle_rate` int NOT NULL DEFAULT '50',
  `status` tinyint NOT NULL DEFAULT '0',
  `reject_reason` varchar(255) DEFAULT NULL,
  `account_balance` bigint NOT NULL DEFAULT '0',
  `settled_total` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_no` (`merchant_no`, `deleted`),
  UNIQUE KEY `uk_merchant_license` (`license_no`, `deleted`),
  KEY `idx_merchant_status` (`status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家（店铺）';

CREATE TABLE IF NOT EXISTS `mall_merchant_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint NOT NULL,
  `action` tinyint NOT NULL,
  `operator` varchar(64) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_audit_merchant` (`merchant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家审核流水';

CREATE TABLE IF NOT EXISTS `mall_merchant_settlement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `settlement_no` varchar(32) NOT NULL,
  `merchant_id` bigint NOT NULL,
  `period_start` datetime DEFAULT NULL,
  `period_end` datetime DEFAULT NULL,
  `order_count` int NOT NULL DEFAULT '0',
  `goods_amount` bigint NOT NULL DEFAULT '0',
  `platform_fee` bigint NOT NULL DEFAULT '0',
  `refund_amount` bigint NOT NULL DEFAULT '0',
  `actual_amount` bigint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '0',
  `settle_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_no` (`settlement_no`, `deleted`),
  KEY `idx_settlement_merchant` (`merchant_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家结算单';

CREATE TABLE IF NOT EXISTS `mall_merchant_bill_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `settlement_id` bigint DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `order_no` varchar(32) DEFAULT NULL,
  `merchant_id` bigint NOT NULL,
  `settle_rate` int NOT NULL DEFAULT '0',
  `pay_amount` bigint NOT NULL DEFAULT '0',
  `goods_amount` bigint NOT NULL DEFAULT '0',
  `platform_fee` bigint NOT NULL DEFAULT '0',
  `refund_flag` tinyint NOT NULL DEFAULT '0',
  `refund_amount` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bill_order` (`order_id`, `merchant_id`, `deleted`),
  KEY `idx_bill_merchant` (`merchant_id`, `settlement_id`, `refund_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家结算明细';

CREATE TABLE IF NOT EXISTS `mall_merchant_refund_adjust` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `refund_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `merchant_id` bigint NOT NULL,
  `refund_amount` bigint NOT NULL DEFAULT '0',
  `merchant_share` bigint NOT NULL DEFAULT '0',
  `platform_share` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_adjust` (`refund_id`, `deleted`),
  KEY `idx_refund_adjust_order` (`order_id`, `merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家退款冲减流水';

CREATE TABLE IF NOT EXISTS `mall_merchant_withdrawal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint NOT NULL,
  `amount` bigint NOT NULL,
  `method` tinyint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '0',
  `audit_by` varchar(64) DEFAULT NULL,
  `audit_time` datetime DEFAULT NULL,
  `reject_reason` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_withdrawal_merchant` (`merchant_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家提现';

-- ============================================================
-- V12 交易真实化：运费模板 / 支付单与对账 / 发货单 / 发票
-- 对应运行库脚本 db/v12-trade.sql（此处不含菜单与种子数据）
-- ============================================================
-- 1) 运费模板
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_freight_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '模板名称',
  `charge_type` tinyint NOT NULL DEFAULT 1 COMMENT '计费方式：1按件2按重量3满额包邮',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否全局默认模板：1是0否',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用0停用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_freight_template_status` (`status`, `is_default`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运费模板';

CREATE TABLE IF NOT EXISTS `mall_freight_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id` bigint NOT NULL COMMENT '模板ID 关联mall_freight_template',
  `region_codes` varchar(500) DEFAULT NULL COMMENT '适用地区：逗号分隔的省份名（如「新疆,西藏」）；空=模板默认规则',
  `first_unit` int NOT NULL DEFAULT 1 COMMENT '首件/首重：按件计费为件数，按重量计费为克',
  `first_fee` bigint NOT NULL DEFAULT 0 COMMENT '首件/首重运费（分）',
  `extra_unit` int NOT NULL DEFAULT 1 COMMENT '续件/续重步长：按件为件数，按重量为克',
  `extra_fee` bigint NOT NULL DEFAULT 0 COMMENT '每续一件/一续重运费（分）',
  `free_threshold` bigint NOT NULL DEFAULT 0 COMMENT '满额免邮阈值（分）：0=不启用',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序：越小越先匹配（精确地区优先于默认规则）',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_freight_rule_template` (`template_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运费模板地区规则';

-- ============================================================
-- 2) 支付单与对账
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pay_no` varchar(40) NOT NULL COMMENT '本地支付单号（一次下单一次支付尝试）',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号（冗余，便于对账）',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `merchant_id` bigint DEFAULT NULL COMMENT '商家归属（冗余自订单）',
  `channel` varchar(16) NOT NULL COMMENT '支付渠道：sandbox/wxpay/alipay',
  `trade_type` varchar(16) NOT NULL DEFAULT 'NATIVE' COMMENT '交易类型：NATIVE/JSAPI/H5/MOCK',
  `amount` bigint NOT NULL COMMENT '支付金额（分）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待支付1成功2失败3已全额退4部分退',
  `channel_trade_no` varchar(64) DEFAULT NULL COMMENT '渠道交易号（成功回调后回填）',
  `prepay_params` text COMMENT '渠道下单返回参数（二维码链接/JSAPI 参数 JSON）',
  `notify_time` datetime DEFAULT NULL COMMENT '回调时间',
  `raw_notify` text COMMENT '原始回调报文（留证与排障）',
  `expire_time` datetime DEFAULT NULL COMMENT '支付超时时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_pay_no` (`pay_no`, `deleted`),
  KEY `idx_payment_order` (`order_id`, `status`, `deleted`),
  KEY `idx_payment_channel_trade` (`channel_trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单';

CREATE TABLE IF NOT EXISTS `mall_pay_reconcile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `bill_date` date NOT NULL COMMENT '账单日期',
  `channel` varchar(16) NOT NULL COMMENT '支付渠道',
  `pay_no` varchar(40) DEFAULT NULL COMMENT '本地支付单号（渠道缺失时为空）',
  `channel_trade_no` varchar(64) DEFAULT NULL COMMENT '渠道交易号',
  `channel_amount` bigint DEFAULT NULL COMMENT '渠道账单金额（分）',
  `local_amount` bigint DEFAULT NULL COMMENT '本地支付单金额（分）',
  `diff_type` tinyint NOT NULL COMMENT '差异类型：1长款2短款3本地缺失4渠道缺失',
  `handle_status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态：0待处理1已确认2已忽略',
  `handle_remark` varchar(255) DEFAULT NULL COMMENT '处理说明',
  `handle_by` varchar(64) DEFAULT NULL COMMENT '处理人',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_reconcile_date` (`bill_date`, `channel`, `handle_status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付对账差异';

-- ============================================================
-- 3) 发货单（一单可多次发货，支持部分发货）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `delivery_no` varchar(32) NOT NULL COMMENT '发货单号：D+时间戳+随机',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号（冗余）',
  `merchant_id` bigint DEFAULT NULL COMMENT '商家归属',
  `express_company` varchar(32) NOT NULL COMMENT '快递公司',
  `express_no` varchar(64) NOT NULL COMMENT '快递单号',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1已发货2已签收',
  `delivery_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发货时间',
  `receiver_name` varchar(50) DEFAULT NULL COMMENT '收货人（下单快照）',
  `receiver_phone` varchar(20) DEFAULT NULL COMMENT '收货电话（下单快照）',
  `receiver_address` varchar(500) DEFAULT NULL COMMENT '收货地址（下单快照）',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_delivery_no` (`delivery_no`, `deleted`),
  UNIQUE KEY `uk_delivery_express` (`express_company`, `express_no`, `deleted`),
  KEY `idx_delivery_order` (`order_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货单';

CREATE TABLE IF NOT EXISTS `mall_delivery_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `delivery_id` bigint NOT NULL COMMENT '发货单ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_item_id` bigint NOT NULL COMMENT '订单明细ID',
  `sku_id` bigint DEFAULT NULL COMMENT 'SKU ID',
  `goods_name` varchar(128) DEFAULT NULL COMMENT '商品名（快照）',
  `quantity` int NOT NULL COMMENT '本次发货数量',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_delivery_item_delivery` (`delivery_id`, `deleted`),
  KEY `idx_delivery_item_order` (`order_id`, `order_item_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货单明细';

-- ============================================================
-- 4) 发票
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `invoice_no` varchar(32) NOT NULL COMMENT '发票申请单号：I+时间戳+随机',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号（冗余）',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `merchant_id` bigint DEFAULT NULL COMMENT '商家归属',
  `title_type` tinyint NOT NULL DEFAULT 1 COMMENT '抬头类型：1个人2企业',
  `title` varchar(128) NOT NULL COMMENT '发票抬头',
  `tax_no` varchar(32) DEFAULT NULL COMMENT '纳税人识别号（企业必填，15-20 位）',
  `email` varchar(128) DEFAULT NULL COMMENT '接收邮箱',
  `amount` bigint NOT NULL COMMENT '开票金额（分）：取订单实付，不可改',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待开票1已开票2已红冲',
  `file_url` varchar(500) DEFAULT NULL COMMENT '电子发票文件地址（PDF/OFD）',
  `issue_time` datetime DEFAULT NULL COMMENT '开票时间',
  `red_flush_time` datetime DEFAULT NULL COMMENT '红冲时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_order` (`order_id`, `deleted`),
  UNIQUE KEY `uk_invoice_no` (`invoice_no`, `deleted`),
  KEY `idx_invoice_member` (`member_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票申请';

-- ============================================================

-- V12 既有表增列（幂等：information_schema 守护）
-- 5.1 SKU 重量（按重量计费）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_sku' AND COLUMN_NAME = 'weight');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_sku` ADD COLUMN `weight` int NOT NULL DEFAULT 0 COMMENT ''重量（克），按重量计费用'' AFTER `stock`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.2 订单明细已发数量（部分发货）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'shipped_quantity');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_order_item` ADD COLUMN `shipped_quantity` int NOT NULL DEFAULT 0 COMMENT ''已发货数量（部分发货累计）'' AFTER `quantity`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.3 商品绑定运费模板（空=回落店铺默认→全局默认）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_goods' AND COLUMN_NAME = 'freight_template_id');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_goods` ADD COLUMN `freight_template_id` bigint DEFAULT NULL COMMENT ''运费模板ID，空则回落店铺/全局默认'' AFTER `merchant_id`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.4 店铺默认运费模板
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_merchant' AND COLUMN_NAME = 'freight_template_id');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_merchant` ADD COLUMN `freight_template_id` bigint DEFAULT NULL COMMENT ''店铺默认运费模板ID'' AFTER `settle_rate`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.5 退款原路退回字段
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'channel');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_refund` ADD COLUMN `channel` varchar(16) DEFAULT NULL COMMENT ''退款渠道：sandbox/wxpay/alipay'' AFTER `pay_order_no`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'channel_refund_no');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_refund` ADD COLUMN `channel_refund_no` varchar(64) DEFAULT NULL COMMENT ''渠道退款单号'' AFTER `channel`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'refund_status');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_refund` ADD COLUMN `refund_status` tinyint NOT NULL DEFAULT 0 COMMENT ''渠道退款状态：0未发起1退款中2退款成功3退款失败'' AFTER `channel_refund_no`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================

-- ===== V14 (support/message/search) START =====
-- V14 三块能力的建表：搜索词表/日志、客服工单/回复、消息模板/日志。
-- 幂等：IF NOT EXISTS；FULLTEXT 索引走 information_schema 守护（与 db/v14-experience.sql 同口径）。
CREATE TABLE IF NOT EXISTS `mall_search_term` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `word` varchar(64) NOT NULL COMMENT '词',
  `pinyin` varchar(128) DEFAULT NULL COMMENT '全拼',
  `pinyin_initial` varchar(32) DEFAULT NULL COMMENT '拼音首字母',
  `synonyms` varchar(255) DEFAULT NULL COMMENT '同义词组，逗号分隔',
  `category_id` bigint DEFAULT NULL COMMENT '关联类目ID',
  `weight` int NOT NULL DEFAULT 0 COMMENT '权重',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用0停用',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_search_term_word` (`word`, `deleted`),
  KEY `idx_search_term_pinyin` (`pinyin`, `deleted`),
  KEY `idx_search_term_initial` (`pinyin_initial`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索词表（同义词/拼音/首字母）';

CREATE TABLE IF NOT EXISTS `mall_search_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `keyword` varchar(64) NOT NULL COMMENT '搜索词',
  `member_id` bigint DEFAULT NULL COMMENT '会员ID',
  `result_count` int NOT NULL DEFAULT 0 COMMENT '命中条数',
  `hit` tinyint NOT NULL DEFAULT 0 COMMENT '是否有结果：1是0否',
  `pinyin_hit` tinyint NOT NULL DEFAULT 0 COMMENT '是否拼音命中',
  `corrected_from` varchar(64) DEFAULT NULL COMMENT '纠错前原词',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_search_log_keyword` (`keyword`, `create_time`),
  KEY `idx_search_log_time` (`create_time`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索日志（热搜/无结果分析）';

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_goods' AND INDEX_NAME = 'ft_goods_name_subtitle');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_goods` ADD FULLTEXT INDEX `ft_goods_name_subtitle` (`name`, `sub_title`) WITH PARSER ngram', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `mall_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `ticket_no` varchar(32) NOT NULL COMMENT '工单号',
  `member_id` bigint NOT NULL COMMENT '提交会员ID',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单ID',
  `order_no` varchar(32) DEFAULT NULL COMMENT '订单号',
  `category` tinyint NOT NULL DEFAULT 5 COMMENT '分类：1商品咨询2订单售后3物流4支付5其他',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `content` varchar(2000) NOT NULL COMMENT '问题描述',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1待处理2已回复3已关闭',
  `priority` tinyint NOT NULL DEFAULT 2 COMMENT '优先级：1低2中3高',
  `first_reply_time` datetime DEFAULT NULL COMMENT '首次回复时间',
  `last_reply_time` datetime DEFAULT NULL COMMENT '最近回复时间',
  `timeout_flag` tinyint NOT NULL DEFAULT 0 COMMENT '首响超时标记：0正常1超24h未首响',
  `close_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_no` (`ticket_no`, `deleted`),
  KEY `idx_ticket_member` (`member_id`, `status`, `deleted`),
  KEY `idx_ticket_status` (`status`, `timeout_flag`, `create_time`, `deleted`),
  KEY `idx_ticket_order` (`order_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服工单';

CREATE TABLE IF NOT EXISTS `mall_ticket_reply` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `ticket_id` bigint NOT NULL COMMENT '工单ID',
  `replier_type` tinyint NOT NULL COMMENT '回复人类型：1会员2客服',
  `replier_id` bigint DEFAULT NULL COMMENT '回复人ID',
  `content` varchar(2000) NOT NULL COMMENT '回复内容',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_ticket_reply_ticket` (`ticket_id`, `create_time`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服工单回复';

CREATE TABLE IF NOT EXISTS `mall_message_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scene_code` varchar(32) NOT NULL COMMENT '场景码',
  `scene_name` varchar(64) NOT NULL COMMENT '场景名称',
  `title_template` varchar(128) NOT NULL COMMENT '标题模板',
  `content_template` varchar(1000) NOT NULL COMMENT '内容模板',
  `channel` varchar(16) NOT NULL DEFAULT 'sms' COMMENT '主通道：sms/site',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用0停用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_template_scene` (`scene_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息模板';

CREATE TABLE IF NOT EXISTS `mall_message_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scene_code` varchar(32) NOT NULL COMMENT '场景码',
  `member_id` bigint NOT NULL COMMENT '接收人',
  `channel` varchar(16) NOT NULL COMMENT '最终通道',
  `title` varchar(128) DEFAULT NULL COMMENT '渲染后标题',
  `content` varchar(1000) DEFAULT NULL COMMENT '渲染后内容',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0失败1成功2降级成功',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `degraded` tinyint NOT NULL DEFAULT 0 COMMENT '降级标记',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `send_time` datetime DEFAULT NULL COMMENT '发送完成时间',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '业务单号',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_message_log_member` (`member_id`, `create_time`, `deleted`),
  KEY `idx_message_log_scene` (`scene_code`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息发送日志';

-- 词表与模板的最小夹具（测试库用；业务库由 db/v14-experience.sql 灌）
INSERT INTO `mall_search_term` (`word`, `pinyin`, `pinyin_initial`, `synonyms`, `category_id`, `weight`, `status`)
SELECT '手机', 'shouji', 'sj', '移动电话,智能手机', 201, 100, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `mall_search_term` WHERE `word` = '手机' AND `deleted` = 0);

INSERT INTO `mall_message_template` (`scene_code`, `scene_name`, `title_template`, `content_template`, `channel`, `status`)
SELECT 'PAY_SUCCESS', '支付成功', '支付成功通知',
       '您的订单 ${orderNo} 已支付成功，金额 ${amount} 元。', 'sms', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `mall_message_template` WHERE `scene_code` = 'PAY_SUCCESS' AND `deleted` = 0);

INSERT INTO `mall_message_template` (`scene_code`, `scene_name`, `title_template`, `content_template`, `channel`, `status`)
SELECT 'ORDER_SHIPPED', '订单发货', '发货通知',
       '您的订单 ${orderNo} 已发货，${expressCompany} 单号 ${expressNo}。', 'sms', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `mall_message_template` WHERE `scene_code` = 'ORDER_SHIPPED' AND `deleted` = 0);

INSERT INTO `mall_message_template` (`scene_code`, `scene_name`, `title_template`, `content_template`, `channel`, `status`)
SELECT 'REFUND_ARRIVED', '退款到账', '退款到账通知',
       '您的订单 ${orderNo} 退款 ${amount} 元已原路退回。', 'sms', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `mall_message_template` WHERE `scene_code` = 'REFUND_ARRIVED' AND `deleted` = 0);
-- ===== V14 (support/message/search) END =====


-- ===== V11 (merchant/compliance) START =====
-- 商家独立后台 + 合规三件套：账号、协议、同意留痕、注销冷静期列。
-- ============================================================
-- V11 商家侧闭环 + 合规三件套
-- 执行方式：mysql -uroot -proot --default-character-set=utf8mb4 yueji_mall < db/v11-merchant-compliance.sql
-- 幂等：CREATE TABLE IF NOT EXISTS + information_schema 守护的 ALTER +
--       NOT EXISTS / ON DUPLICATE KEY UPDATE 种子，可重复执行（连跑两次不报错）。
-- 规范：审计六件套（id/create_time/update_time/create_by/update_by/deleted）同 BaseEntity；
--       每列 COMMENT；金额单位「分」；唯一键一律带 deleted。
-- 与既有版本关系：
--   V10 的 mall_merchant 是店铺主体，本脚本新增 mall_merchant_user（一店多账号，与 sys_user 隔离）；
--   mall_member 增加注销冷静期三列（cancel_status / cancel_apply_time / cancel_effective_time）；
--   新增 mall_agreement（协议正文与版本）+ mall_member_agreement_log（同意留痕）。
-- 演示账号：merchant / 123456（商家 ID=1 平台自营店铺；BCrypt 存储，password_algo=bcrypt）。
-- ============================================================
SET NAMES utf8mb4;

-- ============================================================
-- 1) 商家账号（与平台 sys_user 完全隔离，令牌不通用）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_merchant_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `merchant_id` bigint NOT NULL COMMENT '商家ID 关联mall_merchant',
  `username` varchar(64) NOT NULL COMMENT '登录账号（全局唯一，与 sys_user.username 不共享命名空间）',
  `password` varchar(128) NOT NULL COMMENT '密码哈希（BCrypt）',
  `password_algo` varchar(16) NOT NULL DEFAULT 'bcrypt' COMMENT '密码算法：bcrypt / sha256（存量兼容）',
  `real_name` varchar(32) DEFAULT NULL COMMENT '真实姓名/联系人',
  `role` varchar(32) NOT NULL DEFAULT 'owner' COMMENT '角色：owner店主 / staff店员',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用0停用',
  `fail_count` int NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `lock_until` datetime DEFAULT NULL COMMENT '锁定截止时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记:0正常1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_user_name` (`username`, `deleted`),
  KEY `idx_merchant_user_merchant` (`merchant_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家账号（V11 商家独立后台）';

-- 演示账号：只在该账号不存在时插入，避免覆盖运营改过的密码
INSERT INTO `mall_merchant_user` (`merchant_id`, `username`, `password`, `password_algo`, `real_name`, `role`, `status`)
SELECT 1, 'merchant', '$2a$10$JuWB/1nCoOvQl8rk3MDebOhm8ykC.J8qeNubAAPx9fZJHYrX5Sngm', 'bcrypt', '演示店主', 'owner', 1
WHERE NOT EXISTS (SELECT 1 FROM `mall_merchant_user` WHERE `username` = 'merchant' AND `deleted` = 0);

-- ============================================================
-- 2) 协议（用户协议 / 隐私政策）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_agreement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` tinyint NOT NULL COMMENT '协议类型：1用户协议2隐私政策',
  `version` varchar(16) NOT NULL COMMENT '版本号（如 1.0.0）',
  `title` varchar(64) NOT NULL COMMENT '标题',
  `content` mediumtext COMMENT '协议正文（富文本/纯文本）',
  `effective_time` datetime DEFAULT NULL COMMENT '生效时间，空=立即生效',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1发布0下线',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agreement_type_version` (`type`, `version`, `deleted`),
  KEY `idx_agreement_type_status` (`type`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台协议（用户协议/隐私政策）';

INSERT INTO `mall_agreement` (`type`, `version`, `title`, `content`, `effective_time`, `status`)
SELECT 1, '1.0.0', '悦集商城用户协议',
       '一、协议范围\n本协议是您与悦集商城之间关于使用平台服务所订立的契约。\n二、账号注册与使用\n您应提供真实、准确的注册信息，并妥善保管账号密码。\n三、交易规则\n订单提交即视为向商家发出要约，商家发货后合同成立。\n四、个人信息保护\n我们依据《隐私政策》收集与使用您的个人信息。',
       NOW(), 1
WHERE NOT EXISTS (SELECT 1 FROM `mall_agreement` WHERE `type` = 1 AND `version` = '1.0.0' AND `deleted` = 0);

INSERT INTO `mall_agreement` (`type`, `version`, `title`, `content`, `effective_time`, `status`)
SELECT 2, '1.0.0', '悦集商城隐私政策',
       '一、我们收集的信息\n手机号、收货地址、订单记录等为完成交易所必需的信息。\n二、信息的使用\n仅用于订单履约、售后服务与安全风控。\n三、信息的共享\n仅在履约必需范围内向商家与物流服务商提供。\n四、您的权利\n您可查询、更正、导出个人信息，或申请注销账号。',
       NOW(), 1
WHERE NOT EXISTS (SELECT 1 FROM `mall_agreement` WHERE `type` = 2 AND `version` = '1.0.0' AND `deleted` = 0);

-- ============================================================
-- 3) 协议同意留痕（注册/重新同意都写一条）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_member_agreement_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '会员ID 关联mall_member',
  `agreement_id` bigint NOT NULL COMMENT '协议ID 关联mall_agreement',
  `agreement_type` tinyint NOT NULL COMMENT '协议类型冗余：1用户协议2隐私政策',
  `version` varchar(16) NOT NULL COMMENT '同意的协议版本',
  `agree_time` datetime NOT NULL COMMENT '同意时间',
  `ip` varchar(64) DEFAULT NULL COMMENT '同意时客户端IP',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_member_agreement_member` (`member_id`, `agreement_type`, `deleted`),
  KEY `idx_member_agreement_version` (`agreement_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员协议同意留痕';

-- ============================================================
-- 4) 会员注销冷静期（既有表增列，information_schema 守护）
-- ============================================================
-- 4.1 注销状态：0 正常 / 1 冷静期 / 2 已注销
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_member' AND COLUMN_NAME = 'cancel_status');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_member` ADD COLUMN `cancel_status` tinyint NOT NULL DEFAULT 0 COMMENT ''注销状态：0正常1冷静期2已注销''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.2 申请时间
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_member' AND COLUMN_NAME = 'cancel_apply_time');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_member` ADD COLUMN `cancel_apply_time` datetime DEFAULT NULL COMMENT ''注销申请时间''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.3 冷静期到期时间（申请时间 + 7 天）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_member' AND COLUMN_NAME = 'cancel_effective_time');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_member` ADD COLUMN `cancel_effective_time` datetime DEFAULT NULL COMMENT ''注销生效时间（冷静期到期）''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- ===== V11 (merchant/compliance) END =====

-- ===== V13 (marketing) START =====
-- 促销活动/范围/命中、会员等级与成长值、降价提醒、评价有用、评价追评列。
-- ============================================================
-- V13 营销补面（促销引擎 / 会员等级与权益 / 评价增强 / 购物车营销）
-- 执行方式：mysql -uroot -proot --default-character-set=utf8mb4 yueji_mall < db/v13-marketing.sql
-- 幂等：CREATE TABLE IF NOT EXISTS + information_schema 守护的 ALTER + 菜单 NOT EXISTS，可重复执行。
-- 规范：审计六件套（id/create_time/update_time/create_by/update_by/deleted）同 BaseEntity；
--       每列 COMMENT；金额单位「分」；唯一键一律带 deleted。
-- 与既有版本关系：
--   mall_member 增加 level_id / growth / 包邮次数（守护式 ALTER，历史会员回落 0 成长值）；
--   mall_review 增加 parent_id / review_type / useful_count / tags（历史评价视为首评）。
-- 促销引擎与既有 mall_coupon（券）并行：活动在前、券在后，见需求文档 §八「关键依赖」。
-- ============================================================
SET NAMES utf8mb4;

-- ============================================================
-- 1) 促销活动
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_promotion` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '活动名称',
  `type` tinyint NOT NULL COMMENT '活动类型：1满减2满折3限时折扣4第二件半价5套装6加价购',
  `scope_type` tinyint NOT NULL DEFAULT 1 COMMENT '作用范围：1全场2类目3商品4店铺',
  `rule_json` varchar(1000) DEFAULT NULL COMMENT '规则JSON：{"threshold":30000,"discount":5000,"discountRate":90,"bundleQty":2,"bundlePrice":0,"addOnPrice":0}',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间（空=不限）',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间（空=不限）',
  `priority` int NOT NULL DEFAULT 100 COMMENT '优先级：数值越小越优先（同折扣取优先级高的）',
  `stackable` tinyint NOT NULL DEFAULT 0 COMMENT '是否可与其他活动叠加：1是0否',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1上线0下线',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_promotion_status_time` (`status`, `start_time`, `end_time`, `deleted`),
  KEY `idx_promotion_priority` (`priority`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='促销活动';

CREATE TABLE IF NOT EXISTS `mall_promotion_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `promotion_id` bigint NOT NULL COMMENT '活动ID 关联mall_promotion',
  `scope_type` tinyint NOT NULL COMMENT '范围类型：2类目3商品4店铺（与主表 scope_type 一致）',
  `ref_id` bigint NOT NULL COMMENT '范围对象ID：类目ID/商品ID/店铺ID',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_promotion_scope_pid` (`promotion_id`, `deleted`),
  KEY `idx_promotion_scope_ref` (`scope_type`, `ref_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='促销活动作用范围明细';

CREATE TABLE IF NOT EXISTS `mall_promotion_hit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `promotion_id` bigint NOT NULL COMMENT '活动ID',
  `promotion_name` varchar(64) DEFAULT NULL COMMENT '活动名称（冗余，活动改名后仍可对账）',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `member_id` bigint DEFAULT NULL COMMENT '会员ID',
  `discount_amount` bigint NOT NULL DEFAULT 0 COMMENT '该活动在本单的优惠金额（分）',
  `hit_time` datetime DEFAULT NULL COMMENT '命中时间（下单时间）',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_promotion_hit_order` (`order_id`, `deleted`),
  KEY `idx_promotion_hit_pid` (`promotion_id`, `hit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='促销活动命中留痕';

-- ============================================================
-- 2) 会员等级与成长值
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_member_level` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) NOT NULL COMMENT '等级名称',
  `level_no` int NOT NULL DEFAULT 1 COMMENT '等级序号：1最低',
  `growth_threshold` bigint NOT NULL DEFAULT 0 COMMENT '成长值阈值：达到即可升级',
  `discount_rate` int NOT NULL DEFAULT 100 COMMENT '会员折扣率：100=不打折，95=9.5折',
  `free_shipping_count` int NOT NULL DEFAULT 0 COMMENT '每月包邮次数',
  `benefits_json` varchar(1000) DEFAULT NULL COMMENT '权益说明JSON数组：["专属客服","生日礼包"]',
  `icon` varchar(255) DEFAULT NULL COMMENT '等级图标',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用0停用',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序：越小越靠前',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_member_level_threshold` (`growth_threshold`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级';

CREATE TABLE IF NOT EXISTS `mall_member_level_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `before_level_id` bigint DEFAULT NULL COMMENT '变更前等级ID',
  `after_level_id` bigint DEFAULT NULL COMMENT '变更后等级ID',
  `before_level_name` varchar(32) DEFAULT NULL COMMENT '变更前等级名（冗余）',
  `after_level_name` varchar(32) DEFAULT NULL COMMENT '变更后等级名（冗余）',
  `growth` bigint NOT NULL DEFAULT 0 COMMENT '变更时成长值',
  `reason` varchar(32) DEFAULT NULL COMMENT '变更原因：order消费/signin签到/review评价/manual手动',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_member_level_log_member` (`member_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级变更日志';

-- ============================================================
-- 3) 购物车营销：降价提醒
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_price_remind` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `goods_id` bigint NOT NULL COMMENT '商品ID',
  `sku_id` bigint NOT NULL DEFAULT 0 COMMENT 'SKU ID，0=盯商品最低价',
  `expected_price` bigint NOT NULL COMMENT '期望价（分）：现价 ≤ 期望价即通知',
  `current_price` bigint DEFAULT NULL COMMENT '订阅时价格（分）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0监控中1已通知2已失效',
  `notify_time` datetime DEFAULT NULL COMMENT '通知时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_price_remind_member_goods` (`member_id`, `goods_id`, `sku_id`, `deleted`),
  KEY `idx_price_remind_status` (`status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='降价提醒订阅';

-- ============================================================
-- 4) 评价增强：有用记录（唯一键保证「一人一次」）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mall_review_useful` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `review_id` bigint NOT NULL COMMENT '评价ID 关联mall_review',
  `member_id` bigint NOT NULL COMMENT '点有用的会员ID',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_useful` (`review_id`, `member_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价有用记录';

-- ============================================================
-- 5) 既有表增列（幂等：先查 information_schema 再决定是否 ALTER）
-- ============================================================
-- 5.1 会员等级与成长值
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_member' AND COLUMN_NAME = 'level_id');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_member` ADD COLUMN `level_id` bigint DEFAULT NULL COMMENT ''会员等级ID 关联mall_member_level'' AFTER `points`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_member' AND COLUMN_NAME = 'growth');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_member` ADD COLUMN `growth` bigint NOT NULL DEFAULT 0 COMMENT ''成长值：消费1元=1点'' AFTER `level_id`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_member' AND COLUMN_NAME = 'free_shipping_used');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_member` ADD COLUMN `free_shipping_used` int NOT NULL DEFAULT 0 COMMENT ''本月已用包邮次数'' AFTER `growth`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_member' AND COLUMN_NAME = 'free_shipping_month');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_member` ADD COLUMN `free_shipping_month` varchar(7) DEFAULT NULL COMMENT ''包邮次数统计月份 yyyy-MM'' AFTER `free_shipping_used`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.2 评价增强（历史评价视为首评，review_type 默认 1）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_review' AND COLUMN_NAME = 'parent_id');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_review` ADD COLUMN `parent_id` bigint DEFAULT NULL COMMENT ''追评指向的原评价ID，首评为空'' AFTER `sku_id`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_review' AND COLUMN_NAME = 'review_type');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_review` ADD COLUMN `review_type` tinyint NOT NULL DEFAULT 1 COMMENT ''评价类型：1首评2追评'' AFTER `parent_id`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_review' AND COLUMN_NAME = 'useful_count');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_review` ADD COLUMN `useful_count` int NOT NULL DEFAULT 0 COMMENT ''有用数（mall_review_useful 唯一键去重后累加）'' AFTER `images`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_review' AND COLUMN_NAME = 'tags');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_review` ADD COLUMN `tags` varchar(255) DEFAULT NULL COMMENT ''评价标签，逗号分隔：物流快,包装好'' AFTER `useful_count`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 追评只允许挂在首评上：索引便于详情页一次捞完首评+追评
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_review' AND INDEX_NAME = 'idx_review_parent');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_review` ADD INDEX `idx_review_parent` (`parent_id`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 6) 会员等级种子（4 档，只在无等级时插入）
-- ============================================================
INSERT INTO `mall_member_level` (`name`, `level_no`, `growth_threshold`, `discount_rate`, `free_shipping_count`, `benefits_json`, `status`, `sort`)
SELECT '普通会员', 1, 0, 100, 0, '["社区评论","积分兑换"]', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `mall_member_level` WHERE `deleted` = 0);

INSERT INTO `mall_member_level` (`name`, `level_no`, `growth_threshold`, `discount_rate`, `free_shipping_count`, `benefits_json`, `status`, `sort`)
SELECT '银卡会员', 2, 500, 98, 1, '["9.8折","每月1次包邮","专属客服"]', 1, 2
WHERE NOT EXISTS (SELECT 1 FROM `mall_member_level` WHERE `name` = '银卡会员' AND `deleted` = 0);

INSERT INTO `mall_member_level` (`name`, `level_no`, `growth_threshold`, `discount_rate`, `free_shipping_count`, `benefits_json`, `status`, `sort`)
SELECT '金卡会员', 3, 2000, 95, 3, '["9.5折","每月3次包邮","生日礼包","专属客服"]', 1, 3
WHERE NOT EXISTS (SELECT 1 FROM `mall_member_level` WHERE `name` = '金卡会员' AND `deleted` = 0);

INSERT INTO `mall_member_level` (`name`, `level_no`, `growth_threshold`, `discount_rate`, `free_shipping_count`, `benefits_json`, `status`, `sort`)
SELECT '黑卡会员', 4, 5000, 90, 6, '["9折","每月6次包邮","生日礼包","优先发货","专属客服"]', 1, 4
WHERE NOT EXISTS (SELECT 1 FROM `mall_member_level` WHERE `name` = '黑卡会员' AND `deleted` = 0);

-- ============================================================
-- ===== V13 (marketing) END =====


-- ===== V15 review (REQ-P1-05) START =====
-- V15 评价增强：追评字段 / mall_review_useful 唯一键收紧为 (review_id, member_id) / useful_count 对账
-- 设计说明见 db/v15-review.sql 头注释（追评用 append_* 字段而非父行：uk_review_item 一项一评约束）。
SET NAMES utf8mb4;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_review' AND COLUMN_NAME = 'append_content');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_review` ADD COLUMN `append_content` varchar(500) DEFAULT NULL COMMENT ''追评内容（原评价后30天内可追评一次）'' AFTER `tags`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_review' AND COLUMN_NAME = 'append_time');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_review` ADD COLUMN `append_time` datetime DEFAULT NULL COMMENT ''追评时间'' AFTER `append_content`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_review' AND COLUMN_NAME = 'append_images');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_review` ADD COLUMN `append_images` varchar(1000) DEFAULT NULL COMMENT ''追评图片，逗号分隔'' AFTER `append_time`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `mall_review_useful` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `review_id` bigint NOT NULL COMMENT '评价ID 关联mall_review',
  `member_id` bigint NOT NULL COMMENT '点有用的会员ID',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记（0=已点有用 1=已取消）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_useful` (`review_id`, `member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价「有用」记录（一会员一评价至多一行，幂等）';

DELETE FROM `mall_review_useful` WHERE `deleted` = 1;

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_review_useful' AND INDEX_NAME = 'uk_review_useful' AND SEQ_IN_INDEX = 3);
SET @ddl := IF(@c > 0, 'ALTER TABLE `mall_review_useful` DROP INDEX `uk_review_useful`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_review_useful' AND INDEX_NAME = 'uk_review_useful' AND SEQ_IN_INDEX = 2);
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_review_useful` ADD UNIQUE KEY `uk_review_useful` (`review_id`, `member_id`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `mall_review` r
SET r.`useful_count` = (SELECT COUNT(*) FROM `mall_review_useful` u WHERE u.`review_id` = r.`id` AND u.`deleted` = 0)
WHERE EXISTS (SELECT 1 FROM `mall_review_useful` u WHERE u.`review_id` = r.`id`);
-- ===== V15 review (REQ-P1-05) END =====
-- ===== V15 aftersale (REQ-P1-06) START =====
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1) mall_refund 增列（幂等）
-- ------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'order_item_id');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_refund` ADD COLUMN `order_item_id` bigint DEFAULT NULL COMMENT ''V15 关联订单项ID（部分退款/换货指向的订单项）'' AFTER `member_id`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'quantity');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_refund` ADD COLUMN `quantity` int DEFAULT NULL COMMENT ''V15 换货数量（type=3）'' AFTER `order_item_id`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'exchange_sku_id');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_refund` ADD COLUMN `exchange_sku_id` bigint DEFAULT NULL COMMENT ''V15 换入SKU ID（type=3 换货，买家要换成的商品）'' AFTER `quantity`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'ship_time');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_refund` ADD COLUMN `ship_time` datetime DEFAULT NULL COMMENT ''V15 买家寄回时间（type=3）'' AFTER `exchange_sku_id`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'platform_status');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_refund` ADD COLUMN `platform_status` tinyint NOT NULL DEFAULT 0 COMMENT ''V15 平台介入：0无1仲裁中2已强制退款3已驳回'' AFTER `refund_status`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'platform_remark');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_refund` ADD COLUMN `platform_remark` varchar(255) DEFAULT NULL COMMENT ''V15 平台仲裁意见'' AFTER `platform_status`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'intervene_time');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_refund` ADD COLUMN `intervene_time` datetime DEFAULT NULL COMMENT ''V15 买家申请平台介入时间'' AFTER `platform_remark`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2) 枚举注释对齐（type/status 扩值后同步列 COMMENT，幂等：注释不含新枚举才执行）
-- ------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'type' AND COLUMN_COMMENT NOT LIKE '%3换货%');
SET @ddl := IF(@c > 0, 'ALTER TABLE `mall_refund` MODIFY COLUMN `type` tinyint NOT NULL DEFAULT 1 COMMENT ''售后类型:1仅退款2退货退款3换货4部分退款''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND COLUMN_NAME = 'status' AND COLUMN_COMMENT NOT LIKE '%6买家已寄回%');
SET @ddl := IF(@c > 0, 'ALTER TABLE `mall_refund` MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT ''状态:1待审核2已同意3已拒绝4已退款5已关闭6买家已寄回7商家已收货8已换货''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3) 索引（幂等）：平台仲裁队列扫描
-- ------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_refund' AND INDEX_NAME = 'idx_refund_platform');
SET @ddl := IF(@c = 0, 'ALTER TABLE `mall_refund` ADD INDEX `idx_refund_platform` (`platform_status`, `status`, `create_time`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- ===== V15 aftersale (REQ-P1-06) END =====


-- ===== V16 ai chat (REQ-P1-09) START =====
-- 5 张独立新表，无历史表改动，直接 IF NOT EXISTS 建表即可
CREATE TABLE IF NOT EXISTS mall_chat_session (
    id              BIGINT       NOT NULL COMMENT '主键',
    member_id       BIGINT       NOT NULL COMMENT '会员 ID',
    channel         VARCHAR(16)  NOT NULL DEFAULT 'pc' COMMENT '渠道：pc/h5',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0 机器人 1 已转人工 2 已结束',
    source          VARCHAR(64)  DEFAULT NULL COMMENT '进入来源：goods_detail/order_detail/home/float',
    ticket_id       BIGINT       DEFAULT NULL COMMENT '转人工后生成的工单 ID',
    satisfaction    TINYINT      DEFAULT NULL COMMENT '满意度 1-5，null 未评价',
    intent          VARCHAR(32)  DEFAULT NULL COMMENT '最近一次识别意图（运营分析用）',
    msg_count       INT          NOT NULL DEFAULT 0 COMMENT '消息条数',
    last_active_time DATETIME    DEFAULT NULL COMMENT '最后活跃时间（列表排序）',
    deleted         TINYINT      NOT NULL DEFAULT 0,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       BIGINT       DEFAULT NULL,
    update_by       BIGINT       DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_member (member_id, last_active_time),
    KEY idx_status (status, last_active_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'V16 AI 客服会话';

-- 2) 消息：角色/类型/取证轨迹/成本
CREATE TABLE IF NOT EXISTS mall_chat_message (
    id          BIGINT       NOT NULL COMMENT '主键',
    session_id  BIGINT       NOT NULL COMMENT '会话 ID',
    member_id   BIGINT       NOT NULL COMMENT '会员 ID（冗余，便于按人审计）',
    role        VARCHAR(16)  NOT NULL COMMENT 'user/ai/agent/system',
    msg_type    VARCHAR(24)  NOT NULL DEFAULT 'text' COMMENT 'text/order_card/goods_card/faq_suggest/transfer_card',
    content     VARCHAR(4000) NOT NULL COMMENT '文本内容',
    cards       TEXT         DEFAULT NULL COMMENT '卡片 JSON 数组（订单/商品卡片）',
    intent      VARCHAR(32)  DEFAULT NULL COMMENT '本条识别意图',
    tool_trace  VARCHAR(2000) DEFAULT NULL COMMENT '工具取证轨迹 JSON（可审计，不含 PII 明文）',
    fallback    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否兜底话术（1=无证据兜底）',
    provider    VARCHAR(32)  DEFAULT NULL COMMENT '实际使用的 LLM 提供方：mock/openai/ollama/faq',
    tokens      INT          NOT NULL DEFAULT 0 COMMENT '消耗 token 数（估算）',
    latency_ms  INT          NOT NULL DEFAULT 0 COMMENT '应答耗时',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   BIGINT       DEFAULT NULL,
    update_by   BIGINT       DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_session (session_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'V16 AI 客服消息';

-- 3) FAQ 知识库
CREATE TABLE IF NOT EXISTS mall_faq (
    id          BIGINT       NOT NULL COMMENT '主键',
    code        VARCHAR(48)  NOT NULL COMMENT '业务编码（幂等种子键）',
    question    VARCHAR(255) NOT NULL COMMENT '标准问题',
    answer      VARCHAR(2000) NOT NULL COMMENT '标准答案',
    category    VARCHAR(32)  NOT NULL DEFAULT 'general' COMMENT '分类：物流/售后/支付/优惠/会员/其他',
    synonyms    VARCHAR(500) DEFAULT NULL COMMENT '同义问法，英文逗号分隔',
    hit_count   BIGINT       NOT NULL DEFAULT 0 COMMENT '命中次数（运营优化依据）',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   BIGINT       DEFAULT NULL,
    update_by   BIGINT       DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'V16 AI 客服 FAQ 知识库';

-- 4) 消息级反馈
CREATE TABLE IF NOT EXISTS mall_chat_feedback (
    id          BIGINT       NOT NULL COMMENT '主键',
    message_id  BIGINT       NOT NULL COMMENT '消息 ID',
    session_id  BIGINT       NOT NULL COMMENT '会话 ID',
    member_id   BIGINT       NOT NULL COMMENT '会员 ID',
    rating      VARCHAR(8)   NOT NULL COMMENT 'up/down',
    reason      VARCHAR(255) DEFAULT NULL COMMENT '点踩原因',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   BIGINT       DEFAULT NULL,
    update_by   BIGINT       DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_member (message_id, member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'V16 AI 客服消息反馈';

-- 5) 日统计：用量与成本看板
CREATE TABLE IF NOT EXISTS mall_ai_stat_daily (
    id            BIGINT      NOT NULL COMMENT '主键',
    stat_date     DATE        NOT NULL COMMENT '统计日',
    session_count INT         NOT NULL DEFAULT 0 COMMENT '会话数',
    message_count INT         NOT NULL DEFAULT 0 COMMENT '消息数',
    ai_reply_count INT        NOT NULL DEFAULT 0 COMMENT 'AI 回复条数',
    transfer_count INT        NOT NULL DEFAULT 0 COMMENT '转人工数',
    fallback_count INT        NOT NULL DEFAULT 0 COMMENT '兜底条数',
    tokens        BIGINT      NOT NULL DEFAULT 0 COMMENT 'token 消耗',
    deleted       TINYINT     NOT NULL DEFAULT 0,
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by     BIGINT      DEFAULT NULL,
    update_by     BIGINT      DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_date (stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'V16 AI 客服日统计';
-- ===== V16 ai chat (REQ-P1-09) END =====
