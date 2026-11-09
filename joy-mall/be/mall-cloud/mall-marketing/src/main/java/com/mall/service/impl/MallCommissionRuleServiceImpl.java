package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallCommissionRule;
import com.mall.mapper.MallCommissionRuleMapper;
import com.mall.service.MallCommissionRuleService;
import org.springframework.stereotype.Service;

/**
 * 佣金规则 Service 实现
 */
@Service
public class MallCommissionRuleServiceImpl
        extends ServiceImpl<MallCommissionRuleMapper, MallCommissionRule>
        implements MallCommissionRuleService {
}
