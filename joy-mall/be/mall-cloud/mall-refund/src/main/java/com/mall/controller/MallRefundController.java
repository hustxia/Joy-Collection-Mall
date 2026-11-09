package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.MallRefundPageDTO;
import com.mall.dto.MallRefundSaveDTO;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.dto.MemberBrief;
import com.mall.common.feign.dto.NoticePushDTO;
import com.mall.service.MallRefundService;
import com.mall.entity.MallRefund;
import com.mall.vo.MallRefundVO;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.common.log.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 售后退款单 Controller
 */
@Slf4j
@Tag(name = "售后退款单管理", description = "售后退款单管理")
@RestController
@RequestMapping("/mall_refund")
public class MallRefundController {

    // Service 注入无条件保留（理由见 import 区注释）
    @Autowired
    private MallRefundService mallRefundService;
    @Autowired
    private com.mall.common.util.DictTexts dictTexts;
    // <gen:begin key="controller-body">

@Operation(summary = "分页查询售后退款单")
@PostMapping("/page")
public Result<Page<MallRefundVO>> page(@RequestBody MallRefundPageDTO dto) {
    Page<MallRefund> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    LambdaQueryWrapper<MallRefund> wrapper = new LambdaQueryWrapper<>();
    // V4 售后闭环：列表页业务化查询条件（单号精确性高于模糊，但演示数据量小统一 like）
    if (StringUtils.hasText(dto.getRefundNo())) {
        wrapper.like(MallRefund::getRefundNo, dto.getRefundNo().trim());
    }
    if (StringUtils.hasText(dto.getOrderNo())) {
        wrapper.like(MallRefund::getOrderNo, dto.getOrderNo().trim());
    }
    if (dto.getStatus() != null) {
        wrapper.eq(MallRefund::getStatus, dto.getStatus());
    }
    if (dto.getType() != null) {
        wrapper.eq(MallRefund::getType, dto.getType());
    }
    applyTimeRange(wrapper, dto);
    wrapper.orderByDesc(MallRefund::getId);
    mallRefundService.page(page, wrapper);
    // 会员昵称/手机号批量回填（避免行内查询 N+1）
    Map<Long, MemberBrief> memberMap = memberMapOf(page.getRecords());
    Page<MallRefundVO> voPage = new Page<>();
    BeanUtils.copyProperties(page, voPage);
    List<MallRefundVO> records = page.getRecords().stream()
            .map(e -> {
                MallRefundVO vo = new MallRefundVO();
                BeanUtils.copyProperties(e, vo);
                MemberBrief member = memberMap.get(e.getMemberId());
                if (member != null) {
                    vo.setMemberNickname(member.getNickname());
                    vo.setMemberPhone(member.getPhone());
                }
                return vo;
            })
            .toList();
    voPage.setRecords(records);
    return Result.ok(voPage);
}

@Operation(summary = "导出售后退款单 Excel（按查询条件过滤；不传条件导出全部）")
@PostMapping("/export")
public ResponseEntity<byte[]> export(@RequestBody(required = false) MallRefundPageDTO query) throws IOException {
    // 复用列表页的筛选口径：导出的行必须和用户在页面上看到的一致，
    // 否则「筛出 20 条、导出 2000 条」会被当成 bug。
    LambdaQueryWrapper<MallRefund> wrapper = new LambdaQueryWrapper<>();
    // 与 page 同口径（复用条件拼装，导出行 = 页面所见）
    if (query != null) {
        if (StringUtils.hasText(query.getRefundNo())) {
            wrapper.like(MallRefund::getRefundNo, query.getRefundNo().trim());
        }
        if (StringUtils.hasText(query.getOrderNo())) {
            wrapper.like(MallRefund::getOrderNo, query.getOrderNo().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(MallRefund::getStatus, query.getStatus());
        }
        if (query.getType() != null) {
            wrapper.eq(MallRefund::getType, query.getType());
        }
        applyTimeRange(wrapper, query);
    }
    wrapper.orderByDesc(MallRefund::getId);
    List<MallRefund> rows = mallRefundService.list(wrapper);
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("售后退款单");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("售后单号");
        header.createCell(1).setCellValue("订单ID 关联mall_order");
        header.createCell(2).setCellValue("订单号");
        header.createCell(3).setCellValue("会员");
        header.createCell(4).setCellValue("售后类型");
        header.createCell(5).setCellValue("退款金额");
        header.createCell(6).setCellValue("申请原因");
        header.createCell(7).setCellValue("凭证图URL JSON数组");
        header.createCell(8).setCellValue("状态");
        header.createCell(9).setCellValue("审核意见");
        header.createCell(10).setCellValue("退款时间");
        header.createCell(11).setCellValue("原支付单号 关联pay_order");
        header.createCell(12).setCellValue("备注");
        // 单号与关联 ID 为 20 位数字串，列宽不足时 Excel 会截断显示
        int[] columnWidths = {24, 22, 24, 20, 12, 12, 30, 30, 10, 24, 20, 24, 24};
        for (int i = 0; i < columnWidths.length; i++) {
            sheet.setColumnWidth(i, columnWidths[i] * 256);
        }
        int rowIndex = 1;
        for (MallRefund e : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(cellText(e.getRefundNo()));
            row.createCell(1).setCellValue(cellText(e.getOrderId()));
            row.createCell(2).setCellValue(cellText(e.getOrderNo()));
            row.createCell(3).setCellValue(cellNum(e.getMemberId()));
            row.createCell(4).setCellValue(typeText(e.getType()));
            row.createCell(5).setCellValue(amountYuan(e.getAmount()));
            row.createCell(6).setCellValue(cellText(e.getReason()));
            row.createCell(7).setCellValue(cellText(e.getEvidenceImages()));
            row.createCell(8).setCellValue(statusText(e.getStatus()));
            row.createCell(9).setCellValue(cellText(e.getAuditRemark()));
            row.createCell(10).setCellValue(cellText(e.getRefundTime()));
            row.createCell(11).setCellValue(cellText(e.getPayOrderNo()));
            row.createCell(12).setCellValue(cellText(e.getRemark()));
        }
        wb.write(bos);
        String fileName = URLEncoder.encode("售后退款单.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

@Operation(summary = "根据ID查询售后退款单")
@GetMapping("/{id}")
public Result<MallRefundVO> getById(@PathVariable("id") Long id) {
    MallRefund entity = mallRefundService.getById(id);
    if (entity == null) {
        return Result.ok(null);
    }
    MallRefundVO vo = new MallRefundVO();
    BeanUtils.copyProperties(entity, vo);
    return Result.ok(vo);
}

@Operation(summary = "新增售后退款单")
@Log(title = "售后退款单", type = "INSERT")
@PostMapping
public Result<Void> add(@Valid @RequestBody MallRefundSaveDTO dto) {
    MallRefund entity = new MallRefund();
    BeanUtils.copyProperties(dto, entity);
    mallRefundService.save(entity);
    return Result.ok();
}

@Operation(summary = "修改售后退款单")
@Log(title = "售后退款单", type = "UPDATE")
@PutMapping
public Result<Void> update(@Valid @RequestBody MallRefundSaveDTO dto) {
    MallRefund entity = new MallRefund();
    BeanUtils.copyProperties(dto, entity);
    mallRefundService.updateById(entity);
    return Result.ok();
}

@Operation(summary = "删除售后退款单")
@Log(title = "售后退款单", type = "DELETE")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
    mallRefundService.removeById(id);
    return Result.ok();
}
    // <gen:end key="controller-body">

    private static String cellText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double cellNum(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    /** 申请时间区间（对应 create_time），page 与 export 共用，保证「页面所见 = 导出行」 */
    private void applyTimeRange(LambdaQueryWrapper<MallRefund> wrapper, MallRefundPageDTO dto) {
        if (dto.getApplyStartTime() != null) {
            wrapper.ge(MallRefund::getCreateTime, dto.getApplyStartTime());
        }
        if (dto.getApplyEndTime() != null) {
            wrapper.le(MallRefund::getCreateTime, dto.getApplyEndTime());
        }
    }

    /** 售后类型 → 中文（导出列可读） */
    private static String typeText(Integer type) {
        if (type == null) return "";
        return switch (type) {
            case 1 -> "仅退款";
            case 2 -> "退货退款";
            case 3 -> "换货";
            case 4 -> "部分退款";
            default -> String.valueOf(type);
        };
    }

    /** 售后状态 → 中文（导出列可读） */
    private static String statusText(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 1 -> "待审核";
            case 2 -> "已同意";
            case 3 -> "已拒绝";
            case 4 -> "已退款";
            case 5 -> "已关闭";
            case 6 -> "买家已寄回";
            case 7 -> "商家已收货";
            case 8 -> "已换货";
            default -> String.valueOf(status);
        };
    }

    /** 退款金额（分）→ 元，保留两位 */
    private static double amountYuan(Long amount) {
        if (amount == null) return 0D;
        return java.math.BigDecimal.valueOf(amount)
                .divide(java.math.BigDecimal.valueOf(100))
                .doubleValue();
    }

    private static String text(Row row, Integer col) {
        if (col == null || row == null) {
            return null;
        }
        String value = cellText(row.getCell(col)).trim();
        return value.isEmpty() ? null : value;
    }
    // ===== 以下为手写区：自定义接口写在这里，重新生成不会被覆盖 =====

    // ===== V4 P0-1 售后闭环：审核 / 执行退款 =====

    @Autowired
    private MemberInnerClient memberInnerClient;

    // ===== V15 售后扩展（REQ-P1-06）：换货节点 / 部分退款 / 平台仲裁 =====

    @Operation(summary = "换货-收货确认（V15）：仅「买家已寄回」可确认，回补换出 SKU 库存")
    @Log(title = "售后退款单", type = "UPDATE")
    @PostMapping("/confirm-receipt")
    public Result<Void> confirmReceipt(@RequestBody RefundIdDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        mallRefundService.confirmReceipt(dto.getId(), UserContext.getUsername());
        return Result.ok();
    }

    @Operation(summary = "换货-发出换货（V15）：仅「商家已收货」可发出，扣减换入 SKU 库存")
    @Log(title = "售后退款单", type = "UPDATE")
    @PostMapping("/send-exchange")
    public Result<Void> sendExchange(@RequestBody RefundIdDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        mallRefundService.sendExchange(dto.getId(), UserContext.getUsername());
        return Result.ok();
    }

    @Operation(summary = "平台仲裁队列（V15）：商家超 48 小时未处理的售后单（惰性判定）")
    @PostMapping("/arbitration/page")
    public Result<Page<MallRefundVO>> arbitrationPage(@RequestBody MallRefundPageDTO dto) {
        Page<MallRefund> page = mallRefundService.arbitrationPage(dto.getPageNum(), dto.getPageSize());
        Map<Long, MemberBrief> memberMap = memberMapOf(page.getRecords());
        Page<MallRefundVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(e -> {
            MallRefundVO vo = new MallRefundVO();
            BeanUtils.copyProperties(e, vo);
            MemberBrief member = memberMap.get(e.getMemberId());
            if (member != null) {
                vo.setMemberNickname(member.getNickname());
                vo.setMemberPhone(member.getPhone());
            }
            return vo;
        }).toList());
        return Result.ok(voPage);
    }

    @Operation(summary = "平台仲裁裁决（V15）：forceRefund=true 强制退款（走退款主流程终态），false 驳回")
    @Log(title = "售后退款单", type = "UPDATE")
    @PostMapping("/arbitration/verdict")
    public Result<Void> arbitrationVerdict(@RequestBody ArbitrationVerdictDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        boolean force = Boolean.TRUE.equals(dto.getForceRefund());
        mallRefundService.arbitrate(dto.getId(), force, dto.getRemark(), UserContext.getUsername());
        if (force) {
            MallRefund after = mallRefundService.getById(dto.getId());
            if (after != null) {
                pushRefundNotice(after, "平台仲裁：已强制退款",
                        "售后单 " + after.getRefundNo() + " 经平台仲裁已强制退款，请查收");
            }
        }
        return Result.ok();
    }

    @Operation(summary = "审核售后（仅待审核）：agree=true 同意 → 已同意；false 拒绝 → 已拒绝")
    @Log(title = "售后退款单", type = "UPDATE")
    @PostMapping("/audit")
    public Result<Void> audit(@RequestBody AuditDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        mallRefundService.audit(dto.getId(), Boolean.TRUE.equals(dto.getAgree()),
                dto.getAuditRemark(), UserContext.getUsername());
        // V7 站内消息：售后审核结果（失败降级，不阻断审核主流程）
        MallRefund after = mallRefundService.getById(dto.getId());
        if (after != null) {
            boolean agree = Boolean.TRUE.equals(dto.getAgree());
            pushRefundNotice(after, agree ? "售后审核通过" : "售后审核未通过",
                    "售后单 " + after.getRefundNo() + (agree ? " 审核通过，将尽快为您退款" : " 审核未通过")
                            + (StringUtils.hasText(dto.getAuditRemark()) ? "，备注：" + dto.getAuditRemark() : ""));
        }
        return Result.ok();
    }

    @Operation(summary = "执行 mock 退款（仅已同意）：置已退款；退货退款回补库存并回减销量")
    @Log(title = "售后退款单", type = "UPDATE")
    @PostMapping("/refund")
    public Result<Map<String, Object>> refund(@RequestBody RefundIdDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        MallRefund refund = mallRefundService.doRefund(dto.getId(), UserContext.getUsername());
        // V7 站内消息：退款成功（失败降级，不阻断退款主流程）
        String yuan = java.math.BigDecimal.valueOf(refund.getAmount() == null ? 0L : refund.getAmount())
                .divide(java.math.BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString();
        pushRefundNotice(refund, "退款成功", "售后单 " + refund.getRefundNo() + " 已退款 ¥" + yuan + "，请查收");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", refund.getStatus());
        data.put("refundTime", refund.getRefundTime());
        return Result.ok(data);
    }

    /** V7 售后消息推送（type 2）：统一降级口径，任何失败只记日志 */
    private void pushRefundNotice(MallRefund refund, String title, String content) {
        try {
            NoticePushDTO notice = new NoticePushDTO();
            notice.setMemberId(refund.getMemberId());
            notice.setType(2);
            notice.setTitle(title);
            notice.setContent(content);
            notice.setBizId(refund.getRefundNo());
            memberInnerClient.pushNotice(notice);
        } catch (Exception e) {
            log.warn("售后消息推送失败（已降级），售后单 {}：{}", refund.getRefundNo(), e.getMessage());
        }
    }

    /** 会员昵称/手机号批量查询（page 回填用）：经会员域 /inner 接口批量取，避免跨库直连 */
    private Map<Long, MemberBrief> memberMapOf(List<MallRefund> records) {
        List<Long> memberIds = records.stream().map(MallRefund::getMemberId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return memberInnerClient.membersByIds(memberIds).stream()
                .collect(java.util.stream.Collectors.toMap(MemberBrief::getId, m -> m));
    }

    public static class AuditDTO {
        private Long id;
        private Boolean agree;
        private String auditRemark;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Boolean getAgree() { return agree; }
        public void setAgree(Boolean agree) { this.agree = agree; }
        public String getAuditRemark() { return auditRemark; }
        public void setAuditRemark(String auditRemark) { this.auditRemark = auditRemark; }
    }

    public static class RefundIdDTO {
        private Long id;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }

    /** V15 平台仲裁裁决入参 */
    public static class ArbitrationVerdictDTO {
        private Long id;
        private Boolean forceRefund;
        private String remark;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Boolean getForceRefund() { return forceRefund; }
        public void setForceRefund(Boolean forceRefund) { this.forceRefund = forceRefund; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}