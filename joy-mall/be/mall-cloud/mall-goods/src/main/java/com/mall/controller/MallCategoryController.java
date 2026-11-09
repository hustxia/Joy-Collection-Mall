package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.MallCategoryPageDTO;
import com.mall.dto.MallCategorySaveDTO;
import com.mall.service.MallCategoryService;
import com.mall.entity.MallCategory;
import com.mall.vo.MallCategoryVO;
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
import org.springframework.web.bind.annotation.RequestParam;
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
 * 商品类目 Controller
 */
@Tag(name = "商品类目管理", description = "商品类目管理")
@RestController
@RequestMapping("/mall_category")
public class MallCategoryController {

    // Service 注入无条件保留（理由见 import 区注释）
    @Autowired
    private MallCategoryService mallCategoryService;
    @Autowired
    private com.mall.common.util.DictTexts dictTexts;
    // <gen:begin key="controller-body">

@Operation(summary = "分页查询商品类目")
@PostMapping("/page")
public Result<Page<MallCategoryVO>> page(@RequestBody MallCategoryPageDTO dto) {
    Page<MallCategory> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    LambdaQueryWrapper<MallCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MallCategory::getId);
    mallCategoryService.page(page, wrapper);
    Page<MallCategoryVO> voPage = new Page<>();
    BeanUtils.copyProperties(page, voPage);
    List<MallCategoryVO> records = page.getRecords().stream()
            .map(e -> {
                MallCategoryVO vo = new MallCategoryVO();
                BeanUtils.copyProperties(e, vo);
                return vo;
            })
            .toList();
    voPage.setRecords(records);
    return Result.ok(voPage);
}

@Operation(summary = "商品类目树形列表")
@GetMapping("/tree")
public Result<List<MallCategoryVO>> tree() {
    return Result.ok(mallCategoryService.tree());
}

@Operation(summary = "导出商品类目 Excel（按查询条件过滤；不传条件导出全部）")
@PostMapping("/export")
public ResponseEntity<byte[]> export(@RequestBody(required = false) MallCategoryPageDTO query) throws IOException {
    // 复用列表页的筛选口径：导出的行必须和用户在页面上看到的一致，
    // 否则「筛出 20 条、导出 2000 条」会被当成 bug。
    LambdaQueryWrapper<MallCategory> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
    }
    wrapper.orderByDesc(MallCategory::getId);
    List<MallCategory> rows = mallCategoryService.list(wrapper);
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("商品类目");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("父类目ID 0=顶级");
        header.createCell(1).setCellValue("类目名称");
        header.createCell(2).setCellValue("层级");
        header.createCell(3).setCellValue("类目图标URL");
        header.createCell(4).setCellValue("排序 越小越靠前");
        header.createCell(5).setCellValue("状态");
        header.createCell(6).setCellValue("备注");
        int rowIndex = 1;
        for (MallCategory e : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(cellNum(e.getParentId()));
            row.createCell(1).setCellValue(cellText(e.getName()));
            row.createCell(2).setCellValue(cellNum(e.getLevel()));
            row.createCell(3).setCellValue(cellText(e.getIcon()));
            row.createCell(4).setCellValue(cellNum(e.getSort()));
            row.createCell(5).setCellValue(cellNum(e.getStatus()));
            row.createCell(6).setCellValue(cellText(e.getRemark()));
        }
        wb.write(bos);
        String fileName = URLEncoder.encode("商品类目.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

@Operation(summary = "根据ID查询商品类目")
@GetMapping("/{id}")
public Result<MallCategoryVO> getById(@PathVariable("id") Long id) {
    MallCategory entity = mallCategoryService.getById(id);
    if (entity == null) {
        return Result.ok(null);
    }
    MallCategoryVO vo = new MallCategoryVO();
    BeanUtils.copyProperties(entity, vo);
    return Result.ok(vo);
}

@Operation(summary = "新增商品类目")
@Log(title = "商品类目", type = "INSERT")
@PostMapping
public Result<Void> add(@Valid @RequestBody MallCategorySaveDTO dto) {
    MallCategory entity = new MallCategory();
    BeanUtils.copyProperties(dto, entity);
    mallCategoryService.save(entity);
    return Result.ok();
}

@Operation(summary = "修改商品类目")
@Log(title = "商品类目", type = "UPDATE")
@PutMapping
public Result<Void> update(@Valid @RequestBody MallCategorySaveDTO dto) {
    MallCategory entity = new MallCategory();
    BeanUtils.copyProperties(dto, entity);
    mallCategoryService.updateById(entity);
    return Result.ok();
}

@Operation(summary = "删除商品类目")
@Log(title = "商品类目", type = "DELETE")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
    mallCategoryService.removeById(id);
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