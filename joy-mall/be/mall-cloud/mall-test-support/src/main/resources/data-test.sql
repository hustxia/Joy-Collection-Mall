-- 测试库种子：只放「后台链路必须存在的管理员身份」（下单/审核/发货用例要签 admin token）
-- 全部 INSERT IGNORE，重复执行安全；业务数据一律由用例自建自清
INSERT IGNORE INTO sys_user (id, username, password, password_algo, nickname)
VALUES (1, 'admin', '$2a$10$BQz.XdhOV7sJOpH9z/BeheARd01UQKdchBsEgPfa.rYgmwTh75PaO', 'bcrypt', '管理员');

INSERT IGNORE INTO sys_role (id, role_name, role_key, remark)
VALUES (1, '超级管理员', 'admin', '内置角色：接口权限点直通');

INSERT IGNORE INTO sys_user_role (user_id, role_id)
VALUES (1, 1);
