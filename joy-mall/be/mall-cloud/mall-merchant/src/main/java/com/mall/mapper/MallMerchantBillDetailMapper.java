package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.MallMerchantBillDetail;
import com.mall.merchant.scope.MerchantScoped;
import org.apache.ibatis.annotations.Mapper;

/**
 * MallMerchantBillDetail Mapper（V10 商家域）
 */
@Mapper
@MerchantScoped
public interface MallMerchantBillDetailMapper extends BaseMapper<MallMerchantBillDetail> {
}
