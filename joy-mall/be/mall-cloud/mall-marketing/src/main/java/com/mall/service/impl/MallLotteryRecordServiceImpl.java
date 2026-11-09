package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallLotteryRecord;
import com.mall.mapper.MallLotteryRecordMapper;
import com.mall.service.MallLotteryRecordService;
import org.springframework.stereotype.Service;

/**
 * 抽奖记录 Service
 */
@Service
public class MallLotteryRecordServiceImpl
        extends ServiceImpl<MallLotteryRecordMapper, MallLotteryRecord>
        implements MallLotteryRecordService {
}
