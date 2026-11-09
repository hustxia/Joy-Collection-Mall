package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.SiteNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内信 Mapper（V14）：写 V7 的 mall_notice，不新建表
 */
@Mapper
public interface SiteNoticeMapper extends BaseMapper<SiteNotice> {
}
