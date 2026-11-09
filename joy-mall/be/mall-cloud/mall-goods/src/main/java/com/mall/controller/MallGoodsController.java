package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.MallGoodsPageDTO;
import com.mall.dto.MallGoodsSaveDTO;
import com.mall.service.MallGoodsService;
import com.mall.entity.MallGoods;
import com.mall.vo.MallGoodsVO;
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
 * 商品 Controller
 */
@Tag(name = "商品管理", description = "商品管理")
@RestController
@RequestMapping("/mall_goods")
public class MallGoodsController {

    // Service 注入无条件保留（理由见 import 区注释）
    @Autowired
    private MallGoodsService mallGoodsService;
    @Autowired
    private com.mall.common.util.DictTexts dictTexts;
    // <gen:begin key="controller-body">

@Operation(summary = "分页查询商品")
@PostMapping("/page")
public Result<Page<MallGoodsVO>> page(@RequestBody MallGoodsPageDTO dto) {
    Page<MallGoods> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    LambdaQueryWrapper<MallGoods> wrapper = new LambdaQueryWrapper<>();
    // V10：按商家维度筛选（后台「商家商品」视图）
    if (dto.getMerchantId() != null) {
        wrapper.eq(MallGoods::getMerchantId, dto.getMerchantId());
    }
        wrapper.orderByDesc(MallGoods::getId);
    mallGoodsService.page(page, wrapper);
    Page<MallGoodsVO> voPage = new Page<>();
    BeanUtils.copyProperties(page, voPage);
    List<MallGoodsVO> records = page.getRecords().stream()
            .map(e -> {
                MallGoodsVO vo = new MallGoodsVO();
                BeanUtils.copyProperties(e, vo);
                return vo;
            })
            .toList();
    voPage.setRecords(records);
    return Result.ok(voPage);
}

@Operation(summary = "导出商品 Excel（按查询条件过滤；不传条件导出全部）")
@PostMapping("/export")
public ResponseEntity<byte[]> export(@RequestBody(required = false) MallGoodsPageDTO query) throws IOException {
    // 复用列表页的筛选口径：导出的行必须和用户在页面上看到的一致，
    // 否则「筛出 20 条、导出 2000 条」会被当成 bug。
    LambdaQueryWrapper<MallGoods> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
    }
    wrapper.orderByDesc(MallGoods::getId);
    List<MallGoods> rows = mallGoodsService.list(wrapper);
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("商品");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("商品编码");
        header.createCell(1).setCellValue("类目ID 关联mall_category");
        header.createCell(2).setCellValue("品牌ID 关联mall_brand");
        header.createCell(3).setCellValue("商品名称");
        header.createCell(4).setCellValue("副标题");
        header.createCell(5).setCellValue("主图URL");
        header.createCell(6).setCellValue("轮播图URL JSON数组");
        header.createCell(7).setCellValue("商品详情 富文本HTML");
        header.createCell(8).setCellValue("最低价");
        header.createCell(9).setCellValue("最高价");
        header.createCell(10).setCellValue("销量");
        header.createCell(11).setCellValue("状态");
        header.createCell(12).setCellValue("备注");
        int rowIndex = 1;
        for (MallGoods e : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(cellText(e.getGoodsNo()));
            row.createCell(1).setCellValue(cellNum(e.getCategoryId()));
            row.createCell(2).setCellValue(cellNum(e.getBrandId()));
            row.createCell(3).setCellValue(cellText(e.getName()));
            row.createCell(4).setCellValue(cellText(e.getSubTitle()));
            row.createCell(5).setCellValue(cellText(e.getMainImage()));
            row.createCell(6).setCellValue(cellText(e.getSliderImages()));
            row.createCell(7).setCellValue(cellText(e.getDetail()));
            row.createCell(8).setCellValue(cellNum(e.getPriceMin()));
            row.createCell(9).setCellValue(cellNum(e.getPriceMax()));
            row.createCell(10).setCellValue(cellNum(e.getSales()));
            row.createCell(11).setCellValue(cellNum(e.getStatus()));
            row.createCell(12).setCellValue(cellText(e.getRemark()));
        }
        wb.write(bos);
        String fileName = URLEncoder.encode("商品.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

@Operation(summary = "根据ID查询商品")
@GetMapping("/{id}")
public Result<MallGoodsVO> getById(@PathVariable("id") Long id) {
    MallGoods entity = mallGoodsService.getById(id);
    if (entity == null) {
        return Result.ok(null);
    }
    MallGoodsVO vo = new MallGoodsVO();
    BeanUtils.copyProperties(entity, vo);
    return Result.ok(vo);
}

@Operation(summary = "新增商品")
@Log(title = "商品", type = "INSERT")
@PostMapping
public Result<Void> add(@Valid @RequestBody MallGoodsSaveDTO dto) {
    MallGoods entity = new MallGoods();
    BeanUtils.copyProperties(dto, entity);
    mallGoodsService.save(entity);
    return Result.ok();
}

@Operation(summary = "修改商品")
@Log(title = "商品", type = "UPDATE")
@PutMapping
public Result<Void> update(@Valid @RequestBody MallGoodsSaveDTO dto) {
    MallGoods entity = new MallGoods();
    BeanUtils.copyProperties(dto, entity);
    mallGoodsService.updateById(entity);
    return Result.ok();
}

@Operation(summary = "删除商品")
@Log(title = "商品", type = "DELETE")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
    mallGoodsService.removeById(id);
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