package com.mall.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_member")
public class MallMember extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 微信openid
     */
    private String openid;

    /**
     * 微信unionid
     */
    private String unionid;

    /**
     * 性别:1男2女0未知
     */
    private Integer gender;

    /**
     * 注册来源:1小程序2H5
     */
    private Integer source;

    /**
     * 积分
     */
    private Integer points;

    /**
     * 状态:1正常0禁用
     */
    private Integer status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /**
     * 登录密码(BCrypt哈希)，V3 C 端认证新增列，生成器尚未感知故放手写区
     */
    private String password;

    /**
     * 密码算法:bcrypt
     */
    private String passwordAlgo;

    /**
     * 佣金余额（分），V9 分销新增列，生成器尚未感知故放手写区
     */
    private Long commissionBalance;

    /**
     * 注销状态：0 正常 / 1 冷静期 / 2 已注销。V11 合规新增列，见 db/v11-merchant-compliance.sql。
     * 用状态位而不是「删除会员」：订单、售后、资金流水都必须保留可查（member_id 不变）。
     */
    private Integer cancelStatus;

    /** 注销申请时间（V11 合规） */
    private LocalDateTime cancelApplyTime;

    /** 注销生效时间 = 申请时间 + 7 天冷静期（V11 合规） */
    private LocalDateTime cancelEffectiveTime;

    /**
     * 会员等级ID（V13），对应 mall_member_level.id；空=还没算过等级
     */
    private Long levelId;

    /**
     * 成长值（V13）：消费 1 元 = 1 点，签到/评价另有加成
     */
    private Long growth;

    /**
     * 本月已用包邮次数（V13）：跨月自动归零，见 free_shipping_month
     */
    private Integer freeShippingUsed;

    /**
     * 包邮次数统计月份 yyyy-MM（V13）
     */
    private String freeShippingMonth;
}