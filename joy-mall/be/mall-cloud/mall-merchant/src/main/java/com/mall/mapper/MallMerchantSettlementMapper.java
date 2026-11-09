package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.MallMerchantSettlement;
import com.mall.merchant.scope.MerchantScoped;
import org.apache.ibatis.annotations.Mapper;

/**
 * MallMerchantSettlement Mapper（V10 商家域）
 */
@Mapper
@MerchantScoped
public interface MallMerchantSettlementMapper extends BaseMapper<MallMerchantSettlement> {
}
