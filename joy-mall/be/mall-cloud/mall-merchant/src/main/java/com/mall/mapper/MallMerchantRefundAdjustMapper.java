package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.MallMerchantRefundAdjust;
import com.mall.merchant.scope.MerchantScoped;
import org.apache.ibatis.annotations.Mapper;

/**
 * MallMerchantRefundAdjust Mapper（V10 商家域）
 */
@Mapper
@MerchantScoped
public interface MallMerchantRefundAdjustMapper extends BaseMapper<MallMerchantRefundAdjust> {
}
