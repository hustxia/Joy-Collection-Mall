package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.MallOrderPageDTO;
import com.mall.dto.MallOrderSaveDTO;
import com.mall.dto.MallOrderItemSaveDTO;
import com.mall.entity.MallOrderItem;
import com.mall.service.MallOrderItemService;
import com.mall.vo.MallOrderItemVO;
import com.mall.service.MallOrderService;
import com.mall.entity.MallOrder;
import com.mall.vo.MallOrderVO;
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
import java.util.List;
import java.util.LinkedHashMap;
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
 * 订单 Controller
 */
@Slf4j
@Tag(name = "订单管理", description = "订单管理")
@RestController
@RequestMapping("/mall_order")
public class MallOrderController {

    // Service 注入无条件保留（理由见 import 区注释）
    @Autowired
    private MallOrderService mallOrderService;
    @Autowired
    private MallOrderItemService mallOrderItemService;
    @Autowired
    private com.mall.common.util.DictTexts dictTexts;
    // <gen:begin key="controller-body">

@Operation(summary = "分页查询订单")
@PostMapping("/page")
public Result<Page<MallOrderVO>> page(@RequestBody MallOrderPageDTO dto) {
    Page<MallOrder> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MallOrder::getId);
    mallOrderService.page(page, wrapper);
    Page<MallOrderVO> voPage = new Page<>();
    BeanUtils.copyProperties(page, voPage);
    List<MallOrderVO> records = page.getRecords().stream()
            .map(e -> {
                MallOrderVO vo = new MallOrderVO();
                BeanUtils.copyProperties(e, vo);
                return vo;
            })
            .toList();
    voPage.setRecords(records);
    return Result.ok(voPage);
}

@Operation(summary = "主表详情（含1张子表明细）")
@GetMapping("/{id}/with-children")
public Result<Map<String, Object>> detailWithChildren(@PathVariable("id") Long id) {
    MallOrder entity = mallOrderService.getById(id);
    Map<String, Object> data = new LinkedHashMap<>();
    if (entity == null) {
        return Result.ok(data);
    }
    MallOrderVO masterVo = new MallOrderVO();
    BeanUtils.copyProperties(entity, masterVo);
    List<Map<String, Object>> children = new ArrayList<>();
    // 子表：订单明细（整棵树一次返回，避免前端逐行发请求）
    Map<String, Object> mallOrderItemGroup = new LinkedHashMap<>();
    mallOrderItemGroup.put("key", "mallOrderItem");
    mallOrderItemGroup.put("tableName", "mall_order_item");
    mallOrderItemGroup.put("comment", "订单明细");
    List<MallOrderItemVO> mallOrderItemList = new ArrayList<>();
    for (MallOrderItem row : mallOrderItemService.list(
            new LambdaQueryWrapper<MallOrderItem>().eq(MallOrderItem::getOrderId, id))) {
        MallOrderItemVO childVo = new MallOrderItemVO();
        BeanUtils.copyProperties(row, childVo);
        mallOrderItemList.add(childVo);
    }
    mallOrderItemGroup.put("rows", mallOrderItemList);
    children.add(mallOrderItemGroup);
    data.put("master", masterVo);
    data.put("children", children);
    return Result.ok(data);
}

@Operation(summary = "保存订单（主表 + 子表同事务；子表字段为 null 表示不动，空列表表示清空）")
@PostMapping("/save-with-children")
public Result<Void> saveWithChildren(@Valid @RequestBody WithChildrenDTO dto) {
    mallOrderService.saveWithChildren(dto.getMaster(),
                                       dto.getMallOrderItemRows());
    return Result.ok();
}

/**
 * 主子表保存入参：每张子表一个可空字段（null=不动该子表，[]=清空该子表）
 */
@lombok.Data
public static class WithChildrenDTO {

    private MallOrderSaveDTO master;

    private List<MallOrderItemSaveDTO> mallOrderItemRows;
}

@Operation(summary = "导出订单 Excel（按查询条件过滤；不传条件导出全部）")
@PostMapping("/export")
public ResponseEntity<byte[]> export(@RequestBody(required = false) MallOrderPageDTO query) throws IOException {
    // 复用列表页的筛选口径：导出的行必须和用户在页面上看到的一致，
    // 否则「筛出 20 条、导出 2000 条」会被当成 bug。
    LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
    }
    wrapper.orderByDesc(MallOrder::getId);
    List<MallOrder> rows = mallOrderService.list(wrapper);
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("订单");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("订单号");
        header.createCell(1).setCellValue("会员ID 关联mall_member");
        header.createCell(2).setCellValue("商品总额");
        header.createCell(3).setCellValue("运费");
        header.createCell(4).setCellValue("优惠金额");
        header.createCell(5).setCellValue("实付金额");
        header.createCell(6).setCellValue("订单状态");
        header.createCell(7).setCellValue("支付状态");
        header.createCell(8).setCellValue("支付方式");
        header.createCell(9).setCellValue("支付单号 关联pay_order");
        header.createCell(10).setCellValue("支付时间");
        header.createCell(11).setCellValue("收货人");
        header.createCell(12).setCellValue("联系电话");
        header.createCell(13).setCellValue("收货地址");
        header.createCell(14).setCellValue("快递公司");
        header.createCell(15).setCellValue("快递单号");
        header.createCell(16).setCellValue("发货时间");
        header.createCell(17).setCellValue("完成时间");
        header.createCell(18).setCellValue("买家留言");
        header.createCell(19).setCellValue("管理员备注");
        // 订单号等为 20 位数字串，列宽不足时 Excel 会截断显示，按内容长度显式设置列宽
        int[] columnWidths = {24, 20, 12, 10, 12, 12, 10, 10, 10, 24, 20, 12, 14, 36, 12, 20, 20, 20, 24, 24};
        for (int i = 0; i < columnWidths.length; i++) {
            sheet.setColumnWidth(i, columnWidths[i] * 256);
        }
        int rowIndex = 1;
        for (MallOrder e : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(cellText(e.getOrderNo()));
            row.createCell(1).setCellValue(cellNum(e.getMemberId()));
            row.createCell(2).setCellValue(cellNum(e.getTotalAmount()));
            row.createCell(3).setCellValue(cellNum(e.getFreightAmount()));
            row.createCell(4).setCellValue(cellNum(e.getDiscountAmount()));
            row.createCell(5).setCellValue(cellNum(e.getPayAmount()));
            row.createCell(6).setCellValue(cellNum(e.getStatus()));
            row.createCell(7).setCellValue(cellNum(e.getPayStatus()));
            row.createCell(8).setCellValue(cellNum(e.getPayType()));
            row.createCell(9).setCellValue(cellText(e.getPayOrderNo()));
            row.createCell(10).setCellValue(cellText(e.getPayTime()));
            row.createCell(11).setCellValue(cellText(e.getReceiverName()));
            row.createCell(12).setCellValue(cellText(e.getReceiverPhone()));
            row.createCell(13).setCellValue(cellText(e.getReceiverAddress()));
            row.createCell(14).setCellValue(cellText(e.getExpressCompany()));
            row.createCell(15).setCellValue(cellText(e.getExpressNo()));
            row.createCell(16).setCellValue(cellText(e.getDeliverTime()));
            row.createCell(17).setCellValue(cellText(e.getFinishTime()));
            row.createCell(18).setCellValue(cellText(e.getBuyerRemark()));
            row.createCell(19).setCellValue(cellText(e.getRemark()));
        }
        wb.write(bos);
        String fileName = URLEncoder.encode("订单.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

@Operation(summary = "根据ID查询订单")
@GetMapping("/{id}")
public Result<MallOrderVO> getById(@PathVariable("id") Long id) {
    MallOrder entity = mallOrderService.getById(id);
    if (entity == null) {
        return Result.ok(null);
    }
    MallOrderVO vo = new MallOrderVO();
    BeanUtils.copyProperties(entity, vo);
    return Result.ok(vo);
}

@Operation(summary = "新增订单")
@Log(title = "订单", type = "INSERT")
@PostMapping
public Result<Void> add(@Valid @RequestBody MallOrderSaveDTO dto) {
    MallOrder entity = new MallOrder();
    BeanUtils.copyProperties(dto, entity);
    mallOrderService.save(entity);
    return Result.ok();
}

@Operation(summary = "修改订单")
@Log(title = "订单", type = "UPDATE")
@PutMapping
public Result<Void> update(@Valid @RequestBody MallOrderSaveDTO dto) {
    MallOrder entity = new MallOrder();
    BeanUtils.copyProperties(dto, entity);
    mallOrderService.updateById(entity);
    return Result.ok();
}

@Operation(summary = "删除订单（按表的级联模式处理明细）")
@Log(title = "订单", type = "DELETE")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
    // V4.4：主子表走 removeWithChildren（级联/阻止/不处理由生成配置决定），不能直接 removeById 留孤儿明细
    mallOrderService.removeWithChildren(id);
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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private com.mall.service.MallOrderLogService mallOrderLogService;
    @Autowired
    private com.mall.service.OrderCloseService orderCloseService;
    @Autowired
    private com.mall.common.feign.MemberInnerClient memberInnerClient;
    @Autowired
    private com.mall.service.DeliveryService deliveryService;

    /**
     * 发货（V3 起，V12 起统一走 {@link com.mall.service.DeliveryService}）。
     *
     * <p>V12 变化：不再直接改订单状态，而是生成发货单并按明细累加 {@code shipped_quantity}；
     * 只有全部明细发完订单才转 30。旧前端（只传订单 + 快递单号）行为不变——等价于「发剩余全部」。
     * 权限点 mall_mall_order:edit（MallOrderApiPerms 手写区注册）。
     */
    @Operation(summary = "订单发货（V12）：生成发货单，全部发完订单转 30")
    @Log(title = "订单", type = "UPDATE")
    @PostMapping("/deliver")
    public Result<Void> deliver(@RequestBody DeliverDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new com.mall.common.exception.BusinessException(400, "请选择要发货的订单");
        }
        com.mall.dto.ShipDTO ship = new com.mall.dto.ShipDTO();
        ship.setOrderId(dto.getId());
        ship.setExpressCompany(dto.getExpressCompany());
        ship.setExpressNo(dto.getExpressNo());
        deliveryService.ship(ship, String.valueOf(com.mall.common.context.UserContext.getUsername()));
        return Result.ok();
    }

    /**
     * 发货入参
     */
    @lombok.Data
    public static class DeliverDTO {

        private Long id;

        private String expressCompany;

        private String expressNo;
    }

    /**
     * V4 P2-1 关闭订单（20 待发货 → 60 已关闭）：条件 UPDATE + 共享库存回补 + 日志。
     * 已付款未发货订单关闭只回补库存、不自动退款（退款走既有售后，避免金额口径分叉）。
     * 权限点 mall_mall_order:edit（MallOrderApiPerms 手写区注册）。
     */
    @Operation(summary = "关闭订单（V4）：仅待发货(20)可关闭，回补 SKU 库存，不自动退款")
    @Log(title = "订单", type = "UPDATE")
    @PostMapping("/close")
    public Result<Void> close(@RequestBody OrderIdDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            throw new com.mall.common.exception.BusinessException(400, "订单ID不能为空");
        }
        orderCloseService.closeByAdmin(dto.getOrderId(),
                String.valueOf(com.mall.common.context.UserContext.getUsername()));
        return Result.ok();
    }

    /**
     * V4 P2-1 管理员备注：只改 remark，不碰状态与金额。
     */
    @Operation(summary = "修改管理员备注（V4）")
    @Log(title = "订单", type = "UPDATE")
    @PostMapping("/remark")
    public Result<Void> remark(@RequestBody OrderRemarkDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            throw new com.mall.common.exception.BusinessException(400, "订单ID不能为空");
        }
        String remark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        if (remark.length() > 255) {
            throw new com.mall.common.exception.BusinessException(400, "管理员备注不能超过 255 个字符");
        }
        int affected = jdbcTemplate.update(
                "UPDATE mall_order SET remark = ? WHERE id = ? AND deleted = 0",
                remark.isEmpty() ? null : remark, dto.getOrderId());
        if (affected == 0) {
            throw new com.mall.common.exception.BusinessException(400, "订单不存在");
        }
        return Result.ok();
    }

    /**
     * 关单入参
     */
    @lombok.Data
    public static class OrderIdDTO {

        private Long orderId;
    }

    /**
     * 备注入参
     */
    @lombok.Data
    public static class OrderRemarkDTO {

        private Long orderId;

        private String remark;
    }
}