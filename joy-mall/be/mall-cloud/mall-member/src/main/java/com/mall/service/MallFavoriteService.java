package com.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallFavorite;

/**
 * 商品收藏 Service（V7 手写）
 */
public interface MallFavoriteService extends IService<MallFavorite> {

    /**
     * 幂等切换：已有有效行→逻辑删除返回 false；已删行→恢复返回 true；无行→插入返回 true。
     */
    boolean toggle(Long memberId, Long goodsId);

    /**
     * 是否已收藏（只看有效行）。
     */
    boolean isFavorited(Long memberId, Long goodsId);

    /**
     * 当前会员收藏分页（按收藏时间倒序）。
     */
    Page<MallFavorite> pageOf(Long memberId, int pageNum, int pageSize);
}
