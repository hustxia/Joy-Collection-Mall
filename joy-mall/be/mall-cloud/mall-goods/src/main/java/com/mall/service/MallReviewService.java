package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallReview;

import java.math.BigDecimal;

/**
 * 商品评价 Service
 */
public interface MallReviewService extends IService<MallReview> {

    /** 商品有效评价（status=0）平均分：保留 1 位小数，无评价返回 0 */
    BigDecimal avgRatingOf(Long goodsId);

    /** 商品有效评价（status=0）总数 */
    int countOf(Long goodsId);

    /**
     * 追评（V15 REQ-P1-05）：原评价后 30 天内且未追评过才允许，写 append_content/append_time/append_images。
     *
     * @return 追评后的评价
     */
    MallReview append(Long reviewId, Long memberId, String content, String images);

    /**
     * 点/取消有用（V15 REQ-P1-05）：uk_review_useful(review_id, member_id) 保证同一会员至多计一次。
     *
     * @param liked true=点有用 false=取消
     * @return 重算后的有用数
     */
    int toggleUseful(Long reviewId, Long memberId, boolean liked);

    /** 评价的有用数（与明细表对账口径） */
    int usefulCountOf(Long reviewId);

    /** 当前会员是否已点该评价的有用 */
    boolean likedBy(Long reviewId, Long memberId);
}
