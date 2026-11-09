package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.MemberInnerClient;
import com.mall.entity.MallDistributor;
import com.mall.entity.MallInvitation;
import com.mall.mapper.MallDistributorMapper;
import com.mall.service.MallDistributorService;
import com.mall.service.MallInvitationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Random;

/**
 * 分销员 Service 实现
 */
@Service
public class MallDistributorServiceImpl extends ServiceImpl<MallDistributorMapper, MallDistributor> implements MallDistributorService {

    private final MallInvitationService invitationService;
    private final MemberInnerClient memberInnerClient;

    public MallDistributorServiceImpl(MallInvitationService invitationService, MemberInnerClient memberInnerClient) {
        this.invitationService = invitationService;
        this.memberInnerClient = memberInnerClient;
    }

    @Override
    public String apply(Long memberId) {
        MallDistributor existed = getByMember(memberId);
        if (existed != null && existed.getStatus() != 3) {
            return existed.getInviteCode();
        }
        if (existed != null && existed.getStatus() == 3) {
            existed.setStatus(0);
            existed.setRejectReason(null);
            existed.setInviteCode(genCode());
            updateById(existed);
            return existed.getInviteCode();
        }
        MallDistributor d = new MallDistributor();
        d.setMemberId(memberId);
        d.setInviteCode(genCode());
        d.setLevel(1);
        d.setStatus(0);
        d.setTotalCommission(0L);
        d.setSettled(0L);
        d.setWithdrawable(0L);
        save(d);
        return d.getInviteCode();
    }

    @Override
    @Transactional
    public void audit(Long id, boolean pass, String rejectReason) {
        MallDistributor d = getById(id);
        if (d == null) {
            throw new BusinessException(404, "分销员不存在");
        }
        if (pass) {
            if (!StringUtils.hasText(d.getInviteCode())) {
                d.setInviteCode(genCode());
            }
            d.setStatus(1);
            d.setRejectReason(null);
        } else {
            d.setStatus(3);
            d.setRejectReason(rejectReason);
        }
        updateById(d);
        try {
            com.mall.common.feign.dto.NoticePushDTO notice = new com.mall.common.feign.dto.NoticePushDTO();
            notice.setMemberId(d.getMemberId());
            notice.setType(1);
            notice.setTitle(pass ? "分销申请已通过" : "分销申请被驳回");
            notice.setContent(rejectReason);
            notice.setBizId(String.valueOf(id));
            memberInnerClient.pushNotice(notice);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void enable(Long id) {
        MallDistributor d = getById(id);
        if (d == null) {
            throw new BusinessException(404, "分销员不存在");
        }
        d.setStatus(1);
        updateById(d);
    }

    @Override
    public void disable(Long id) {
        MallDistributor d = getById(id);
        if (d == null) {
            throw new BusinessException(404, "分销员不存在");
        }
        d.setStatus(2);
        updateById(d);
    }

    @Override
    @Transactional
    public void bind(Long memberId, String inviteCode, String channel) {
        if (!StringUtils.hasText(inviteCode)) {
            throw new BusinessException(400, "邀请码不能为空");
        }
        MallDistributor inviter = getByInviteCode(inviteCode);
        if (inviter == null) {
            throw new BusinessException(400, "邀请码无效");
        }
        if (inviter.getMemberId().equals(memberId)) {
            throw new BusinessException(400, "不能邀请自己");
        }
        if (inviter.getStatus() != 1) {
            throw new BusinessException(400, "邀请人未启用分销");
        }
        MallInvitation existed = invitationService.getOne(new LambdaQueryWrapper<MallInvitation>()
                .eq(MallInvitation::getInviteeId, memberId)
                .eq(MallInvitation::getDeleted, 0));
        if (existed != null) {
            throw new BusinessException(400, "已绑定邀请关系，不可更改");
        }
        MallInvitation inv = new MallInvitation();
        inv.setInviterId(inviter.getId());
        inv.setInviteeId(memberId);
        inv.setChannel(channel);
        inv.setLevel(1);
        inv.setOrderCount(0);
        inv.setContribution(0L);
        invitationService.save(inv);
    }

    @Override
    public MallDistributor getByMember(Long memberId) {
        return getOne(new LambdaQueryWrapper<MallDistributor>()
                .eq(MallDistributor::getMemberId, memberId)
                .eq(MallDistributor::getDeleted, 0));
    }

    @Override
    public MallDistributor getByInviteCode(String inviteCode) {
        return getOne(new LambdaQueryWrapper<MallDistributor>()
                .eq(MallDistributor::getInviteCode, inviteCode)
                .eq(MallDistributor::getDeleted, 0));
    }

    private final Random random = new Random();

    private String genCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            String code = sb.toString();
            if (getByInviteCode(code) == null) {
                return code;
            }
        }
        throw new BusinessException(500, "生成邀请码失败，请重试");
    }
}
