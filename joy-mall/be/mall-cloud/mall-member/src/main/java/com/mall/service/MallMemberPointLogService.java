package com.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallMemberPointLog;

/**
 * 会员积分流水 Service（V7 手写）
 */
public interface MallMemberPointLogService extends IService<MallMemberPointLog> {

    /**
     * 发放/扣减积分：条件更新余额（并发下不会扣成负数），成功后写流水。
     *
     * @param changeType 1消费返 2签到 3下单抵扣 4退款扣回
     * @param points     正加负减，不为 0
     * @return 变动后的余额
     * @throws com.mall.common.exception.BusinessException 会员不存在或积分不足时抛出
     */
    int grant(Long memberId, Integer changeType, Integer points, Long orderId, String remark);

    /**
     * 当前会员流水分页（新→旧）。
     */
    Page<MallMemberPointLog> pageOf(Long memberId, int pageNum, int pageSize);
}
