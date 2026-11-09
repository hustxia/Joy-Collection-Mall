package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.feign.dto.MemberBenefitBrief;
import com.mall.dto.MallMemberLevelSaveDTO;
import com.mall.entity.MallMemberLevel;

import java.util.List;
import java.util.Map;

/**
 * 会员等级与权益服务（V13）。
 *
 * <p>成长值来源统一走 {@link #grantGrowth}：消费（订单创建）、签到、评价三处都调它，
 * 于是「升级判定 + 等级日志」只有一份实现，不会出现「签到能升级、消费不升级」这种不一致。
 */
public interface MallMemberLevelService extends IService<MallMemberLevel> {

    /** 启用中的等级（按阈值升序） */
    List<MallMemberLevel> enabledLevels();

    /** 下单链路只要三个数：折扣率、剩余包邮次数、等级名 */
    MemberBenefitBrief benefitOf(Long memberId);

    /**
     * 增减成长值并按阈值自动升降级（写 mall_member_level_log）。
     *
     * @return 变动后成长值；会员不存在返回 0
     */
    int grantGrowth(Long memberId, long value, String reason);

    /** 扣一次包邮次数；本月次数已用完/等级不支持返回 false（调用方回落运费模板） */
    boolean consumeFreeShipping(Long memberId);

    /** C 端「我的等级」：当前等级 + 进度 + 全部等级权益 + 最近变更日志 */
    Map<String, Object> myLevel(Long memberId);

    /** 后台新增/修改等级 */
    void saveLevel(MallMemberLevelSaveDTO dto);

    /** 后台删除等级（有会员在用则拒绝，避免把人的 level_id 悬空） */
    void removeLevel(Long id);
}
