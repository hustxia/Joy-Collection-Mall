package com.mall.controller.shop;

import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.entity.MallSignIn;
import com.mall.service.MallSignInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端每日签到（V7）：签到送积分（连签天数越多送越多，上限 7 分/天），
 * memberId 只从 UserContext 取。
 */
@Tag(name = "商城前台-每日签到", description = "签到/签到日历")
@RestController
@RequestMapping("/shop/signin")
public class ShopSignInController {

    private final MallSignInService mallSignInService;

    public ShopSignInController(MallSignInService mallSignInService) {
        this.mallSignInService = mallSignInService;
    }

    @Operation(summary = "今日签到（重复签到 400；连签天数决定赠送积分，上限 7）")
    @PostMapping
    public Result<Map<String, Object>> signIn() {
        MallSignIn signIn = mallSignInService.signIn(requireMemberId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("continuousDays", signIn.getContinuousDays());
        data.put("points", signIn.getPoints());
        return Result.ok(data);
    }

    @Operation(summary = "签到日历：该月已签日期列表 + 今天是否已签")
    @GetMapping("/calendar")
    public Result<Map<String, Object>> calendar(@RequestParam("month") String month) {
        Long memberId = requireMemberId();
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "月份格式不正确，应为 yyyy-MM");
        }
        List<String> days = mallSignInService.daysOf(memberId, yearMonth).stream()
                .map(sign -> sign.getSignDate().toString())
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", days);
        data.put("todaySigned", mallSignInService.todaySigned(memberId));
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
