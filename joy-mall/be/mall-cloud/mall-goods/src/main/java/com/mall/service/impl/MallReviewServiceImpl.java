package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.entity.MallReview;
import com.mall.entity.MallReviewUseful;
import com.mall.mapper.MallReviewMapper;
import com.mall.mapper.MallReviewUsefulMapper;
import com.mall.service.MallReviewService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 商品评价 Service
 */
@Service
public class MallReviewServiceImpl extends ServiceImpl<MallReviewMapper, MallReview> implements MallReviewService {

    /** 追评窗口：原评价后 30 天内（REQ-P1-05） */
    private static final Duration APPEND_WINDOW = Duration.ofDays(30);

    private final JdbcTemplate jdbcTemplate;
    private final MallReviewUsefulMapper usefulMapper;

    public MallReviewServiceImpl(JdbcTemplate jdbcTemplate, MallReviewUsefulMapper usefulMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.usefulMapper = usefulMapper;
    }

    @Override
    public BigDecimal avgRatingOf(Long goodsId) {
        BigDecimal avg = jdbcTemplate.queryForObject(
                "SELECT IFNULL(ROUND(AVG(rating), 1), 0) FROM mall_review"
                        + " WHERE goods_id = ? AND status = 0 AND deleted = 0",
                BigDecimal.class, goodsId);
        return avg == null ? BigDecimal.ZERO : avg;
    }

    @Override
    public int countOf(Long goodsId) {
        LambdaQueryWrapper<MallReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallReview::getGoodsId, goodsId).eq(MallReview::getStatus, 0);
        return Math.toIntExact(count(wrapper));
    }

    // ===== V15 评价增强（REQ-P1-05） =====

    @Override
    @Transactional
    public MallReview append(Long reviewId, Long memberId, String content, String images) {
        MallReview review = getById(reviewId);
        if (review == null) {
            throw new BusinessException(400, "评价不存在");
        }
        if (!review.getMemberId().equals(memberId)) {
            throw new BusinessException(403, "只能追评自己的评价");
        }
        if (review.getAppendTime() != null) {
            throw new BusinessException(400, "已追评过（每条评价仅可追评一次）");
        }
        if (review.getCreateTime() == null
                || review.getCreateTime().plus(APPEND_WINDOW).isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "已过追评期（原评价后 30 天内可追评）");
        }
        review.setAppendContent(content == null ? "" : content);
        review.setAppendImages(images == null ? "" : images);
        review.setAppendTime(LocalDateTime.now());
        updateById(review);
        return review;
    }

    @Override
    @Transactional
    public int toggleUseful(Long reviewId, Long memberId, boolean liked) {
        MallReview review = getById(reviewId);
        if (review == null) {
            throw new BusinessException(400, "评价不存在");
        }
        LambdaQueryWrapper<MallReviewUseful> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallReviewUseful::getReviewId, reviewId).eq(MallReviewUseful::getMemberId, memberId);
        MallReviewUseful row = usefulMapper.selectOne(wrapper);
        if (liked) {
            if (row == null) {
                MallReviewUseful insert = new MallReviewUseful();
                insert.setReviewId(reviewId);
                insert.setMemberId(memberId);
                try {
                    usefulMapper.insert(insert);
                } catch (DuplicateKeyException e) {
                    // 唯一键兜底：取消后再点时明细行仍存在（deleted=1，@TableLogic 下 selectOne 不可见），
                    // 原生 SQL 复活，保证幂等不产生第二行
                    jdbcTemplate.update(
                            "UPDATE mall_review_useful SET deleted = 0 WHERE review_id = ? AND member_id = ?",
                            reviewId, memberId);
                }
            } else if (Integer.valueOf(1).equals(row.getDeleted())) {
                row.setDeleted(0);
                usefulMapper.updateById(row);
            }
            // row 已存在且未删除：重复点赞，幂等不重复计数
        } else if (row != null && Integer.valueOf(0).equals(row.getDeleted())) {
            usefulMapper.deleteById(row.getId());
        }
        int count = usefulCountOf(reviewId);
        // 冗余计数列与明细表对账同步（同事务，读侧免联查明细表）
        jdbcTemplate.update("UPDATE mall_review SET useful_count = ? WHERE id = ?", count, reviewId);
        return count;
    }

    @Override
    public int usefulCountOf(Long reviewId) {
        LambdaQueryWrapper<MallReviewUseful> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallReviewUseful::getReviewId, reviewId);
        return Math.toIntExact(usefulMapper.selectCount(wrapper));
    }

    @Override
    public boolean likedBy(Long reviewId, Long memberId) {
        LambdaQueryWrapper<MallReviewUseful> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallReviewUseful::getReviewId, reviewId).eq(MallReviewUseful::getMemberId, memberId);
        return usefulMapper.selectCount(wrapper) > 0;
    }
}
