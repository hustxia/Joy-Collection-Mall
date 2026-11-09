package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.MallAddressPageDTO;
import com.mall.dto.MallAddressSaveDTO;
import com.mall.service.MallAddressService;
import com.mall.entity.MallAddress;
import com.mall.vo.MallAddressVO;
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
 * 收货地址 Controller
 */
@Tag(name = "收货地址管理", description = "收货地址管理")
@RestController
@RequestMapping("/mall_address")
public class MallAddressController {

    // Service 注入无条件保留（理由见 import 区注释）
    @Autowired
    private MallAddressService mallAddressService;
    // <gen:begin key="controller-body">

@Operation(summary = "分页查询收货地址")
@PostMapping("/page")
public Result<Page<MallAddressVO>> page(@RequestBody MallAddressPageDTO dto) {
    Page<MallAddress> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    LambdaQueryWrapper<MallAddress> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MallAddress::getId);
    mallAddressService.page(page, wrapper);
    Page<MallAddressVO> voPage = new Page<>();
    BeanUtils.copyProperties(page, voPage);
    List<MallAddressVO> records = page.getRecords().stream()
            .map(e -> {
                MallAddressVO vo = new MallAddressVO();
                BeanUtils.copyProperties(e, vo);
                return vo;
            })
            .toList();
    voPage.setRecords(records);
    return Result.ok(voPage);
}

@Operation(summary = "导出收货地址 Excel（按查询条件过滤；不传条件导出全部）")
@PostMapping("/export")
public ResponseEntity<byte[]> export(@RequestBody(required = false) MallAddressPageDTO query) throws IOException {
    // 复用列表页的筛选口径：导出的行必须和用户在页面上看到的一致，
    // 否则「筛出 20 条、导出 2000 条」会被当成 bug。
    LambdaQueryWrapper<MallAddress> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
    }
    wrapper.orderByDesc(MallAddress::getId);
    List<MallAddress> rows = mallAddressService.list(wrapper);
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("收货地址");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("会员ID 关联mall_member");
        header.createCell(1).setCellValue("收货人");
        header.createCell(2).setCellValue("联系电话");
        header.createCell(3).setCellValue("省");
        header.createCell(4).setCellValue("市");
        header.createCell(5).setCellValue("区");
        header.createCell(6).setCellValue("详细地址");
        header.createCell(7).setCellValue("默认地址");
        header.createCell(8).setCellValue("备注");
        int rowIndex = 1;
        for (MallAddress e : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(cellNum(e.getMemberId()));
            row.createCell(1).setCellValue(cellText(e.getReceiverName()));
            row.createCell(2).setCellValue(cellText(e.getReceiverPhone()));
            row.createCell(3).setCellValue(cellText(e.getProvince()));
            row.createCell(4).setCellValue(cellText(e.getCity()));
            row.createCell(5).setCellValue(cellText(e.getDistrict()));
            row.createCell(6).setCellValue(cellText(e.getDetail()));
            row.createCell(7).setCellValue(cellNum(e.getIsDefault()));
            row.createCell(8).setCellValue(cellText(e.getRemark()));
        }
        wb.write(bos);
        String fileName = URLEncoder.encode("收货地址.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

@Operation(summary = "根据ID查询收货地址")
@GetMapping("/{id}")
public Result<MallAddressVO> getById(@PathVariable("id") Long id) {
    MallAddress entity = mallAddressService.getById(id);
    if (entity == null) {
        return Result.ok(null);
    }
    MallAddressVO vo = new MallAddressVO();
    BeanUtils.copyProperties(entity, vo);
    return Result.ok(vo);
}

@Operation(summary = "新增收货地址")
@Log(title = "收货地址", type = "INSERT")
@PostMapping
public Result<Void> add(@Valid @RequestBody MallAddressSaveDTO dto) {
    MallAddress entity = new MallAddress();
    BeanUtils.copyProperties(dto, entity);
    mallAddressService.save(entity);
    return Result.ok();
}

@Operation(summary = "修改收货地址")
@Log(title = "收货地址", type = "UPDATE")
@PutMapping
public Result<Void> update(@Valid @RequestBody MallAddressSaveDTO dto) {
    MallAddress entity = new MallAddress();
    BeanUtils.copyProperties(dto, entity);
    mallAddressService.updateById(entity);
    return Result.ok();
}

@Operation(summary = "删除收货地址")
@Log(title = "收货地址", type = "DELETE")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
    mallAddressService.removeById(id);
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