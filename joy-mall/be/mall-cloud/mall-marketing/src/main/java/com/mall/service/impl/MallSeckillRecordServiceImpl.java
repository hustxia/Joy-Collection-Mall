package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallSeckillRecord;
import com.mall.mapper.MallSeckillRecordMapper;
import com.mall.service.MallSeckillRecordService;
import org.springframework.stereotype.Service;

/**
 * 秒杀参与记录 Service
 */
@Service
public class MallSeckillRecordServiceImpl
        extends ServiceImpl<MallSeckillRecordMapper, MallSeckillRecord>
        implements MallSeckillRecordService {
}
