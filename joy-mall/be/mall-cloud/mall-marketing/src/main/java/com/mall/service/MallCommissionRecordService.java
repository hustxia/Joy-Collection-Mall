package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallCommissionRecord;

/**
 * 佣金记录 Service
 */
public interface MallCommissionRecordService extends IService<MallCommissionRecord> {

    /**
     * 订单完成结算佣金（幂等）：按规则计算金额 → 落待结算记录 → 累加分销员与会员余额。
     * 重复调用（订单完成事件重投）直接返回，不产生重复佣金。
     */
    void settle(Long distributorId, Long orderId, Long memberId, Long goodsId, Long payAmount);

    /**
     * 退款失效：将已结算佣金置为失效并回退分销员可提现与会员余额（复用 refund 状态机钩子）。
     */
    void invalid(Long orderId, Long distributorId);
}
