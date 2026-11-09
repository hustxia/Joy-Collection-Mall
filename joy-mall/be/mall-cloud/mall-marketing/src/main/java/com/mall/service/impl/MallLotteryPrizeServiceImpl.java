package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallLotteryPrize;
import com.mall.mapper.MallLotteryPrizeMapper;
import com.mall.service.MallLotteryPrizeService;
import org.springframework.stereotype.Service;

/**
 * 抽奖奖品 Service
 */
@Service
public class MallLotteryPrizeServiceImpl
        extends ServiceImpl<MallLotteryPrizeMapper, MallLotteryPrize>
        implements MallLotteryPrizeService {
}
