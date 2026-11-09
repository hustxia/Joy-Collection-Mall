package com.mall.logistics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 物流轨迹 SPI（V12）。
 *
 * <p>接快递100 / 快递鸟时实现本接口并替换 {@code mall.logistics.provider} 配置即可，
 * 订单与发货逻辑不变。轨迹查询失败必须返回空列表而不是抛异常——物流查不到
 * 不该让用户打不开订单详情。
 */
public interface LogisticsProvider {

    /** 提供方编码：mock / kuaidi100 / kuaidiniao */
    String code();

    /**
     * 查询轨迹。
     *
     * @param company      快递公司名
     * @param expressNo    快递单号
     * @param deliveryTime 发货时间（mock 用于推算节点时间）
     * @return 按时间倒序的节点列表；无轨迹返回空列表
     */
    List<TrackNode> track(String company, String expressNo, LocalDateTime deliveryTime);

    /** 轨迹节点 */
    class TrackNode {
        private String time;
        private String status;
        private String desc;

        public TrackNode() {
        }

        public TrackNode(String time, String status, String desc) {
            this.time = time;
            this.status = status;
            this.desc = desc;
        }

        public String getTime() {
            return time;
        }

        public String getStatus() {
            return status;
        }

        public String getDesc() {
            return desc;
        }
    }
}
