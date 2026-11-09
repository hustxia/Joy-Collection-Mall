package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.MallMerchantUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商家账号 Mapper。
 *
 * <p>故意<b>不</b>标注 {@code @MerchantScoped}：登录时还没有商家身份（上下文为空），
 * 一旦标注会被拦截器改写成 {@code merchant_id = null} 直接查不到人。
 */
@Mapper
public interface MallMerchantUserMapper extends BaseMapper<MallMerchantUser> {
}
