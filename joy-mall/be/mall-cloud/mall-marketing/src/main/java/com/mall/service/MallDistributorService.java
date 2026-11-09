package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallDistributor;

/**
 * 分销员 Service
 */
public interface MallDistributorService extends IService<MallDistributor> {

    /** 申请成为分销员，返回专属邀请码（幂等：已申请且未驳回直接返回原码） */
    String apply(Long memberId);

    /** 后台审核：通过→启用（补发邀请码），驳回→填原因 */
    void audit(Long id, boolean pass, String rejectReason);

    /** 启用 */
    void enable(Long id);

    /** 停用 */
    void disable(Long id);

    /** 绑定邀请关系：memberId 通过 inviteCode 绑定上级（防自邀/防重复绑定） */
    void bind(Long memberId, String inviteCode, String channel);

    /** 按会员查分销员（含已逻辑删除之外的有效记录） */
    MallDistributor getByMember(Long memberId);

    /** 按邀请码查分销员 */
    MallDistributor getByInviteCode(String inviteCode);
}
