package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 会员每日签到（V7 手写）：同一会员同一日期只保留一行（uk_sign_member_date 含 deleted）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_sign_in")
public class MallSignIn extends BaseEntity {

    /**
     * 会员ID 关联mall_member
     */
    private Long memberId;

    /**
     * 签到日期
     */
    private LocalDate signDate;

    /**
     * 当日连续天数
     */
    private Integer continuousDays;

    /**
     * 本次赠送积分
     */
    private Integer points;
}
