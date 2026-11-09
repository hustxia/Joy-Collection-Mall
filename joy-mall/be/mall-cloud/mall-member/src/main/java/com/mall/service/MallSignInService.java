package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallSignIn;

import java.time.YearMonth;
import java.util.List;

/**
 * 会员每日签到 Service（V7 手写）
 */
public interface MallSignInService extends IService<MallSignIn> {

    /**
     * 当日签到：今日已签抛业务异常；连续天数 = 昨天有签到记录 ? 昨天连续天数+1 : 1；
     * 赠送积分 = min(连续天数, 7)，同步写积分流水（changeType=2）。
     */
    MallSignIn signIn(Long memberId);

    /**
     * 指定月份已签日期列表（升序）。
     */
    List<MallSignIn> daysOf(Long memberId, YearMonth month);

    /**
     * 今天是否已签。
     */
    boolean todaySigned(Long memberId);
}
