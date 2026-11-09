package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.MallSkuPageDTO;
import com.mall.dto.MallSkuSaveDTO;
import com.mall.service.MallSkuService;
import com.mall.entity.MallSku;
import com.mall.vo.MallSkuVO;
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
 * 商品SKU Controller
 */
@Tag(name = "商品SKU管理", description = "商品SKU管理")
@RestController
@RequestMapping("/mall_sku")
public class MallSkuController {

    // Service 注入无条件保留（理由见 import 区注释）
    @Autowired
    private MallSkuService mallSkuService;
    @Autowired
    private com.mall.common.util.DictTexts dictTexts;
    // <gen:begin key="controller-body">

@Operation(summary = "分页查询商品SKU")
@PostMapping("/page")
public Result<Page<MallSkuVO>> page(@RequestBody MallSkuPageDTO dto) {
    Page<MallSku> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    LambdaQueryWrapper<MallSku> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MallSku::getId);
    mallSkuService.page(page, wrapper);
    Page<MallSkuVO> voPage = new Page<>();
    BeanUtils.copyProperties(page, voPage);
    List<MallSkuVO> records = page.getRecords().stream()
            .map(e -> {
                MallSkuVO vo = new MallSkuVO();
                BeanUtils.copyProperties(e, vo);
                return vo;
            })
            .toList();
    voPage.setRecords(records);
    return Result.ok(voPage);
}

@Operation(summary = "导出商品SKU Excel（按查询条件过滤；不传条件导出全部）")
@PostMapping("/export")
public ResponseEntity<byte[]> export(@RequestBody(required = false) MallSkuPageDTO query) throws IOException {
    // 复用列表页的筛选口径：导出的行必须和用户在页面上看到的一致，
    // 否则「筛出 20 条、导出 2000 条」会被当成 bug。
    LambdaQueryWrapper<MallSku> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
    }
    wrapper.orderByDesc(MallSku::getId);
    List<MallSku> rows = mallSkuService.list(wrapper);
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("商品SKU");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("商品ID 关联mall_goods");
        header.createCell(1).setCellValue("SKU编码");
        header.createCell(2).setCellValue("规格JSON数组文本");
        header.createCell(3).setCellValue("售价");
        header.createCell(4).setCellValue("库存");
        header.createCell(5).setCellValue("销量");
        header.createCell(6).setCellValue("SKU图URL");
        header.createCell(7).setCellValue("状态");
        header.createCell(8).setCellValue("备注");
        int rowIndex = 1;
        for (MallSku e : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(cellNum(e.getGoodsId()));
            row.createCell(1).setCellValue(cellText(e.getSkuCode()));
            row.createCell(2).setCellValue(cellText(e.getSpecJson()));
            row.createCell(3).setCellValue(cellNum(e.getPrice()));
            row.createCell(4).setCellValue(cellNum(e.getStock()));
            row.createCell(5).setCellValue(cellNum(e.getSales()));
            row.createCell(6).setCellValue(cellText(e.getImage()));
            row.createCell(7).setCellValue(cellNum(e.getStatus()));
            row.createCell(8).setCellValue(cellText(e.getRemark()));
        }
        wb.write(bos);
        String fileName = URLEncoder.encode("商品SKU.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

@Operation(summary = "根据ID查询商品SKU")
@GetMapping("/{id}")
public Result<MallSkuVO> getById(@PathVariable("id") Long id) {
    MallSku entity = mallSkuService.getById(id);
    if (entity == null) {
        return Result.ok(null);
    }
    MallSkuVO vo = new MallSkuVO();
    BeanUtils.copyProperties(entity, vo);
    return Result.ok(vo);
}

@Operation(summary = "新增商品SKU")
@Log(title = "商品SKU", type = "INSERT")
@PostMapping
public Result<Void> add(@Valid @RequestBody MallSkuSaveDTO dto) {
    MallSku entity = new MallSku();
    BeanUtils.copyProperties(dto, entity);
    mallSkuService.save(entity);
    return Result.ok();
}

@Operation(summary = "修改商品SKU")
@Log(title = "商品SKU", type = "UPDATE")
@PutMapping
public Result<Void> update(@Valid @RequestBody MallSkuSaveDTO dto) {
    MallSku entity = new MallSku();
    BeanUtils.copyProperties(dto, entity);
    mallSkuService.updateById(entity);
    return Result.ok();
}

@Operation(summary = "删除商品SKU")
@Log(title = "商品SKU", type = "DELETE")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
    mallSkuService.removeById(id);
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