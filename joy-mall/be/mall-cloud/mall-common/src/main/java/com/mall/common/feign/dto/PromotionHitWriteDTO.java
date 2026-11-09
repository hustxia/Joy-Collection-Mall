package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 促销命中写库请求（V13，订单域 → 营销域）：订单创建成功后按订单回写 mall_promotion_hit。
 *
 * <p>为什么由订单域决定金额：跨店拆单后一个活动可能同时命中多笔订单，整单优惠必须按店铺
 * 归属拆开，否则「各订单优惠之和 ≠ 活动总优惠」，对账时无法解释差额。营销域只负责落库。
 */
@Data
public class PromotionHitWriteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<Row> rows = new ArrayList<>();

    @Data
    public static class Row implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long promotionId;
        private String promotionName;
        private Long orderId;
        private Long memberId;
        /** 该订单分摊到的优惠金额（分） */
        private Long discountAmount;
    }
}
