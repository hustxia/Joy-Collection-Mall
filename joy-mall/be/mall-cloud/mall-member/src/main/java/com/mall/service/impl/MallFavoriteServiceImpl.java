package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallFavorite;
import com.mall.mapper.MallFavoriteMapper;
import com.mall.service.MallFavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品收藏 Service（V7 手写）
 */
@Service
public class MallFavoriteServiceImpl extends ServiceImpl<MallFavoriteMapper, MallFavorite>
        implements MallFavoriteService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggle(Long memberId, Long goodsId) {
        MallFavorite exist = getBaseMapper().selectAny(memberId, goodsId);
        if (exist == null) {
            MallFavorite favorite = new MallFavorite();
            favorite.setMemberId(memberId);
            favorite.setGoodsId(goodsId);
            save(favorite);
            return true;
        }
        if (exist.getDeleted() != null && exist.getDeleted() == 1) {
            // 恢复原行而非插新行：uk_favorite_member_goods 含 deleted，重复插入会撞唯一键
            getBaseMapper().restore(exist.getId());
            return true;
        }
        removeById(exist.getId());
        return false;
    }

    @Override
    public boolean isFavorited(Long memberId, Long goodsId) {
        LambdaQueryWrapper<MallFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallFavorite::getMemberId, memberId).eq(MallFavorite::getGoodsId, goodsId);
        return count(wrapper) > 0;
    }

    @Override
    public Page<MallFavorite> pageOf(Long memberId, int pageNum, int pageSize) {
        Page<MallFavorite> page = new Page<>(Math.max(pageNum, 1), Math.max(pageSize, 1));
        LambdaQueryWrapper<MallFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallFavorite::getMemberId, memberId).orderByDesc(MallFavorite::getId);
        return page(page, wrapper);
    }
}
