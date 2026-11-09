package com.mall.controller.shop;

import com.mall.common.context.UserContext;
import com.mall.common.result.Result;
import com.mall.service.MallMemberLevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端会员等级（V13）：我的等级 / 成长值进度 / 权益说明 / 全部等级阶梯。
 *
 * <p>memberId 只从 UserContext 取，不接受外部传参——等级权益是账号数据，越权读没有业务场景。
 */
@Tag(name = "商城前台-会员等级", description = "我的等级与成长值")
@RestController
@RequestMapping("/shop/member/level")
public class ShopMemberLevelController {

    private final MallMemberLevelService levelService;

    public ShopMemberLevelController(MallMemberLevelService levelService) {
        this.levelService = levelService;
    }

    @Operation(summary = "我的等级（成长值/进度/权益/全部等级）")
    @GetMapping
    public Result<Map<String, Object>> my() {
        return Result.ok(levelService.myLevel(UserContext.getUserId()));
    }
}
