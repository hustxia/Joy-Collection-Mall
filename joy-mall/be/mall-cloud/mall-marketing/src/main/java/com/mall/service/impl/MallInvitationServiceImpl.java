package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallInvitation;
import com.mall.mapper.MallInvitationMapper;
import com.mall.service.MallInvitationService;
import org.springframework.stereotype.Service;

/**
 * 邀请关系 Service 实现
 */
@Service
public class MallInvitationServiceImpl
        extends ServiceImpl<MallInvitationMapper, MallInvitation>
        implements MallInvitationService {
}
