package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallCart;

/**
 * 购物车 Service（V3 手写）
 */
public interface MallCartService extends IService<MallCart> {

    /** 物理删除会员指定购物车项（下单成功后清空；逻辑删会撞 uk_member_sku） */
    void clearByIdIn(Long memberId, java.util.List<Long> ids);

    /** 查询会员购物车：checked 非空时仅取勾选项 */
    java.util.List<MallCart> cartOf(Long memberId, Integer checked);
}
