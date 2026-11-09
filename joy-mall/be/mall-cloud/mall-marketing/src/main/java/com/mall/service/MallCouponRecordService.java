package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallCouponRecord;

/**
 * 领券记录 Service
 */
public interface MallCouponRecordService extends IService<MallCouponRecord> {

    // <gen:begin key="service-body">
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====

    /** 提交订单锁定券：条件 UPDATE（status=1 且 order_id 空），成功返回 true */
    boolean lockRecord(Long recordId, Long orderId);

    /** 支付成功：将该订单下所有 status=1 记录置为已用（status=2） */
    void useByOrder(Long orderId);

    /** 取消/关闭订单：释放该订单锁定的券（order_id 置空，status 回 1） */
    void releaseByOrder(Long orderId);
}