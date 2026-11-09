package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.OrderInnerClient;
import com.mall.common.result.Result;
import com.mall.entity.MallReview;
import com.mall.service.MallReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C 端商品评价（V7）：提交评价 / 商品评价列表 / 我的评价。
 *
 * <p>三个接口都要会员 token（/shop 写接口不在匿名白名单内），memberId 只从 UserContext 取。
 * 「是否可评」由订单域 /inner/order-item/can-review 判定（归属本人 + 订单完成态 + 未评过），
 * 订单项快照字段（orderId/goodsId/skuId）随请求传入、经 canReview 校验真实性后落库；
 * member_nickname 由前端随单传入（商品域不回调会员域，避免级联不可用），缺省空串。
 */
@Tag(name = "商城前台-商品评价", description = "评价提交与浏览")
@RestController
@RequestMapping("/shop/review")
public class ShopReviewController {

    private final MallReviewService mallReviewService;
    private final OrderInnerClient orderInnerClient;

    public ShopReviewController(MallReviewService mallReviewService, OrderInnerClient orderInnerClient) {
        this.mallReviewService = mallReviewService;
        this.orderInnerClient = orderInnerClient;
    }

    @Operation(summary = "提交评价（订单项可评校验 + 一项一评）")
    @PostMapping
    public Result<Void> submit(@RequestBody SubmitDTO dto) {
        if (dto == null || dto.getOrderItemId() == null || dto.getOrderId() == null
                || dto.getGoodsId() == null || dto.getSkuId() == null) {
            throw new BusinessException(400, "参数错误：orderItemId/orderId/goodsId/skuId 必填");
        }
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new BusinessException(400, "评分须为 1~5");
        }
        Long memberId = UserContext.getUserId();
        Boolean canReview = orderInnerClient.canReview(dto.getOrderItemId(), memberId);
        if (!Boolean.TRUE.equals(canReview)) {
            throw new BusinessException(400, "该商品不可评价");
        }
        MallReview review = new MallReview();
        review.setOrderId(dto.getOrderId());
        review.setOrderItemId(dto.getOrderItemId());
        review.setGoodsId(dto.getGoodsId());
        review.setSkuId(dto.getSkuId());
        review.setMemberId(memberId);
        review.setMemberNickname(dto.getMemberNickname() == null ? "" : dto.getMemberNickname());
        review.setRating(dto.getRating());
        review.setContent(dto.getContent() == null ? "" : dto.getContent());
        review.setImages(dto.getImages() == null ? "" : dto.getImages());
        review.setStatus(0);
        try {
            mallReviewService.save(review);
        } catch (DuplicateKeyException e) {
            // uk_review_item(order_item_id, deleted)：并发/重复提交兜底
            throw new BusinessException(400, "已评价过");
        }
        return Result.ok();
    }

    @Operation(summary = "商品评价分页（status=0，可按标签筛选）+ 聚合 avg/total")
    @GetMapping("/goods/{goodsId}")
    public Result<Map<String, Object>> goodsReviews(@PathVariable("goodsId") Long goodsId,
                                                    @RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "10") long pageSize,
                                                    @RequestParam(required = false) String tag) {
        LambdaQueryWrapper<MallReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallReview::getGoodsId, goodsId)
                .eq(MallReview::getStatus, 0);
        // 标签为逗号分隔存储：FIND_IN_SET 精确匹配单个标签（tags 值本身不含逗号歧义）
        if (StringUtils.hasText(tag)) {
            wrapper.apply("FIND_IN_SET({0}, tags)", tag.trim());
        }
        wrapper.orderByDesc(MallReview::getId);
        Page<MallReview> page = mallReviewService.page(new Page<>(pageNum, Math.min(pageSize, 60)), wrapper);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", page.getRecords());
        data.put("total", page.getTotal());
        data.put("avg", mallReviewService.avgRatingOf(goodsId));
        return Result.ok(data);
    }

    @Operation(summary = "我的评价分页")
    @GetMapping("/my")
    public Result<Page<MallReview>> my(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize) {
        LambdaQueryWrapper<MallReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallReview::getMemberId, UserContext.getUserId())
                .orderByDesc(MallReview::getId);
        return Result.ok(mallReviewService.page(new Page<>(pageNum, Math.min(pageSize, 60)), wrapper));
    }

    // ===== V15 评价增强（REQ-P1-05） =====

    @Operation(summary = "评价详情（含追评/有用数/标签；带 token 时附 likedByMe）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        MallReview review = mallReviewService.getById(id);
        if (review == null) {
            throw new BusinessException(400, "评价不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("review", review);
        data.put("usefulCount", mallReviewService.usefulCountOf(id));
        Long memberId = UserContext.getUserId();
        data.put("likedByMe", memberId == null ? null : mallReviewService.likedBy(id, memberId));
        return Result.ok(data);
    }

    @Operation(summary = "追评（原评价后 30 天内，仅一次；验收：详情展示两条并标注「追评」）")
    @PostMapping("/append")
    public Result<Void> append(@RequestBody AppendDTO dto) {
        if (dto == null || dto.getReviewId() == null || !StringUtils.hasText(dto.getContent())) {
            throw new BusinessException(400, "参数错误：reviewId/content 必填");
        }
        mallReviewService.append(dto.getReviewId(), UserContext.getUserId(), dto.getContent(), dto.getImages());
        return Result.ok();
    }

    @Operation(summary = "点/取消有用（同一会员幂等：重复点只计一次）")
    @PostMapping("/useful")
    public Result<Map<String, Object>> useful(@RequestBody UsefulDTO dto) {
        if (dto == null || dto.getReviewId() == null || dto.getLiked() == null) {
            throw new BusinessException(400, "参数错误：reviewId/liked 必填");
        }
        Long memberId = UserContext.getUserId();
        int count = mallReviewService.toggleUseful(dto.getReviewId(), memberId, dto.getLiked());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("usefulCount", count);
        data.put("liked", mallReviewService.likedBy(dto.getReviewId(), memberId));
        return Result.ok(data);
    }

    @Data
    public static class SubmitDTO {
        private Long orderItemId;
        private Long orderId;
        private Long goodsId;
        private Long skuId;
        private Integer rating;
        private String content;
        /** 逗号分隔的图片 URL */
        private String images;
        /** 展示昵称：由前端随单传入（商品域不回调会员域），缺省空串 */
        private String memberNickname;
    }

    @Data
    public static class AppendDTO {
        /** 要追评的原评价 ID */
        private Long reviewId;
        private String content;
        /** 逗号分隔的图片 URL（晒单追评可选） */
        private String images;
    }

    @Data
    public static class UsefulDTO {
        private Long reviewId;
        /** true=点有用 false=取消 */
        private Boolean liked;
    }
}
