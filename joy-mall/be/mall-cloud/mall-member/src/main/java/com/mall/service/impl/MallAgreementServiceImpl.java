package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallAgreement;
import com.mall.entity.MallMemberAgreementLog;
import com.mall.mapper.MallAgreementMapper;
import com.mall.mapper.MallMemberAgreementLogMapper;
import com.mall.service.MallAgreementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 协议实现（V11 合规）。
 *
 * <p>生效判定放在 SQL 里（status + effective_time <= NOW()）而不是内存里过滤：
 * 运营定时发布新版时，前台无需重启就能切到新版本。
 */
@Slf4j
@Service
public class MallAgreementServiceImpl extends ServiceImpl<MallAgreementMapper, MallAgreement>
        implements MallAgreementService {

    private final MallMemberAgreementLogMapper agreementLogMapper;

    public MallAgreementServiceImpl(MallMemberAgreementLogMapper agreementLogMapper) {
        this.agreementLogMapper = agreementLogMapper;
    }

    @Override
    public MallAgreement current(int type) {
        List<MallAgreement> list = list(new LambdaQueryWrapper<MallAgreement>()
                .eq(MallAgreement::getType, type)
                .eq(MallAgreement::getStatus, 1)
                .and(w -> w.isNull(MallAgreement::getEffectiveTime)
                        .or().le(MallAgreement::getEffectiveTime, LocalDateTime.now()))
                .orderByDesc(MallAgreement::getEffectiveTime)
                .orderByDesc(MallAgreement::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<MallAgreement> listPublished() {
        return list(new LambdaQueryWrapper<MallAgreement>()
                .eq(MallAgreement::getStatus, 1)
                .orderByAsc(MallAgreement::getType)
                .orderByDesc(MallAgreement::getEffectiveTime));
    }

    @Override
    public MallAgreement record(Long memberId, int type, String ip) {
        MallAgreement agreement = current(type);
        if (agreement == null) {
            log.warn("会员 {} 同意协议时没有生效版本：type={}", memberId, type);
            return null;
        }
        MallMemberAgreementLog row = new MallMemberAgreementLog();
        row.setMemberId(memberId);
        row.setAgreementId(agreement.getId());
        row.setAgreementType(type);
        row.setVersion(agreement.getVersion());
        row.setAgreeTime(LocalDateTime.now());
        row.setIp(ip);
        agreementLogMapper.insert(row);
        return agreement;
    }
}
