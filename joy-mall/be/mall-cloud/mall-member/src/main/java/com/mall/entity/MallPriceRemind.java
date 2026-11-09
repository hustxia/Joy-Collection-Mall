package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 降价提醒订阅（V13 购物车营销）。
 *
 * <p>幂等口径：{@code uk_price_remind_member_goods(member_id, goods_id, sku_id, deleted)} +
 * 状态机（0 监控中 → 1 已通知）条件更新，保证「同一会员同一商品只触发一次」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_price_remind")
public class MallPriceRemind extends BaseEntity {

    /** 监控中 */
    public static final int STATUS_WATCHING = 0;
    /** 已通知 */
    public static final int STATUS_NOTIFIED = 1;
    /** 已失效（商品下架等） */
    public static final int STATUS_INVALID = 2;

    private Long memberId;

    private Long goodsId;

    /** 0=盯商品最低价 */
    private Long skuId;

    /** 期望价（分） */
    private Long expectedPrice;

    /** 订阅时价格（分） */
    private Long currentPrice;

    /** 状态：0监控中1已通知2已失效 */
    private Integer status;

    /** 通知时间 */
    private LocalDateTime notifyTime;
}
