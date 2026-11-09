package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallGroupTeam;
import com.mall.mapper.MallGroupTeamMapper;
import com.mall.service.MallGroupTeamService;
import org.springframework.stereotype.Service;

/**
 * 拼团 Service
 */
@Service
public class MallGroupTeamServiceImpl
        extends ServiceImpl<MallGroupTeamMapper, MallGroupTeam>
        implements MallGroupTeamService {
}
