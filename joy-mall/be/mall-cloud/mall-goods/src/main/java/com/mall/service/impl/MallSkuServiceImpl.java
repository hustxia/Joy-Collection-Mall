package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallSku;
import com.mall.mapper.MallSkuMapper;
import com.mall.service.MallSkuService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 商品SKU Service
 */
@Service
public class MallSkuServiceImpl extends ServiceImpl<MallSkuMapper, MallSku> implements MallSkuService {

    private final JdbcTemplate jdbcTemplate;

    public MallSkuServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // <gen:begin key="service-body">
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====

    @Override
    public boolean deductStock(Map<Long, Integer> deltas) {
        List<Long> doneIds = new ArrayList<>();
        List<Integer> doneQty = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : deltas.entrySet()) {
            int qty = e.getValue();
            int affected = jdbcTemplate.update(
                    "UPDATE mall_sku SET stock = stock - ? WHERE id = ? AND stock >= ? AND deleted = 0",
                    qty, e.getKey(), qty);
            if (affected == 0) {
                // 回补已扣部分，保证整体原子（失败即全失败）
                for (int i = 0; i < doneIds.size(); i++) {
                    jdbcTemplate.update("UPDATE mall_sku SET stock = stock + ? WHERE id = ? AND deleted = 0",
                            doneQty.get(i), doneIds.get(i));
                }
                return false;
            }
            doneIds.add(e.getKey());
            doneQty.add(qty);
        }
        return true;
    }

    @Override
    public void restoreStock(Map<Long, Integer> deltas) {
        for (Map.Entry<Long, Integer> e : deltas.entrySet()) {
            jdbcTemplate.update("UPDATE mall_sku SET stock = stock + ? WHERE id = ? AND deleted = 0",
                    e.getValue(), e.getKey());
        }
    }

    @Override
    public void incrementSales(Map<Long, Integer> deltas) {
        // GREATEST 兜底：delta 可为负（退货退款回减销量），保证不出现负销量
        for (Map.Entry<Long, Integer> e : deltas.entrySet()) {
            jdbcTemplate.update("UPDATE mall_sku SET sales = GREATEST(sales + ?, 0) WHERE id = ? AND deleted = 0",
                    e.getValue(), e.getKey());
        }
    }
}