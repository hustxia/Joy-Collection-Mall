package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.MallMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 会员 Mapper
 */
@Mapper
public interface MallMemberMapper extends BaseMapper<MallMember> {

    // <gen:begin key="mapper-body">
    // <gen:end key="mapper-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====

    /**
     * 条件加减积分（V7）：WHERE 里带 points + delta >= 0，
     * 余额不足时影响行数为 0——并发扣减不会把积分扣成负数。
     */
    @Update("UPDATE mall_member SET points = points + #{delta}"
            + " WHERE id = #{memberId} AND deleted = 0 AND points + #{delta} >= 0")
    int addPoints(@Param("memberId") Long memberId, @Param("delta") int delta);
}