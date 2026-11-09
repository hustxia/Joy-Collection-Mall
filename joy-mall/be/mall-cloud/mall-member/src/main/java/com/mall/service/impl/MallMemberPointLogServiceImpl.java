package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.entity.MallMember;
import com.mall.entity.MallMemberPointLog;
import com.mall.mapper.MallMemberMapper;
import com.mall.mapper.MallMemberPointLogMapper;
import com.mall.service.MallMemberPointLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员积分流水 Service（V7 手写）
 */
@Service
public class MallMemberPointLogServiceImpl extends ServiceImpl<MallMemberPointLogMapper, MallMemberPointLog>
        implements MallMemberPointLogService {

    private final MallMemberMapper mallMemberMapper;

    public MallMemberPointLogServiceImpl(MallMemberMapper mallMemberMapper) {
        this.mallMemberMapper = mallMemberMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grant(Long memberId, Integer changeType, Integer points, Long orderId, String remark) {
        MallMember member = mallMemberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(400, "会员不存在");
        }
        // 条件更新兜底并发：余额不足时影响行数 0，绝不扣成负数
        int affected = mallMemberMapper.addPoints(memberId, points);
        if (affected == 0) {
            throw new BusinessException(400, "积分不足");
        }
        Integer balance = mallMemberMapper.selectById(memberId).getPoints();
        int balanceAfter = balance == null ? 0 : balance;
        MallMemberPointLog pointLog = new MallMemberPointLog();
        pointLog.setMemberId(memberId);
        pointLog.setChangeType(changeType);
        pointLog.setPoints(points);
        pointLog.setBalanceAfter(balanceAfter);
        pointLog.setOrderId(orderId);
        pointLog.setRemark(remark == null ? "" : remark);
        save(pointLog);
        return balanceAfter;
    }

    @Override
    public Page<MallMemberPointLog> pageOf(Long memberId, int pageNum, int pageSize) {
        Page<MallMemberPointLog> page = new Page<>(Math.max(pageNum, 1), Math.max(pageSize, 1));
        LambdaQueryWrapper<MallMemberPointLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallMemberPointLog::getMemberId, memberId).orderByDesc(MallMemberPointLog::getId);
        return page(page, wrapper);
    }
}
