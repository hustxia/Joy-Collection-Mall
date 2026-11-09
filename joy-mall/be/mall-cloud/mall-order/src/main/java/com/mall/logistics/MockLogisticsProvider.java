package com.mall.logistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 模拟物流轨迹（V12 默认实现）：按发货时间推算四个节点，只返回「已发生」的节点。
 *
 * <p>用途：本地全栈演示与自动化验收（对接真实物流服务需要付费账号与快递公司编码，
 * 属于「待决策」项）。节点间隔 1 分钟，方便测「发货 → 立即可查轨迹」的链路。
 */
@Slf4j
@Component
public class MockLogisticsProvider implements LogisticsProvider {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 节点定义：相对发货时间的分钟偏移 */
    private static final String[][] NODES = {
            {"0", "已揽收", "【%s】已揽收，快递员：模拟快递"},
            {"1", "运输中", "快件已离开【%s】分拨中心，发往下一站"},
            {"2", "派送中", "快件已到达【%s】派送网点，正在派送"},
            {"3", "已签收", "快件已签收，签收人：本人"}
    };

    @Override
    public String code() {
        return "mock";
    }

    @Override
    public List<TrackNode> track(String company, String expressNo, LocalDateTime deliveryTime) {
        List<TrackNode> nodes = new ArrayList<>();
        if (deliveryTime == null) {
            return nodes;
        }
        LocalDateTime now = LocalDateTime.now();
        for (int i = NODES.length - 1; i >= 0; i--) {
            LocalDateTime at = deliveryTime.plusMinutes(Long.parseLong(NODES[i][0]));
            // 揽收节点与发货同刻，必然已发生；后续节点按时间推进（MySQL DATETIME 秒级四舍五入，
            // 同秒内不能用 isAfter 判定，故首节点无条件返回）
            if (i > 0 && at.isAfter(now)) {
                continue;
            }
            String companyName = company == null ? "快递" : company;
            nodes.add(new TrackNode(at.format(TS), NODES[i][1],
                    String.format(NODES[i][2], companyName) + "（单号 " + expressNo + "）"));
        }
        return nodes;
    }
}
