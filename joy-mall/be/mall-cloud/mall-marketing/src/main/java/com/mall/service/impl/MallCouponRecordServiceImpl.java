package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallCouponRecord;
import com.mall.mapper.MallCouponRecordMapper;
import com.mall.service.MallCouponRecordService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 领券记录 Service
 */
@Service
public class MallCouponRecordServiceImpl extends ServiceImpl<MallCouponRecordMapper, MallCouponRecord> implements MallCouponRecordService {

    private final JdbcTemplate jdbcTemplate;

    public MallCouponRecordServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // <gen:begin key="service-body">
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====

    @Override
    public boolean lockRecord(Long recordId, Long orderId) {
        int affected = jdbcTemplate.update(
                "UPDATE mall_coupon_record SET order_id = ? WHERE id = ? AND status = 1 AND order_id IS NULL",
                orderId, recordId);
        return affected == 1;
    }

    @Override
    public void useByOrder(Long orderId) {
        jdbcTemplate.update(
                "UPDATE mall_coupon_record SET status = 2, used_time = NOW() WHERE order_id = ? AND status = 1",
                orderId);
    }

    @Override
    public void releaseByOrder(Long orderId) {
        jdbcTemplate.update(
                "UPDATE mall_coupon_record SET order_id = NULL WHERE order_id = ? AND status = 1",
                orderId);
    }
}