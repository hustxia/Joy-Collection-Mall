package com.mall.ai.support;

import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.OrderItemBrief;
import com.mall.common.feign.dto.RefundBrief;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具取证结果：AI 回答的「事实来源」。
 *
 * <p>三个用途：拼「事实草稿」给 LLM、生成前端卡片、写 tool_trace 供审计。
 * 只要 {@code order} 为空且没有 FAQ 命中，编排器就判定为无证据 → 兜底话术。
 */
@Data
public class Evidence {

    private OrderBrief order;
    private List<OrderItemBrief> items = new ArrayList<>();
    private Map<String, Object> track;
    private RefundBrief refund;
    /** 取证过程中的降级信息（如售后域超时），只入 trace，不影响对话 */
    private List<String> warn = new ArrayList<>();

    public boolean hasOrder() {
        return order != null;
    }

    public static String orderStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 10 -> "待付款";
            case 20 -> "待发货";
            case 30 -> "已发货";
            case 40 -> "已完成";
            case 50 -> "已取消";
            case 60 -> "已关闭";
            default -> "状态" + status;
        };
    }

    public static String refundStatusText(Integer status) {
        if (status == null) {
            return "无售后";
        }
        return switch (status) {
            case 1 -> "申请中";
            case 2 -> "审核通过";
            case 3 -> "已退款";
            case 4 -> "已拒绝";
            case 5 -> "已关闭";
            default -> "状态" + status;
        };
    }

    /** 最近一条物流节点描述 */
    @SuppressWarnings("unchecked")
    public String latestNode() {
        if (track == null) {
            return null;
        }
        Object pkgs = track.get("packages");
        if (!(pkgs instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> pkg)) {
            return null;
        }
        Object nodes = pkg.get("nodes");
        if (nodes instanceof List<?> ns && !ns.isEmpty()) {
            Object last = ns.get(ns.size() - 1);
            if (last instanceof Map<?, ?> node) {
                Object desc = node.get("desc");
                Object time = node.get("time");
                return (time == null ? "" : time + " ") + (desc == null ? "" : desc);
            }
        }
        Object company = pkg.get("expressCompany");
        Object no = pkg.get("expressNo");
        return company == null ? null : company + " " + (no == null ? "" : no);
    }

    /** 物流卡片用：公司 + 单号 + 节点列表 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> packages() {
        if (track == null || !(track.get("packages") instanceof List<?> list)) {
            return List.of();
        }
        return (List<Map<String, Object>>) list;
    }

    /** 事实草稿：给 LLM 的「只能照这个说」的底稿，mock provider 直接输出 */
    public String draft() {
        if (order == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("订单 ").append(order.getOrderNo())
                .append("（").append(orderStatusText(order.getStatus())).append("）");
        if (!items.isEmpty()) {
            sb.append("，商品：").append(items.stream()
                    .map(i -> i.getGoodsName() + "×" + i.getQuantity())
                    .limit(3).reduce((a, b) -> a + "、" + b).orElse(""));
        }
        if (order.getPayAmount() != null) {
            sb.append("，实付 ¥").append(String.format("%.2f", order.getPayAmount() / 100.0));
        }
        String node = latestNode();
        if (node != null) {
            sb.append("。最新物流：").append(node);
        }
        if (refund != null) {
            sb.append("。售后单 ").append(refund.getRefundNo() == null ? "" : refund.getRefundNo())
                    .append(" 当前状态：").append(refundStatusText(refund.getStatus()));
        }
        sb.append("。");
        if (order.getStatus() != null && order.getStatus() == 20) {
            sb.append("商家备货中，发货后会同步物流。");
        }
        if (order.getStatus() != null && order.getStatus() == 40) {
            sb.append("订单已完成，如有质量问题可在签收后 7 天内申请售后。");
        }
        return sb.toString();
    }

    /** 工具调用轨迹（后台审计用）：只记事实，不记手机号等 PII */
    public String trace() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderNo", order == null ? null : order.getOrderNo());
        map.put("orderStatus", order == null ? null : order.getStatus());
        map.put("itemCount", items.size());
        map.put("hasTrack", track != null && !packages().isEmpty());
        map.put("refundStatus", refund == null ? null : refund.getStatus());
        if (!warn.isEmpty()) {
            map.put("warn", warn);
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
