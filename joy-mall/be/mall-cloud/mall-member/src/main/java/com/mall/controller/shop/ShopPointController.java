package com.mall.controller.shop;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.entity.MallMember;
import com.mall.entity.MallMemberPointLog;
import com.mall.service.MallMemberPointLogService;
import com.mall.service.MallMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端会员积分（V7）：余额读 mall_member.points，流水读 mall_member_point_log。
 * memberId 只从 UserContext 取。
 */
@Tag(name = "商城前台-会员积分", description = "积分余额/流水查询")
@RestController
@RequestMapping("/shop/point")
public class ShopPointController {

    private static final int MAX_PAGE_SIZE = 100;

    private final MallMemberService mallMemberService;
    private final MallMemberPointLogService pointLogService;

    public ShopPointController(MallMemberService mallMemberService, MallMemberPointLogService pointLogService) {
        this.mallMemberService = mallMemberService;
        this.pointLogService = pointLogService;
    }

    @Operation(summary = "当前积分余额")
    @GetMapping("/balance")
    public Result<Map<String, Object>> balance() {
        MallMember member = mallMemberService.getById(requireMemberId());
        int points = member == null || member.getPoints() == null ? 0 : member.getPoints();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("points", points);
        return Result.ok(data);
    }

    @Operation(summary = "积分流水分页（新→旧）")
    @GetMapping("/logs")
    public Result<Map<String, Object>> logs(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Long memberId = requireMemberId();
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<MallMemberPointLog> page = pointLogService.pageOf(memberId, pageNum, size);

        List<Map<String, Object>> rows = page.getRecords().stream().map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(log.getId()));
            row.put("changeType", log.getChangeType());
            row.put("points", log.getPoints());
            row.put("balanceAfter", log.getBalanceAfter());
            row.put("orderId", log.getOrderId() == null ? null : String.valueOf(log.getOrderId()));
            row.put("remark", log.getRemark());
            row.put("createTime", log.getCreateTime());
            return row;
        }).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", page.getTotal());
        return Result.ok(data);
    }

    private Long requireMemberId() {
        Long memberId = UserContext.getUserId();
        if (memberId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return memberId;
    }
}
