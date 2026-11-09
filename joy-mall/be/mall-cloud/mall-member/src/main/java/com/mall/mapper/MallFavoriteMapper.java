package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.MallFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 商品收藏 Mapper（V7 手写）
 */
@Mapper
public interface MallFavoriteMapper extends BaseMapper<MallFavorite> {

    /**
     * 查任意状态的收藏行（含已逻辑删除），收藏切换用。
     * <p>uk_favorite_member_goods(member_id, goods_id, deleted) 允许 (m,g,0) 与 (m,g,1) 并存，
     * 这里按 deleted 升序优先取有效行。
     */
    @Select("SELECT * FROM mall_favorite WHERE member_id = #{memberId} AND goods_id = #{goodsId}"
            + " ORDER BY deleted ASC LIMIT 1")
    MallFavorite selectAny(@Param("memberId") Long memberId, @Param("goodsId") Long goodsId);

    /** 恢复已逻辑删除的收藏行（再次收藏不插新行，避免撞唯一键） */
    @Update("UPDATE mall_favorite SET deleted = 0, update_time = NOW() WHERE id = #{id}")
    int restore(@Param("id") Long id);
}
