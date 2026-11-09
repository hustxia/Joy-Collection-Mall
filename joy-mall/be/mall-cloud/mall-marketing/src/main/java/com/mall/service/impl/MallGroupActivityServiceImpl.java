package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallGroupActivity;
import com.mall.mapper.MallGroupActivityMapper;
import com.mall.service.MallGroupActivityService;
import org.springframework.stereotype.Service;

/**
 * 拼团活动 Service
 */
@Service
public class MallGroupActivityServiceImpl
        extends ServiceImpl<MallGroupActivityMapper, MallGroupActivity>
        implements MallGroupActivityService {
}
