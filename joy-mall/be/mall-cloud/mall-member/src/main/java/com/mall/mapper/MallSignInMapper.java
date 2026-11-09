package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.MallSignIn;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员每日签到 Mapper（V7 手写）
 */
@Mapper
public interface MallSignInMapper extends BaseMapper<MallSignIn> {
}
