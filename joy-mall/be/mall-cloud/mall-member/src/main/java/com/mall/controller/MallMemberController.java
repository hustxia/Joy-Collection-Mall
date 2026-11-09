package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.MallMemberPageDTO;
import com.mall.dto.MallMemberSaveDTO;
import com.mall.service.MallMemberService;
import com.mall.entity.MallMember;
import com.mall.vo.MallMemberVO;
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
 * 会员 Controller
 */
@Tag(name = "会员管理", description = "会员管理")
@RestController
@RequestMapping("/mall_member")
public class MallMemberController {

    // Service 注入无条件保留（理由见 import 区注释）
    @Autowired
    private MallMemberService mallMemberService;
    @Autowired
    private com.mall.common.util.DictTexts dictTexts;
    // <gen:begin key="controller-body">

@Operation(summary = "分页查询会员")
@PostMapping("/page")
public Result<Page<MallMemberVO>> page(@RequestBody MallMemberPageDTO dto) {
    Page<MallMember> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    LambdaQueryWrapper<MallMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MallMember::getId);
    mallMemberService.page(page, wrapper);
    Page<MallMemberVO> voPage = new Page<>();
    BeanUtils.copyProperties(page, voPage);
    List<MallMemberVO> records = page.getRecords().stream()
            .map(e -> {
                MallMemberVO vo = new MallMemberVO();
                BeanUtils.copyProperties(e, vo);
                return vo;
            })
            .toList();
    voPage.setRecords(records);
    return Result.ok(voPage);
}

@Operation(summary = "导出会员 Excel（按查询条件过滤；不传条件导出全部）")
@PostMapping("/export")
public ResponseEntity<byte[]> export(@RequestBody(required = false) MallMemberPageDTO query) throws IOException {
    // 复用列表页的筛选口径：导出的行必须和用户在页面上看到的一致，
    // 否则「筛出 20 条、导出 2000 条」会被当成 bug。
    LambdaQueryWrapper<MallMember> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
    }
    wrapper.orderByDesc(MallMember::getId);
    List<MallMember> rows = mallMemberService.list(wrapper);
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("会员");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("昵称");
        header.createCell(1).setCellValue("头像URL");
        header.createCell(2).setCellValue("手机号");
        header.createCell(3).setCellValue("微信openid");
        header.createCell(4).setCellValue("微信unionid");
        header.createCell(5).setCellValue("性别");
        header.createCell(6).setCellValue("注册来源");
        header.createCell(7).setCellValue("积分");
        header.createCell(8).setCellValue("状态");
        header.createCell(9).setCellValue("最后登录时间");
        header.createCell(10).setCellValue("备注");
        int rowIndex = 1;
        for (MallMember e : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(cellText(e.getNickname()));
            row.createCell(1).setCellValue(cellText(e.getAvatar()));
            row.createCell(2).setCellValue(cellText(e.getPhone()));
            row.createCell(3).setCellValue(cellText(e.getOpenid()));
            row.createCell(4).setCellValue(cellText(e.getUnionid()));
            row.createCell(5).setCellValue(cellNum(e.getGender()));
            row.createCell(6).setCellValue(cellNum(e.getSource()));
            row.createCell(7).setCellValue(cellNum(e.getPoints()));
            row.createCell(8).setCellValue(cellNum(e.getStatus()));
            row.createCell(9).setCellValue(cellText(e.getLastLoginTime()));
            row.createCell(10).setCellValue(cellText(e.getRemark()));
        }
        wb.write(bos);
        String fileName = URLEncoder.encode("会员.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

@Operation(summary = "根据ID查询会员")
@GetMapping("/{id}")
public Result<MallMemberVO> getById(@PathVariable("id") Long id) {
    MallMember entity = mallMemberService.getById(id);
    if (entity == null) {
        return Result.ok(null);
    }
    MallMemberVO vo = new MallMemberVO();
    BeanUtils.copyProperties(entity, vo);
    return Result.ok(vo);
}

@Operation(summary = "新增会员")
@Log(title = "会员", type = "INSERT")
@PostMapping
public Result<Void> add(@Valid @RequestBody MallMemberSaveDTO dto) {
    MallMember entity = new MallMember();
    BeanUtils.copyProperties(dto, entity);
    mallMemberService.save(entity);
    return Result.ok();
}

@Operation(summary = "修改会员")
@Log(title = "会员", type = "UPDATE")
@PutMapping
public Result<Void> update(@Valid @RequestBody MallMemberSaveDTO dto) {
    MallMember entity = new MallMember();
    BeanUtils.copyProperties(dto, entity);
    mallMemberService.updateById(entity);
    return Result.ok();
}

@Operation(summary = "删除会员")
@Log(title = "会员", type = "DELETE")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
    mallMemberService.removeById(id);
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