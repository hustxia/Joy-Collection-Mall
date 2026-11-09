package com.mall.controller.shop;

import com.mall.common.context.UserContext;
import com.mall.common.result.Result;
import com.mall.service.MallMemberComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 会员合规自助（V11）：注销申请/撤销/状态、个人信息导出。
 *
 * <p>全部接口都只认会员令牌里的 memberId——不接受前端传 memberId，
 * 否则「导出他人信息」只需要改一个参数。
 */
@Tag(name = "商城前台-合规", description = "账号注销与个人信息导出")
@RestController
@RequestMapping("/shop/member")
public class ShopMemberComplianceController {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MallMemberComplianceService complianceService;

    public ShopMemberComplianceController(MallMemberComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    @Operation(summary = "注销申请：进入 7 天冷静期，期间可撤销")
    @PostMapping("/cancel/apply")
    public Result<Map<String, Object>> applyCancel() {
        return Result.ok(complianceService.applyCancel(UserContext.getUserId()));
    }

    @Operation(summary = "撤销注销：冷静期内有效")
    @PostMapping("/cancel/revoke")
    public Result<Map<String, Object>> revokeCancel() {
        return Result.ok(complianceService.revokeCancel(UserContext.getUserId()));
    }

    @Operation(summary = "注销状态（惰性执行到期注销）")
    @GetMapping("/cancel/status")
    public Result<Map<String, Object>> cancelStatus() {
        return Result.ok(complianceService.cancelStatus(UserContext.getUserId()));
    }

    @Operation(summary = "个人信息导出：默认 JSON，format=csv 导出可下载的 CSV")
    @GetMapping("/export")
    public ResponseEntity<Object> export(@RequestParam(value = "format", required = false) String format) {
        Long memberId = UserContext.getUserId();
        if ("csv".equalsIgnoreCase(format)) {
            byte[] bytes = complianceService.exportCsv(memberId).getBytes(StandardCharsets.UTF_8);
            String fileName = "member-export-" + memberId + "-" + FILE_TS.format(LocalDateTime.now()) + ".csv";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(bytes);
        }
        // JSON 走统一 Result 壳，与其他接口一致；CSV 是文件下载，不能再包一层
        return ResponseEntity.ok(Result.ok(complianceService.export(memberId)));
    }
}
