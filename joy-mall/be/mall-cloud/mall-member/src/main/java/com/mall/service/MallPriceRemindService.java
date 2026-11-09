package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallPriceRemind;

import java.util.List;
import java.util.Map;

/**
 * 降价提醒服务（V13 购物车营销）。
 *
 * <p>触发方式：定时扫描（{@code mall.price-remind.cron}）拿「监控中」的订阅，按 SKU 现价比对期望价，
 * 命中就把状态从 0 条件更新成 1 并推站内信。用扫描而不是「改价事件」的原因：
 * 改价入口分散在商品后台、批量导入、脚本三处，逐个埋点容易漏；扫描只看结果（现价 ≤ 期望价），
 * 任何入口改价都能覆盖，且条件更新天然幂等（同一会员同一商品只通知一次）。
 */
public interface MallPriceRemindService extends IService<MallPriceRemind> {

    /** 订阅（同一会员同一商品重复订阅只更新期望价，不产生第二行） */
    void subscribe(Long memberId, Long goodsId, Long skuId, Long expectedPrice);

    /** 我的订阅（带当前价与是否已达期望价） */
    List<Map<String, Object>> myReminds(Long memberId);

    /** 取消订阅（物理无关：逻辑删除即可，唯一键带 deleted 允许重新订阅） */
    void cancel(Long memberId, Long id);

    /**
     * 扫描并通知一轮。
     *
     * @return 本轮通知条数
     */
    int sweep();
}
