package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallSeckillActivity;
import com.mall.mapper.MallSeckillActivityMapper;
import com.mall.service.MallSeckillActivityService;
import org.springframework.stereotype.Service;

/**
 * 秒杀活动 Service
 */
@Service
public class MallSeckillActivityServiceImpl
        extends ServiceImpl<MallSeckillActivityMapper, MallSeckillActivity>
        implements MallSeckillActivityService {
}
