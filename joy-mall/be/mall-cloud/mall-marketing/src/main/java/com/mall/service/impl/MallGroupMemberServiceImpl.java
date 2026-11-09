package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallGroupMember;
import com.mall.mapper.MallGroupMemberMapper;
import com.mall.service.MallGroupMemberService;
import org.springframework.stereotype.Service;

/**
 * 拼团成员 Service
 */
@Service
public class MallGroupMemberServiceImpl
        extends ServiceImpl<MallGroupMemberMapper, MallGroupMember>
        implements MallGroupMemberService {
}
