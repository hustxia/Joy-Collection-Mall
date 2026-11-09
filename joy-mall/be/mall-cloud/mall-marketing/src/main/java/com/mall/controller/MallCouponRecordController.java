package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.MallCouponRecordPageDTO;
import com.mall.dto.MallCouponRecordSaveDTO;
import com.mall.service.MallCouponRecordService;
import com.mall.entity.MallCouponRecord;
import com.mall.vo.MallCouponRecordVO;
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
 * 领券记录 Controller
 */
@Tag(name = "领券记录管理", description = "领券记录管理")
@RestController
@RequestMapping("/mall_coupon_record")
public class MallCouponRecordController {

    // Service 注入无条件保留（理由见 import 区注释）
    @Autowired
    private MallCouponRecordService mallCouponRecordService;
    @Autowired
    private com.mall.common.util.DictTexts dictTexts;
    // <gen:begin key="controller-body">

@Operation(summary = "分页查询领券记录")
@PostMapping("/page")
public Result<Page<MallCouponRecordVO>> page(@RequestBody MallCouponRecordPageDTO dto) {
    Page<MallCouponRecord> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    LambdaQueryWrapper<MallCouponRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MallCouponRecord::getId);
    mallCouponRecordService.page(page, wrapper);
    Page<MallCouponRecordVO> voPage = new Page<>();
    BeanUtils.copyProperties(page, voPage);
    List<MallCouponRecordVO> records = page.getRecords().stream()
            .map(e -> {
                MallCouponRecordVO vo = new MallCouponRecordVO();
                BeanUtils.copyProperties(e, vo);
                return vo;
            })
            .toList();
    voPage.setRecords(records);
    return Result.ok(voPage);
}

@Operation(summary = "导出领券记录 Excel（按查询条件过滤；不传条件导出全部）")
@PostMapping("/export")
public ResponseEntity<byte[]> export(@RequestBody(required = false) MallCouponRecordPageDTO query) throws IOException {
    // 复用列表页的筛选口径：导出的行必须和用户在页面上看到的一致，
    // 否则「筛出 20 条、导出 2000 条」会被当成 bug。
    LambdaQueryWrapper<MallCouponRecord> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
    }
    wrapper.orderByDesc(MallCouponRecord::getId);
    List<MallCouponRecord> rows = mallCouponRecordService.list(wrapper);
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("领券记录");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("券ID 关联mall_coupon");
        header.createCell(1).setCellValue("会员");
        header.createCell(2).setCellValue("使用的订单ID 空=未使用");
        header.createCell(3).setCellValue("使用状态");
        header.createCell(4).setCellValue("领取时间");
        header.createCell(5).setCellValue("使用时间");
        header.createCell(6).setCellValue("备注");
        int rowIndex = 1;
        for (MallCouponRecord e : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(cellNum(e.getCouponId()));
            row.createCell(1).setCellValue(cellNum(e.getMemberId()));
            row.createCell(2).setCellValue(cellNum(e.getOrderId()));
            row.createCell(3).setCellValue(cellNum(e.getStatus()));
            row.createCell(4).setCellValue(cellText(e.getReceivedTime()));
            row.createCell(5).setCellValue(cellText(e.getUsedTime()));
            row.createCell(6).setCellValue(cellText(e.getRemark()));
        }
        wb.write(bos);
        String fileName = URLEncoder.encode("领券记录.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

@Operation(summary = "根据ID查询领券记录")
@GetMapping("/{id}")
public Result<MallCouponRecordVO> getById(@PathVariable("id") Long id) {
    MallCouponRecord entity = mallCouponRecordService.getById(id);
    if (entity == null) {
        return Result.ok(null);
    }
    MallCouponRecordVO vo = new MallCouponRecordVO();
    BeanUtils.copyProperties(entity, vo);
    return Result.ok(vo);
}

@Operation(summary = "新增领券记录")
@Log(title = "领券记录", type = "INSERT")
@PostMapping
public Result<Void> add(@Valid @RequestBody MallCouponRecordSaveDTO dto) {
    MallCouponRecord entity = new MallCouponRecord();
    BeanUtils.copyProperties(dto, entity);
    mallCouponRecordService.save(entity);
    return Result.ok();
}

@Operation(summary = "修改领券记录")
@Log(title = "领券记录", type = "UPDATE")
@PutMapping
public Result<Void> update(@Valid @RequestBody MallCouponRecordSaveDTO dto) {
    MallCouponRecord entity = new MallCouponRecord();
    BeanUtils.copyProperties(dto, entity);
    mallCouponRecordService.updateById(entity);
    return Result.ok();
}

@Operation(summary = "删除领券记录")
@Log(title = "领券记录", type = "DELETE")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
    mallCouponRecordService.removeById(id);
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