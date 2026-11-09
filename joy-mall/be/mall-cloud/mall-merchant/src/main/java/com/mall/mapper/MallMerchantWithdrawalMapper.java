package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.MallMerchantWithdrawal;
import com.mall.merchant.scope.MerchantScoped;
import org.apache.ibatis.annotations.Mapper;

/**
 * MallMerchantWithdrawal Mapper（V10 商家域）
 */
@Mapper
@MerchantScoped
public interface MallMerchantWithdrawalMapper extends BaseMapper<MallMerchantWithdrawal> {
}
