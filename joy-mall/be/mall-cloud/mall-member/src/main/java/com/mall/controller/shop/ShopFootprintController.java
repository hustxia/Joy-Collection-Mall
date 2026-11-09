package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员足迹（V4 P2-2）：商品浏览记录，H5 / PC 会员中心共用。
 *
 * <p>身份只认 {@link UserContext#getUserId()}（会员 token 由 AuthContextFilter 解析成 {@code m:<memberId>}），
 * 入参不接收 memberId——避免越权读写别人的足迹。
 * 同一会员同一商品只保留一行（唯一键 uk_member_goods），再次浏览 upsert 刷新 view_time。
 */
@Tag(name = "商城前台-会员足迹", description = "浏览记录：记录 / 列表 / 清空")
@RestController
@RequestMapping("/shop/footprint")
public class ShopFootprintController {

    /** 列表单页上限，防止 pageSize=99999 拖垮 join */
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;

    public ShopFootprintController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Operation(summary = "记录一次商品浏览（同商品只保留最新一条，重复浏览仅刷新时间）")
    @PostMapping("/record")
    public Result<Void> record(@RequestBody RecordDTO dto) {
        Long memberId = requireMemberId();
        Long goodsId = parseId(dto == null ? null : dto.getGoodsId());
        if (goodsId == null) {
            throw new BusinessException(400, "商品ID不能为空");
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_goods WHERE id = ? AND deleted = 0", Integer.class, goodsId);
        if (exists == null || exists == 0) {
            // 商品已下架/删除：静默成功，足迹页不因脏数据报错
            return Result.ok();
        }
        jdbcTemplate.update(
                "INSERT INTO mall_member_footprint"
                        + " (id, member_id, goods_id, view_time, create_time, update_time, deleted)"
                        + " VALUES (?, ?, ?, NOW(), NOW(), NOW(), 0)"
                        + " ON DUPLICATE KEY UPDATE view_time = NOW(), update_time = NOW(), deleted = 0",
                IdWorker.getId(), memberId, goodsId);
        return Result.ok();
    }

    @Operation(summary = "我的足迹列表（按最近浏览倒序，带商品名/主图/价格）")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Long memberId = requireMemberId();
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int page = Math.max(pageNum, 1);
        int offset = (page - 1) * size;

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_member_footprint WHERE member_id = ? AND deleted = 0",
                Integer.class, memberId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT f.goods_id AS goodsId, f.view_time AS viewTime,"
                        + " g.name AS goodsName, g.main_image AS mainImage, g.price_min AS priceMin,"
                        + " g.status AS goodsStatus"
                        + " FROM mall_member_footprint f"
                        + " LEFT JOIN mall_goods g ON g.id = f.goods_id AND g.deleted = 0"
                        + " WHERE f.member_id = ? AND f.deleted = 0"
                        + " ORDER BY f.view_time DESC, f.id DESC"
                        + " LIMIT ? OFFSET ?",
                memberId, size, offset);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total == null ? 0 : total);
        return Result.ok(data);
    }

    @Operation(summary = "清空我的足迹（逻辑删）")
    @PostMapping("/clear")
    public Result<Map<String, Object>> clear() {
        Long memberId = requireMemberId();
        int affected = jdbcTemplate.update(
                "UPDATE mall_member_footprint SET deleted = 1, update_time = NOW()"
                        + " WHERE member_id = ? AND deleted = 0",
                memberId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cleared", affected);
        return Result.ok(data);
    }

    private Long requireMemberId() {
        Long memberId = UserContext.getUserId();
        if (memberId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return memberId;
    }

    /** 前端雪花 ID 以字符串传递，这里兼容字符串与数字 */
    private static Long parseId(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "商品ID格式不正确");
        }
    }

    /** 记录浏览入参：goodsId 用字符串接，避免前端雪花 ID 精度丢失 */
    @lombok.Data
    public static class RecordDTO {

        private String goodsId;
    }
}
