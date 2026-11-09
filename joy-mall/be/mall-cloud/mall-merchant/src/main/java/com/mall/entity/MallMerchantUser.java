package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商家账号（V11）：一个商家可开多个账号，与平台 {@code sys_user} 完全隔离。
 *
 * <p>登录令牌的 username 记为 {@code mt:{账号ID}:{商家ID}}，网关与服务侧都靠这个前缀
 * 把它和平台会话、会员会话区分开——三套令牌互不通用是硬约束，不是约定。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_merchant_user")
public class MallMerchantUser extends BaseEntity {

    /** 角色：店主（全部权限） */
    public static final String ROLE_OWNER = "owner";
    /** 角色：店员（只做发品/发货/售后） */
    public static final String ROLE_STAFF = "staff";

    /** 商家ID 关联 mall_merchant */
    private Long merchantId;

    /** 登录账号（全局唯一） */
    private String username;

    /** 密码哈希 */
    private String password;

    /** 密码算法：bcrypt / sha256 */
    private String passwordAlgo;

    /** 真实姓名 */
    private String realName;

    /** 角色：owner / staff */
    private String role;

    /** 状态：1启用0停用 */
    private Integer status;

    /** 连续失败次数 */
    private Integer failCount;

    /** 锁定截止时间 */
    private LocalDateTime lockUntil;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;
}
