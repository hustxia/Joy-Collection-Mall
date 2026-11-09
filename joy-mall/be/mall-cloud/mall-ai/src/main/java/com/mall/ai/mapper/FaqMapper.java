package com.mall.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.ai.entity.Faq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FaqMapper extends BaseMapper<Faq> {

    @Update("UPDATE mall_faq SET hit_count = hit_count + 1 WHERE id = #{id}")
    int increaseHit(@Param("id") Long id);
}
