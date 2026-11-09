package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.MallCouponPageDTO;
import com.mall.dto.MallCouponSaveDTO;
import com.mall.service.MallCouponService;
import com.mall.entity.MallCoupon;
import com.mall.vo.MallCouponVO;
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
 * 优惠券 Controller
 */
@Tag(name = "优惠券管理", description = "优惠券管理")
@RestController
@RequestMapping("/mall_coupon")
public class MallCouponController {

    // Service 注入无条件保留（理由见 import 区注释）
    @Autowired
    private MallCouponService mallCouponService;
    @Autowired
    private com.mall.common.util.DictTexts dictTexts;
    // <gen:begin key="controller-body">

@Operation(summary = "分页查询优惠券")
@PostMapping("/page")
public Result<Page<MallCouponVO>> page(@RequestBody MallCouponPageDTO dto) {
    Page<MallCoupon> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    LambdaQueryWrapper<MallCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MallCoupon::getId);
    mallCouponService.page(page, wrapper);
    Page<MallCouponVO> voPage = new Page<>();
    BeanUtils.copyProperties(page, voPage);
    List<MallCouponVO> records = page.getRecords().stream()
            .map(e -> {
                MallCouponVO vo = new MallCouponVO();
                BeanUtils.copyProperties(e, vo);
                return vo;
            })
            .toList();
    voPage.setRecords(records);
    return Result.ok(voPage);
}

@Operation(summary = "导出优惠券 Excel（按查询条件过滤；不传条件导出全部）")
@PostMapping("/export")
public ResponseEntity<byte[]> export(@RequestBody(required = false) MallCouponPageDTO query) throws IOException {
    // 复用列表页的筛选口径：导出的行必须和用户在页面上看到的一致，
    // 否则「筛出 20 条、导出 2000 条」会被当成 bug。
    LambdaQueryWrapper<MallCoupon> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
    }
    wrapper.orderByDesc(MallCoupon::getId);
    List<MallCoupon> rows = mallCouponService.list(wrapper);
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("优惠券");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("券名称");
        header.createCell(1).setCellValue("类型");
        header.createCell(2).setCellValue("面额");
        header.createCell(3).setCellValue("折扣率 如95=9.5折");
        header.createCell(4).setCellValue("使用门槛");
        header.createCell(5).setCellValue("发放总量");
        header.createCell(6).setCellValue("已领取数");
        header.createCell(7).setCellValue("每人限领");
        header.createCell(8).setCellValue("生效时间");
        header.createCell(9).setCellValue("失效时间");
        header.createCell(10).setCellValue("状态");
        header.createCell(11).setCellValue("备注");
        int rowIndex = 1;
        for (MallCoupon e : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(cellText(e.getName()));
            row.createCell(1).setCellValue(cellNum(e.getType()));
            row.createCell(2).setCellValue(cellNum(e.getAmount()));
            row.createCell(3).setCellValue(cellNum(e.getDiscountRate()));
            row.createCell(4).setCellValue(cellNum(e.getThresholdAmount()));
            row.createCell(5).setCellValue(cellNum(e.getTotalCount()));
            row.createCell(6).setCellValue(cellNum(e.getReceivedCount()));
            row.createCell(7).setCellValue(cellNum(e.getPerLimit()));
            row.createCell(8).setCellValue(cellText(e.getStartTime()));
            row.createCell(9).setCellValue(cellText(e.getEndTime()));
            row.createCell(10).setCellValue(cellNum(e.getStatus()));
            row.createCell(11).setCellValue(cellText(e.getRemark()));
        }
        wb.write(bos);
        String fileName = URLEncoder.encode("优惠券.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

@Operation(summary = "根据ID查询优惠券")
@GetMapping("/{id}")
public Result<MallCouponVO> getById(@PathVariable("id") Long id) {
    MallCoupon entity = mallCouponService.getById(id);
    if (entity == null) {
        return Result.ok(null);
    }
    MallCouponVO vo = new MallCouponVO();
    BeanUtils.copyProperties(entity, vo);
    return Result.ok(vo);
}

@Operation(summary = "新增优惠券")
@Log(title = "优惠券", type = "INSERT")
@PostMapping
public Result<Void> add(@Valid @RequestBody MallCouponSaveDTO dto) {
    MallCoupon entity = new MallCoupon();
    BeanUtils.copyProperties(dto, entity);
    mallCouponService.save(entity);
    return Result.ok();
}

@Operation(summary = "修改优惠券")
@Log(title = "优惠券", type = "UPDATE")
@PutMapping
public Result<Void> update(@Valid @RequestBody MallCouponSaveDTO dto) {
    MallCoupon entity = new MallCoupon();
    BeanUtils.copyProperties(dto, entity);
    mallCouponService.updateById(entity);
    return Result.ok();
}

@Operation(summary = "删除优惠券")
@Log(title = "优惠券", type = "DELETE")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
    mallCouponService.removeById(id);
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