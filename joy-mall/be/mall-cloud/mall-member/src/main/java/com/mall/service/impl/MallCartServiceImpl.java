package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallCart;
import com.mall.mapper.MallCartMapper;
import com.mall.service.MallCartService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 购物车 Service（V3 手写）
 */
@Service
public class MallCartServiceImpl extends ServiceImpl<MallCartMapper, MallCart> implements MallCartService {

    @Override
    public void clearByIdIn(Long memberId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 物理删除：uk_member_sku 含 deleted 列，软删会与历史已删行撞唯一键（下单清车直接 500）
        getBaseMapper().hardDeleteByIds(memberId, ids);
    }

    @Override
    public List<MallCart> cartOf(Long memberId, Integer checked) {
        LambdaQueryWrapper<MallCart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallCart::getMemberId, memberId);
        if (checked != null) {
            wrapper.eq(MallCart::getChecked, checked);
        }
        return list(wrapper);
    }
}
