package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.MallNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内消息 Mapper（V7 手写）
 */
@Mapper
public interface MallNoticeMapper extends BaseMapper<MallNotice> {
}
