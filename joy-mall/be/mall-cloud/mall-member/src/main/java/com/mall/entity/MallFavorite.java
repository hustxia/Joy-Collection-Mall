package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品收藏（V7 手写）：同一会员同一商品只保留一行（uk_favorite_member_goods 含 deleted），
 * 取消收藏走逻辑删，再次收藏恢复原行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_favorite")
public class MallFavorite extends BaseEntity {

    /**
     * 会员ID 关联mall_member
     */
    private Long memberId;

    /**
     * 商品ID 关联mall_goods
     */
    private Long goodsId;
}
