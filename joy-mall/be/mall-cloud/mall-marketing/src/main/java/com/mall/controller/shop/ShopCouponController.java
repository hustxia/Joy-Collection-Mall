package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.entity.MallCoupon;
import com.mall.entity.MallCouponRecord;
import com.mall.service.MallCouponRecordService;
import com.mall.service.MallCouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C 端优惠券（V3 P1）：领券中心/领券/我的券。
 * 防超发：received_count 条件更新（< total_count 才 +1）。
 */
@Tag(name = "商城前台-优惠券", description = "领券中心/我的券")
@RestController
@RequestMapping("/shop/coupon")
public class ShopCouponController {

    @Autowired
    private MallCouponService mallCouponService;
    @Autowired
    private MallCouponRecordService mallCouponRecordService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Operation(summary = "领券中心：上架且在有效期、未抢光的券")
    @GetMapping("/center")
    public Result<List<Map<String, Object>>> center() {
        Long memberId = UserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<MallCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallCoupon::getStatus, 1)
                .le(MallCoupon::getStartTime, now)
                .ge(MallCoupon::getEndTime, now)
                .orderByDesc(MallCoupon::getId);
        List<MallCoupon> coupons = mallCouponService.list(wrapper);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MallCoupon coupon : coupons) {
            int total = coupon.getTotalCount() == null ? 0 : coupon.getTotalCount();
            int received = coupon.getReceivedCount() == null ? 0 : coupon.getReceivedCount();
            if (received >= total) {
                continue;
            }
            LambdaQueryWrapper<MallCouponRecord> mine = new LambdaQueryWrapper<>();
            mine.eq(MallCouponRecord::getCouponId, coupon.getId())
                    .eq(MallCouponRecord::getMemberId, memberId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(coupon.getId()));
            m.put("name", coupon.getName());
            m.put("type", coupon.getType());
            m.put("amount", coupon.getAmount());
            m.put("thresholdAmount", coupon.getThresholdAmount());
            m.put("startTime", coupon.getStartTime());
            m.put("endTime", coupon.getEndTime());
            m.put("perLimit", coupon.getPerLimit());
            m.put("received", mallCouponRecordService.count(mine));
            m.put("left", total - received);
            rows.add(m);
        }
        return Result.ok(rows);
    }

    @Operation(summary = "领券：限领校验 + 条件更新防超发")
    @PostMapping("/receive")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> receive(@RequestBody ReceiveDTO dto) {
        if (dto == null || dto.getCouponId() == null) {
            throw new BusinessException(400, "参数错误：couponId 必填");
        }
        Long memberId = UserContext.getUserId();
        MallCoupon coupon = mallCouponService.getById(dto.getCouponId());
        LocalDateTime now = LocalDateTime.now();
        if (coupon == null || !Integer.valueOf(1).equals(coupon.getStatus())
                || (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime()))
                || (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime()))) {
            throw new BusinessException(400, "优惠券不在可领取期");
        }
        int perLimit = coupon.getPerLimit() == null ? 1 : coupon.getPerLimit();
        LambdaQueryWrapper<MallCouponRecord> mine = new LambdaQueryWrapper<>();
        mine.eq(MallCouponRecord::getCouponId, coupon.getId())
                .eq(MallCouponRecord::getMemberId, memberId);
        if (mallCouponRecordService.count(mine) >= perLimit) {
            throw new BusinessException(400, "已达每人限领数量");
        }
        int affected = jdbcTemplate.update(
                "UPDATE mall_coupon SET received_count = received_count + 1"
                        + " WHERE id = ? AND received_count < total_count AND deleted = 0",
                coupon.getId());
        if (affected == 0) {
            throw new BusinessException(400, "优惠券已抢光");
        }
        MallCouponRecord record = new MallCouponRecord();
        record.setCouponId(coupon.getId());
        record.setMemberId(memberId);
        record.setStatus(1);
        record.setReceivedTime(now);
        mallCouponRecordService.save(record);
        return Result.ok();
    }

    @Operation(summary = "我的券：status 1未用 2已用 3过期")
    @GetMapping("/my")
    public Result<List<Map<String, Object>>> my(@RequestParam(required = false) Integer status) {
        Long memberId = UserContext.getUserId();
        LambdaQueryWrapper<MallCouponRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallCouponRecord::getMemberId, memberId);
        if (status != null) {
            wrapper.eq(MallCouponRecord::getStatus, status);
        }
        wrapper.orderByDesc(MallCouponRecord::getId);
        List<MallCouponRecord> records = mallCouponRecordService.list(wrapper);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (records.isEmpty()) {
            return Result.ok(rows);
        }
        List<Long> couponIds = records.stream().map(MallCouponRecord::getCouponId).distinct().toList();
        Map<Long, MallCoupon> couponMap = mallCouponService.listByIds(couponIds).stream()
                .collect(Collectors.toMap(MallCoupon::getId, Function.identity()));
        for (MallCouponRecord record : records) {
            MallCoupon coupon = couponMap.get(record.getCouponId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordId", String.valueOf(record.getId()));
            m.put("couponId", String.valueOf(record.getCouponId()));
            m.put("name", coupon == null ? null : coupon.getName());
            m.put("amount", coupon == null ? null : coupon.getAmount());
            m.put("thresholdAmount", coupon == null ? null : coupon.getThresholdAmount());
            m.put("endTime", coupon == null ? null : coupon.getEndTime());
            m.put("status", record.getStatus());
            m.put("locked", record.getOrderId() != null && Integer.valueOf(1).equals(record.getStatus()));
            rows.add(m);
        }
        return Result.ok(rows);
    }

    @Data
    public static class ReceiveDTO {
        private Long couponId;
    }
}
