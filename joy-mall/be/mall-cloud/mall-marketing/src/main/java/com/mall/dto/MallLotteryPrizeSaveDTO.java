package com.mall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 奖品池批量保存 DTO：整池替换（先按活动逻辑删再批量插）。
 */
@Data
public class MallLotteryPrizeSaveDTO {

    @NotNull(message = "活动ID必填")
    private Long activityId;

    @NotNull(message = "奖品列表必填")
    private List<PrizeItem> prizes;

    /** 单个奖品（整池替换，id 一律忽略） */
    @Data
    public static class PrizeItem {

        /** 奖品类型：0积分1优惠券2实物3谢谢参与 */
        @NotNull(message = "奖品类型必填")
        private Integer prizeType;

        @NotNull(message = "奖品名称必填")
        private String name;

        /** 关联ID：积分=分值，优惠券=券ID，实物=商品ID，谢谢参与=NULL */
        private Long refId;

        @NotNull(message = "权重必填")
        private Integer weight;

        /** 库存：-1不限量，>=0限量，缺省 -1 */
        private Integer stock;

        /** 展示顺序，缺省 0 */
        private Integer sort;
    }
}
