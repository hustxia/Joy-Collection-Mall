package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallPromotionGoods;
import com.mall.mapper.MallPromotionGoodsMapper;
import com.mall.service.MallPromotionGoodsService;
import org.springframework.stereotype.Service;

/**
 * 推广商品 Service 实现
 */
@Service
public class MallPromotionGoodsServiceImpl
        extends ServiceImpl<MallPromotionGoodsMapper, MallPromotionGoods>
        implements MallPromotionGoodsService {
}
