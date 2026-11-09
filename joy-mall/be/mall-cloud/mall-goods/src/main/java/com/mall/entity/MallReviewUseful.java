package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评价「有用」记录（V15 REQ-P1-05）。
 *
 * <p>唯一键 uk_review_useful(review_id, member_id)：一会员一评价至多一行。
 * 点有用 = 插入或复活（deleted 0→1 翻转），取消 = 逻辑删除，天然幂等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_review_useful")
public class MallReviewUseful extends BaseEntity {

    /**
     * 评价ID
     */
    private Long reviewId;

    /**
     * 点有用的会员ID
     */
    private Long memberId;
}
