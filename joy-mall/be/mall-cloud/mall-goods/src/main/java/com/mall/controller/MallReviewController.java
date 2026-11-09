package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.dto.MallReviewPageDTO;
import com.mall.entity.MallReview;
import com.mall.service.MallReviewService;
import com.mall.vo.MallReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品评价（后台）：分页 / 回复 / 隐藏
 */
@Tag(name = "商品评价管理", description = "商品评价管理")
@RestController
@RequestMapping("/mall_review")
public class MallReviewController {

    @Autowired
    private MallReviewService mallReviewService;

    @Operation(summary = "分页查询商品评价（goodsId/rating/status 筛选）")
    @PostMapping("/page")
    public Result<Page<MallReviewVO>> page(@RequestBody MallReviewPageDTO dto) {
        Page<MallReview> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<MallReview> wrapper = new LambdaQueryWrapper<>();
        if (dto.getGoodsId() != null) {
            wrapper.eq(MallReview::getGoodsId, dto.getGoodsId());
        }
        if (dto.getRating() != null) {
            wrapper.eq(MallReview::getRating, dto.getRating());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(MallReview::getStatus, dto.getStatus());
        }
        // V15：按标签筛选（tags 逗号分隔，FIND_IN_SET 精确匹配）
        if (StringUtils.hasText(dto.getTag())) {
            wrapper.apply("FIND_IN_SET({0}, tags)", dto.getTag().trim());
        }
        wrapper.orderByDesc(MallReview::getId);
        mallReviewService.page(page, wrapper);
        Page<MallReviewVO> voPage = new Page<>();
        BeanUtils.copyProperties(page, voPage);
        List<MallReviewVO> records = page.getRecords().stream()
                .map(e -> {
                    MallReviewVO vo = new MallReviewVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        voPage.setRecords(records);
        return Result.ok(voPage);
    }

    @Operation(summary = "回复评价（写 reply + reply_time）")
    @Log(title = "商品评价", type = "UPDATE")
    @PostMapping("/reply")
    public Result<Void> reply(@RequestBody ReplyDTO dto) {
        if (dto == null || dto.getId() == null || !StringUtils.hasText(dto.getReply())) {
            throw new BusinessException(400, "参数错误：id/reply 必填");
        }
        MallReview review = mustExist(dto.getId());
        review.setReply(dto.getReply());
        review.setReplyTime(LocalDateTime.now());
        mallReviewService.updateById(review);
        return Result.ok();
    }

    @Operation(summary = "隐藏/恢复评价（body 带 status：0正常1隐藏）")
    @Log(title = "商品评价", type = "UPDATE")
    @PostMapping("/hide")
    public Result<Void> hide(@RequestBody HideDTO dto) {
        if (dto == null || dto.getId() == null
                || dto.getStatus() == null || (dto.getStatus() != 0 && dto.getStatus() != 1)) {
            throw new BusinessException(400, "参数错误：id 必填，status 只能 0/1");
        }
        MallReview review = mustExist(dto.getId());
        review.setStatus(dto.getStatus());
        mallReviewService.updateById(review);
        return Result.ok();
    }

    private MallReview mustExist(Long id) {
        MallReview review = mallReviewService.getById(id);
        if (review == null) {
            throw new BusinessException(400, "评价不存在");
        }
        return review;
    }

    @Data
    public static class ReplyDTO {
        private Long id;
        private String reply;
    }

    @Data
    public static class HideDTO {
        private Long id;
        private Integer status;
    }
}
