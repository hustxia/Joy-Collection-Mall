package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.MallCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 购物车 Mapper（V3 手写）
 */
@Mapper
public interface MallCartMapper extends BaseMapper<MallCart> {

    /**
     * 物理删除购物车行。
     * <p>mall_cart 上有 uk_member_sku(member_id, sku_id, deleted)，deleted 只有 0/1 两值，
     * 意味着「同一会员同一 SKU 最多只能存在一条已逻辑删除的行」。
     * 走 removeById 的软删（UPDATE ... SET deleted=1）时，第二次删除同一 SKU 会命中
     * Duplicate entry 'm-s-1' for key 'uk_member_sku' → 接口 500。
     * 购物车是临时数据、无历史留存价值，因此这里改物理删除，从根上绕开该索引冲突。
     */
    @Delete("DELETE FROM mall_cart WHERE id = #{id} AND member_id = #{memberId}")
    int hardDelete(@Param("id") Long id, @Param("memberId") Long memberId);

    /** 批量物理删除（下单后清勾选行用），同上：绕开 uk_member_sku 含 deleted 的软删冲突 */
    @Delete("<script>DELETE FROM mall_cart WHERE member_id = #{memberId} AND id IN "
            + "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    int hardDeleteByIds(@Param("memberId") Long memberId, @Param("ids") java.util.List<Long> ids);
}
