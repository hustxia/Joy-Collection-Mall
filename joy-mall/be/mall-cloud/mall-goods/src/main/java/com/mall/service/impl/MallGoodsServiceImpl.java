package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallGoods;
import com.mall.mapper.MallGoodsMapper;
import com.mall.service.MallGoodsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 商品 Service
 */
@Service
public class MallGoodsServiceImpl extends ServiceImpl<MallGoodsMapper, MallGoods> implements MallGoodsService {

    private final JdbcTemplate jdbcTemplate;

    public MallGoodsServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // <gen:begin key="service-body">
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====

    @Override
    public void incrementSales(Map<Long, Integer> deltas) {
        // GREATEST 兜底：delta 可为负（退货退款回减销量），保证不出现负销量
        for (Map.Entry<Long, Integer> e : deltas.entrySet()) {
            jdbcTemplate.update("UPDATE mall_goods SET sales = GREATEST(sales + ?, 0) WHERE id = ? AND deleted = 0",
                    e.getValue(), e.getKey());
        }
    }
}