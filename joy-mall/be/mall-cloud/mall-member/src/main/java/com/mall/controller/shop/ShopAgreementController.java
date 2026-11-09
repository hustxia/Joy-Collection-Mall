package com.mall.controller.shop;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.entity.MallAgreement;
import com.mall.service.MallAgreementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 协议展示（V11 合规）：C 端匿名可读，登录/注册页与个人中心都要能打开。
 *
 * <p>返回正文 + 版本号：前端展示「用户协议 v1.0.0」，用户同意的是这个版本，
 * 后续发新版才能对比出「哪些人还没同意新版本」。
 */
@Tag(name = "商城前台-协议", description = "用户协议与隐私政策")
@RestController
@RequestMapping("/shop/agreement")
public class ShopAgreementController {

    private final MallAgreementService agreementService;

    public ShopAgreementController(MallAgreementService agreementService) {
        this.agreementService = agreementService;
    }

    @Operation(summary = "当前生效的用户协议与隐私政策（匿名）")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(agreementService.listPublished().stream().map(this::toRow).toList());
    }

    @Operation(summary = "协议正文（匿名）：type 1 用户协议 / 2 隐私政策")
    @GetMapping("/{type}")
    public Result<Map<String, Object>> detail(@PathVariable("type") Integer type) {
        if (type == null || (type != MallAgreement.TYPE_USER && type != MallAgreement.TYPE_PRIVACY)) {
            throw new BusinessException(400, "协议类型不正确");
        }
        MallAgreement agreement = agreementService.current(type);
        if (agreement == null) {
            throw new BusinessException(400, "协议暂未发布");
        }
        Map<String, Object> data = toRow(agreement);
        data.put("content", agreement.getContent());
        return Result.ok(data);
    }

    private Map<String, Object> toRow(MallAgreement agreement) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", String.valueOf(agreement.getId()));
        row.put("type", agreement.getType());
        row.put("typeText", MallAgreement.typeText(agreement.getType()));
        row.put("title", agreement.getTitle());
        row.put("version", agreement.getVersion());
        row.put("effectiveTime", agreement.getEffectiveTime());
        return row;
    }
}
