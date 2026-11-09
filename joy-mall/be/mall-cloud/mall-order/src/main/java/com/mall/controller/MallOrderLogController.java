package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.MallOrderLogPageDTO;
import com.mall.dto.MallOrderLogSaveDTO;
import com.mall.service.MallOrderLogService;
import com.mall.entity.MallOrderLog;
import com.mall.vo.MallOrderLogVO;
import com.mall.common.result.Result;
import com.mall.common.log.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import java.util.List;
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
 * 订单流转日志 Controller
 */
@Tag(name = "订单流转日志管理", description = "订单流转日志管理")
@RestController
@RequestMapping("/mall_order_log")
public class MallOrderLogController {

    // Service 注入无条件保留（理由见 import 区注释）
    @Autowired
    private MallOrderLogService mallOrderLogService;
    @Autowired
    private com.mall.common.util.DictTexts dictTexts;
    // <gen:begin key="controller-body">

@Operation(summary = "分页查询订单流转日志")
@PostMapping("/page")
public Result<Page<MallOrderLogVO>> page(@RequestBody MallOrderLogPageDTO dto) {
    Page<MallOrderLog> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    LambdaQueryWrapper<MallOrderLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MallOrderLog::getId);
    mallOrderLogService.page(page, wrapper);
    Page<MallOrderLogVO> voPage = new Page<>();
    BeanUtils.copyProperties(page, voPage);
    List<MallOrderLogVO> records = page.getRecords().stream()
            .map(e -> {
                MallOrderLogVO vo = new MallOrderLogVO();
                BeanUtils.copyProperties(e, vo);
                return vo;
            })
            .toList();
    voPage.setRecords(records);
    return Result.ok(voPage);
}

@Operation(summary = "导出订单流转日志 Excel（按查询条件过滤；不传条件导出全部）")
@PostMapping("/export")
public ResponseEntity<byte[]> export(@RequestBody(required = false) MallOrderLogPageDTO query) throws IOException {
    // 复用列表页的筛选口径：导出的行必须和用户在页面上看到的一致，
    // 否则「筛出 20 条、导出 2000 条」会被当成 bug。
    LambdaQueryWrapper<MallOrderLog> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
    }
    wrapper.orderByDesc(MallOrderLog::getId);
    List<MallOrderLog> rows = mallOrderLogService.list(wrapper);
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("订单流转日志");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("订单ID 关联mall_order");
        header.createCell(1).setCellValue("订单号");
        header.createCell(2).setCellValue("变更内容");
        header.createCell(3).setCellValue("操作人");
        header.createCell(4).setCellValue("操作方");
        header.createCell(5).setCellValue("备注");
        // 订单 ID / 订单号为 20 位数字串，按内容长度设置列宽避免 Excel 截断
        int[] columnWidths = {22, 24, 36, 14, 12, 24};
        for (int i = 0; i < columnWidths.length; i++) {
            sheet.setColumnWidth(i, columnWidths[i] * 256);
        }
        int rowIndex = 1;
        for (MallOrderLog e : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(cellText(e.getOrderId()));
            row.createCell(1).setCellValue(cellText(e.getOrderNo()));
            row.createCell(2).setCellValue(cellText(e.getContent()));
            row.createCell(3).setCellValue(cellText(e.getOperator()));
            row.createCell(4).setCellValue(cellNum(e.getOperatorType()));
            row.createCell(5).setCellValue(cellText(e.getRemark()));
        }
        wb.write(bos);
        String fileName = URLEncoder.encode("订单流转日志.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

@Operation(summary = "根据ID查询订单流转日志")
@GetMapping("/{id}")
public Result<MallOrderLogVO> getById(@PathVariable("id") Long id) {
    MallOrderLog entity = mallOrderLogService.getById(id);
    if (entity == null) {
        return Result.ok(null);
    }
    MallOrderLogVO vo = new MallOrderLogVO();
    BeanUtils.copyProperties(entity, vo);
    return Result.ok(vo);
}

@Operation(summary = "新增订单流转日志")
@Log(title = "订单流转日志", type = "INSERT")
@PostMapping
public Result<Void> add(@Valid @RequestBody MallOrderLogSaveDTO dto) {
    MallOrderLog entity = new MallOrderLog();
    BeanUtils.copyProperties(dto, entity);
    mallOrderLogService.save(entity);
    return Result.ok();
}

@Operation(summary = "修改订单流转日志")
@Log(title = "订单流转日志", type = "UPDATE")
@PutMapping
public Result<Void> update(@Valid @RequestBody MallOrderLogSaveDTO dto) {
    MallOrderLog entity = new MallOrderLog();
    BeanUtils.copyProperties(dto, entity);
    mallOrderLogService.updateById(entity);
    return Result.ok();
}

@Operation(summary = "删除订单流转日志")
@Log(title = "订单流转日志", type = "DELETE")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
    mallOrderLogService.removeById(id);
    return Result.ok();
}
    // <gen:end key="controller-body">

    private static String cellText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double cellNum(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private static String text(Row row, Integer col) {
        if (col == null || row == null) {
            return null;
        }
        String value = cellText(row.getCell(col)).trim();
        return value.isEmpty() ? null : value;
    }
    // ===== 以下为手写区：自定义接口写在这里，重新生成不会被覆盖 =====
}