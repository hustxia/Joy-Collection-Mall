package com.mall.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价 VO
 */
@Data
public class MallReviewVO {

    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单项ID
     */
    private Long orderItemId;

    /**
     * 商品ID
     */
    private Long goodsId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 会员昵称
     */
    private String memberNickname;

    /**
     * 评分：1~5
     */
    private Integer rating;

    /**
     * 评价内容
     */
    private String content;

    /**
     * 评价图片，逗号分隔
     */
    private String images;

    /**
     * 有用数（V15）
     */
    private Integer usefulCount;

    /**
     * 评价标签，逗号分隔（V15）
     */
    private String tags;

    /**
     * 追评内容（V15）
     */
    private String appendContent;

    /**
     * 追评时间（V15）
     */
    private LocalDateTime appendTime;

    /**
     * 追评图片，逗号分隔（V15）
     */
    private String appendImages;

    /**
     * 状态：0正常1后台隐藏
     */
    private Integer status;

    /**
     * 后台回复
     */
    private String reply;

    /**
     * 回复时间
     */
    private LocalDateTime replyTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
