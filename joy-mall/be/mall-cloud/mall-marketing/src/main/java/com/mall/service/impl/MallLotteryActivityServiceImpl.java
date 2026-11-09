package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallLotteryActivity;
import com.mall.mapper.MallLotteryActivityMapper;
import com.mall.service.MallLotteryActivityService;
import org.springframework.stereotype.Service;

/**
 * 抽奖活动 Service
 */
@Service
public class MallLotteryActivityServiceImpl
        extends ServiceImpl<MallLotteryActivityMapper, MallLotteryActivity>
        implements MallLotteryActivityService {
}
