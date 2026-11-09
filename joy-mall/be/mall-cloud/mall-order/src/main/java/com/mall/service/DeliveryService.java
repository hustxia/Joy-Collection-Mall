package com.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.dto.NoticePushDTO;
import com.mall.dto.DeliveryBatchDTO;
import com.mall.dto.ShipDTO;
import com.mall.entity.MallDelivery;
import com.mall.entity.MallDeliveryItem;
import com.mall.entity.MallOrder;
import com.mall.entity.MallOrderItem;
import com.mall.entity.MallOrderLog;
import com.mall.logistics.LogisticsProvider;
import com.mall.mapper.MallDeliveryItemMapper;
import com.mall.mapper.MallDeliveryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 发货服务（V12）：单笔发货、批量导入、部分发货、包裹与轨迹查询。
 *
 * <p>部分发货的判定全部落在 {@code mall_order_item.shipped_quantity} 上：
 * 每次发货用条件 UPDATE（{@code shipped_quantity + n <= quantity}）累加，
 * 要么成功要么失败，不会出现「多发了 2 件」的脏数据。
 * 订单状态只在**所有明细都发完**时转 30，未发完保持 20 —— 用户看到的是「部分发货」而不是「已发货」。
 */
@Slf4j
@Service
public class DeliveryService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 订单状态：待发货 */
    private static final int ORDER_PAID = 20;
    /** 订单状态：已发货 */
    private static final int ORDER_SHIPPED = 30;

    private final MallDeliveryMapper deliveryMapper;
    private final MallDeliveryItemMapper deliveryItemMapper;
    private final MallOrderService mallOrderService;
    private final MallOrderItemService mallOrderItemService;
    private final MallOrderLogService mallOrderLogService;
    private final MemberInnerClient memberInnerClient;
    private final LogisticsProvider logisticsProvider;
    private final JdbcTemplate jdbcTemplate;

    public DeliveryService(MallDeliveryMapper deliveryMapper, MallDeliveryItemMapper deliveryItemMapper,
                           MallOrderService mallOrderService, MallOrderItemService mallOrderItemService,
                           MallOrderLogService mallOrderLogService, MemberInnerClient memberInnerClient,
                           LogisticsProvider logisticsProvider, JdbcTemplate jdbcTemplate) {
        this.deliveryMapper = deliveryMapper;
        this.deliveryItemMapper = deliveryItemMapper;
        this.mallOrderService = mallOrderService;
        this.mallOrderItemService = mallOrderItemService;
        this.mallOrderLogService = mallOrderLogService;
        this.memberInnerClient = memberInnerClient;
        this.logisticsProvider = logisticsProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 发货（单笔）：支持按明细数量部分发货；items 为空则发剩余全部。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ship(ShipDTO dto, String operator) {
        if (dto == null || dto.getOrderId() == null) {
            throw new BusinessException(400, "请选择要发货的订单");
        }
        if (!StringUtils.hasText(dto.getExpressCompany()) || !StringUtils.hasText(dto.getExpressNo())) {
            throw new BusinessException(400, "快递公司/快递单号必填");
        }
        MallOrder order = mallOrderService.getById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }
        if (!Integer.valueOf(ORDER_PAID).equals(order.getStatus())) {
            throw new BusinessException(400, "仅待发货订单可发货，当前状态：" + order.getStatus());
        }
        String expressNo = dto.getExpressNo().trim();
        Long dup = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_delivery WHERE express_no = ? AND deleted = 0", Long.class, expressNo);
        if (dup != null && dup > 0) {
            throw new BusinessException(400, "快递单号已存在：" + expressNo);
        }

        List<MallOrderItem> items = mallOrderItemService.list(new LambdaQueryWrapper<MallOrderItem>()
                .eq(MallOrderItem::getOrderId, order.getId()).orderByAsc(MallOrderItem::getId));
        if (items.isEmpty()) {
            throw new BusinessException(400, "订单没有明细，无法发货");
        }
        List<MallDeliveryItem> toShip = resolveShipItems(items, dto.getItems());
        if (toShip.isEmpty()) {
            throw new BusinessException(400, "该订单已全部发货");
        }

        MallDelivery delivery = new MallDelivery();
        delivery.setDeliveryNo("D" + LocalDateTime.now().format(TS)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000)));
        delivery.setOrderId(order.getId());
        delivery.setOrderNo(order.getOrderNo());
        delivery.setMerchantId(order.getMerchantId());
        delivery.setExpressCompany(dto.getExpressCompany().trim());
        delivery.setExpressNo(expressNo);
        delivery.setStatus(MallDelivery.ST_SHIPPED);
        delivery.setDeliveryTime(LocalDateTime.now());
        delivery.setReceiverName(order.getReceiverName());
        delivery.setReceiverPhone(order.getReceiverPhone());
        delivery.setReceiverAddress(order.getReceiverAddress());
        delivery.setRemark(dto.getRemark());
        deliveryMapper.insert(delivery);

        for (MallDeliveryItem item : toShip) {
            item.setDeliveryId(delivery.getId());
            item.setOrderId(order.getId());
            deliveryItemMapper.insert(item);
            int affected = jdbcTemplate.update(
                    "UPDATE mall_order_item SET shipped_quantity = shipped_quantity + ?"
                            + " WHERE id = ? AND shipped_quantity + ? <= quantity AND deleted = 0",
                    item.getQuantity(), item.getOrderItemId(), item.getQuantity());
            if (affected == 0) {
                // 条件累加失败 = 并发超发，抛出让整笔发货回滚
                throw new BusinessException(400, "「" + item.getGoodsName() + "」可发数量不足，请刷新后重试");
            }
        }
        boolean allShipped = countUnshipped(order.getId()) == 0;
        if (allShipped) {
            jdbcTemplate.update("UPDATE mall_order SET status = ?, express_company = ?, express_no = ?,"
                            + " deliver_time = NOW() WHERE id = ? AND status = ? AND deleted = 0",
                    ORDER_SHIPPED, delivery.getExpressCompany(), delivery.getExpressNo(),
                    order.getId(), ORDER_PAID);
        }
        String logText = (allShipped ? "发货：" : "部分发货：") + delivery.getExpressCompany() + " "
                + delivery.getExpressNo() + "，本次 " + toShip.stream().mapToInt(MallDeliveryItem::getQuantity).sum()
                + " 件（发货单 " + delivery.getDeliveryNo() + "）";
        writeLog(order, logText, operator);
        if (allShipped) {
            pushDeliverNotice(order, delivery);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deliveryId", String.valueOf(delivery.getId()));
        data.put("deliveryNo", delivery.getDeliveryNo());
        data.put("orderNo", order.getOrderNo());
        data.put("orderStatus", allShipped ? ORDER_SHIPPED : ORDER_PAID);
        data.put("allShipped", allShipped);
        data.put("shippedItems", toShip.size());
        return data;
    }

    /**
     * 发货工作台按订单号取「待发明细」：一次发货弹窗需要的全部信息。
     * <p>已发完的明细不出现在列表里，剩余量直接给前端做默认值。
     */
    public Map<String, Object> pendingByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException(400, "请输入订单号");
        }
        MallOrder order = mallOrderService.getOne(new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getOrderNo, orderNo.trim()).last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (MallOrderItem item : mallOrderItemService.list(new LambdaQueryWrapper<MallOrderItem>()
                .eq(MallOrderItem::getOrderId, order.getId()).orderByAsc(MallOrderItem::getId))) {
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            int shipped = item.getShippedQuantity() == null ? 0 : item.getShippedQuantity();
            if (shipped >= quantity) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderItemId", String.valueOf(item.getId()));
            row.put("goodsName", item.getGoodsName());
            row.put("specJson", item.getSpecJson());
            row.put("quantity", quantity);
            row.put("shippedQuantity", shipped);
            row.put("remain", quantity - shipped);
            items.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", String.valueOf(order.getId()));
        data.put("orderNo", order.getOrderNo());
        data.put("status", order.getStatus());
        data.put("receiverName", order.getReceiverName());
        data.put("receiverPhone", order.getReceiverPhone());
        data.put("receiverAddress", order.getReceiverAddress());
        data.put("items", items);
        data.put("deliverable", Integer.valueOf(ORDER_PAID).equals(order.getStatus()) && !items.isEmpty());
        return data;
    }

    /** 待发货（含部分未发）的明细行数 */
    private long countUnshipped(Long orderId) {        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_order_item WHERE order_id = ? AND shipped_quantity < quantity"
                        + " AND deleted = 0", Long.class, orderId);
        return count == null ? 0L : count;
    }

    /** 计算本次要发的明细：指定行按指定数量，未指定行发剩余全部 */
    private List<MallDeliveryItem> resolveShipItems(List<MallOrderItem> items, List<ShipDTO.Item> requested) {
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        if (requested != null && !requested.isEmpty()) {
            for (ShipDTO.Item req : requested) {
                if (req.getOrderItemId() == null || req.getQuantity() == null || req.getQuantity() <= 0) {
                    throw new BusinessException(400, "发货明细数量必须大于 0");
                }
                wanted.put(req.getOrderItemId(), req.getQuantity());
            }
        }
        List<MallDeliveryItem> list = new ArrayList<>();
        for (MallOrderItem item : items) {
            int shipped = item.getShippedQuantity() == null ? 0 : item.getShippedQuantity();
            int remain = (item.getQuantity() == null ? 0 : item.getQuantity()) - shipped;
            if (remain <= 0) {
                continue;
            }
            Integer qty = wanted.isEmpty() ? remain : wanted.get(item.getId());
            if (qty == null) {
                continue;
            }
            if (qty > remain) {
                throw new BusinessException(400, "「" + item.getGoodsName() + "」最多还能发 " + remain + " 件");
            }
            MallDeliveryItem di = new MallDeliveryItem();
            di.setOrderItemId(item.getId());
            di.setSkuId(item.getSkuId());
            di.setGoodsName(item.getGoodsName());
            di.setQuantity(qty);
            list.add(di);
        }
        if (!wanted.isEmpty() && list.isEmpty()) {
            throw new BusinessException(400, "所选明细已发完或不属于该订单");
        }
        return list;
    }

    /**
     * 批量发货：逐行独立事务语义（单行失败只记错误，不影响其他行）。
     */
    public Map<String, Object> batch(DeliveryBatchDTO dto, String operator) {
        if (dto == null || dto.getRows() == null || dto.getRows().isEmpty()) {
            throw new BusinessException(400, "导入内容为空");
        }
        int success = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        List<String> seenExpress = new ArrayList<>();
        int rowNo = 0;
        for (DeliveryBatchDTO.Row row : dto.getRows()) {
            rowNo++;
            try {
                if (!StringUtils.hasText(row.getOrderNo())) {
                    throw new BusinessException(400, "订单号为空");
                }
                if (!StringUtils.hasText(row.getExpressCompany())) {
                    throw new BusinessException(400, "快递公司为空");
                }
                if (!StringUtils.hasText(row.getExpressNo())) {
                    throw new BusinessException(400, "快递单号为空");
                }
                if (seenExpress.contains(row.getExpressNo().trim())) {
                    throw new BusinessException(400, "本批次内快递单号重复");
                }
                seenExpress.add(row.getExpressNo().trim());
                MallOrder order = mallOrderService.getOne(new LambdaQueryWrapper<MallOrder>()
                        .eq(MallOrder::getOrderNo, row.getOrderNo().trim()).last("LIMIT 1"));
                if (order == null) {
                    throw new BusinessException(400, "订单不存在");
                }
                ShipDTO ship = new ShipDTO();
                ship.setOrderId(order.getId());
                ship.setExpressCompany(row.getExpressCompany());
                ship.setExpressNo(row.getExpressNo());
                ship(ship, operator);
                success++;
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("row", rowNo);
                err.put("orderNo", row.getOrderNo());
                err.put("expressNo", row.getExpressNo());
                err.put("reason", e.getMessage());
                errors.add(err);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", dto.getRows().size());
        data.put("success", success);
        data.put("failed", errors.size());
        data.put("errors", errors);
        return data;
    }

    public Page<Map<String, Object>> page(String keyword, Integer status, long pageNum, long pageSize) {
        LambdaQueryWrapper<MallDelivery> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(MallDelivery::getOrderNo, kw).or().like(MallDelivery::getExpressNo, kw));
        }
        if (status != null) {
            wrapper.eq(MallDelivery::getStatus, status);
        }
        wrapper.orderByDesc(MallDelivery::getId);
        Page<MallDelivery> page = deliveryMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<Map<String, Object>> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(d -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(d.getId()));
            row.put("deliveryNo", d.getDeliveryNo());
            row.put("orderNo", d.getOrderNo());
            row.put("expressCompany", d.getExpressCompany());
            row.put("expressNo", d.getExpressNo());
            row.put("status", d.getStatus());
            row.put("deliveryTime", d.getDeliveryTime());
            row.put("receiverName", d.getReceiverName());
            row.put("itemCount", deliveryItemMapper.selectCount(new LambdaQueryWrapper<MallDeliveryItem>()
                    .eq(MallDeliveryItem::getDeliveryId, d.getId())));
            return row;
        }).toList());
        return result;
    }

    /** 发货单详情：包裹内明细 */
    public Map<String, Object> detail(Long id) {
        MallDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) {
            throw new BusinessException(400, "发货单不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("delivery", delivery);
        data.put("items", deliveryItemMapper.selectList(new LambdaQueryWrapper<MallDeliveryItem>()
                .eq(MallDeliveryItem::getDeliveryId, id)).stream().map(i -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("goodsName", i.getGoodsName());
            row.put("skuId", i.getSkuId());
            row.put("quantity", i.getQuantity());
            return row;
        }).toList());
        return data;
    }

    /** 订单的全部包裹（含轨迹），C 端「查看物流」用 */
    public Map<String, Object> trackByOrder(Long orderId, Long memberId) {
        MallOrder order = mallOrderService.getById(orderId);
        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }
        if (memberId != null && !memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权查看他人订单物流");
        }
        List<MallDelivery> deliveries = deliveryMapper.selectList(new LambdaQueryWrapper<MallDelivery>()
                .eq(MallDelivery::getOrderId, orderId).orderByAsc(MallDelivery::getId));
        List<Map<String, Object>> packages = new ArrayList<>();
        for (MallDelivery delivery : deliveries) {
            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("deliveryNo", delivery.getDeliveryNo());
            pkg.put("expressCompany", delivery.getExpressCompany());
            pkg.put("expressNo", delivery.getExpressNo());
            pkg.put("deliveryTime", delivery.getDeliveryTime());
            pkg.put("items", deliveryItemMapper.selectList(new LambdaQueryWrapper<MallDeliveryItem>()
                    .eq(MallDeliveryItem::getDeliveryId, delivery.getId())).stream().map(i -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("goodsName", i.getGoodsName());
                row.put("quantity", i.getQuantity());
                return row;
            }).toList());
            List<LogisticsProvider.TrackNode> nodes;
            try {
                nodes = logisticsProvider.track(delivery.getExpressCompany(), delivery.getExpressNo(),
                        delivery.getDeliveryTime());
            } catch (Exception e) {
                log.warn("物流轨迹查询失败（返回空节点）：{}", e.getMessage());
                nodes = List.of();
            }
            pkg.put("nodes", nodes.stream().map(n -> {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("time", n.getTime());
                node.put("status", n.getStatus());
                node.put("desc", n.getDesc());
                return node;
            }).toList());
            packages.add(pkg);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("orderStatus", order.getStatus());
        data.put("packages", packages);
        data.put("tip", packages.isEmpty() ? "暂无物流信息" : "");
        return data;
    }

    private void writeLog(MallOrder order, String content, String operator) {
        MallOrderLog logRow = new MallOrderLog();
        logRow.setOrderId(order.getId());
        logRow.setOrderNo(order.getOrderNo());
        logRow.setContent(content);
        logRow.setOperator(operator == null ? String.valueOf(UserContext.getUsername()) : operator);
        logRow.setOperatorType(2);
        mallOrderLogService.save(logRow);
    }

    private void pushDeliverNotice(MallOrder order, MallDelivery delivery) {
        try {
            NoticePushDTO notice = new NoticePushDTO();
            notice.setMemberId(order.getMemberId());
            notice.setType(1);
            notice.setTitle("订单已发货");
            notice.setContent("订单 " + order.getOrderNo() + " 已发货，物流："
                    + delivery.getExpressCompany() + " " + delivery.getExpressNo());
            notice.setBizId(order.getOrderNo());
            memberInnerClient.pushNotice(notice);
        } catch (Exception e) {
            log.warn("发货消息推送失败（已降级），订单 {}：{}", order.getOrderNo(), e.getMessage());
        }
    }
}
