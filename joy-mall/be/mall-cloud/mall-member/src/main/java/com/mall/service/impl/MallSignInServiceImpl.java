package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.entity.MallSignIn;
import com.mall.mapper.MallSignInMapper;
import com.mall.service.MallMemberPointLogService;
import com.mall.service.MallSignInService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 会员每日签到 Service（V7 手写）
 */
@Service
public class MallSignInServiceImpl extends ServiceImpl<MallSignInMapper, MallSignIn> implements MallSignInService {

    /** 连签奖励上限：连续 7 天及以上每天固定 7 分 */
    private static final int MAX_SIGN_POINTS = 7;

    private final MallMemberPointLogService pointLogService;

    public MallSignInServiceImpl(MallMemberPointLogService pointLogService) {
        this.pointLogService = pointLogService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallSignIn signIn(Long memberId) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<MallSignIn> todayQuery = new LambdaQueryWrapper<>();
        todayQuery.eq(MallSignIn::getMemberId, memberId).eq(MallSignIn::getSignDate, today);
        if (count(todayQuery) > 0) {
            throw new BusinessException(400, "今日已签到");
        }
        LambdaQueryWrapper<MallSignIn> yesterdayQuery = new LambdaQueryWrapper<>();
        yesterdayQuery.eq(MallSignIn::getMemberId, memberId)
                .eq(MallSignIn::getSignDate, today.minusDays(1));
        MallSignIn yesterday = getOne(yesterdayQuery);
        int continuousDays = yesterday == null ? 1 : yesterday.getContinuousDays() + 1;
        int points = Math.min(continuousDays, MAX_SIGN_POINTS);

        MallSignIn signIn = new MallSignIn();
        signIn.setMemberId(memberId);
        signIn.setSignDate(today);
        signIn.setContinuousDays(continuousDays);
        signIn.setPoints(points);
        save(signIn);
        // 签到记录与积分流水同一事务：任一失败整体回滚，不出现「签了没给分」
        pointLogService.grant(memberId, 2, points, null, "每日签到");
        return signIn;
    }

    @Override
    public List<MallSignIn> daysOf(Long memberId, YearMonth month) {
        LambdaQueryWrapper<MallSignIn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallSignIn::getMemberId, memberId)
                .between(MallSignIn::getSignDate, month.atDay(1), month.atEndOfMonth())
                .orderByAsc(MallSignIn::getSignDate);
        return list(wrapper);
    }

    @Override
    public boolean todaySigned(Long memberId) {
        LambdaQueryWrapper<MallSignIn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallSignIn::getMemberId, memberId).eq(MallSignIn::getSignDate, LocalDate.now());
        return count(wrapper) > 0;
    }
}
