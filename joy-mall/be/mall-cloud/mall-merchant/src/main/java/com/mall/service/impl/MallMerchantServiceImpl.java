package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.dto.MallMerchantApplyDTO;
import com.mall.dto.MallMerchantPageDTO;
import com.mall.dto.MallMerchantSaveDTO;
import com.mall.entity.MallMerchant;
import com.mall.entity.MallMerchantAuditLog;
import com.mall.mapper.MallMerchantMapper;
import com.mall.service.MallMerchantAuditLogService;
import com.mall.service.MallMerchantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 商家服务实现（V10）。
 *
 * <p>状态机：0 待审 →（通过）1 启用 /（驳回）3 驳回；1 ↔ 2 停用/启用。
 * 所有状态迁移都用「带前置态的条件 UPDATE」，受影响 0 行即并发/非法，天然幂等。
 */
@Slf4j
@Service
public class MallMerchantServiceImpl extends ServiceImpl<MallMerchantMapper, MallMerchant>
        implements MallMerchantService {

    /** 状态常量 */
    public static final int ST_PENDING = 0;
    public static final int ST_ENABLED = 1;
    public static final int ST_DISABLED = 2;
    public static final int ST_REJECTED = 3;

    /** 全局默认抽成费率的字典键（避免为单个配置新建 sys_config） */
    private static final String DICT_SETTLE_RATE = "mall_settle_rate";
    private static final int FALLBACK_SETTLE_RATE = 50;

    private final MallMerchantAuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;

    public MallMerchantServiceImpl(MallMerchantAuditLogService auditLogService, JdbcTemplate jdbcTemplate) {
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<MallMerchant> pageQuery(MallMerchantPageDTO dto) {
        LambdaQueryWrapper<MallMerchant> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            String kw = dto.getKeyword().trim();
            wrapper.and(w -> w.like(MallMerchant::getName, kw)
                    .or().like(MallMerchant::getMerchantNo, kw)
                    .or().like(MallMerchant::getContactName, kw)
                    .or().like(MallMerchant::getLegalName, kw));
        }
        if (dto.getStatus() != null) {
            wrapper.eq(MallMerchant::getStatus, dto.getStatus());
        }
        if (StringUtils.hasText(dto.getCategory())) {
            wrapper.eq(MallMerchant::getCategory, dto.getCategory());
        }
        wrapper.orderByDesc(MallMerchant::getId);
        return page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallMerchant apply(MallMerchantApplyDTO dto, String operator) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new BusinessException(400, "店铺名称必填");
        }
        String licenseNo = StringUtils.hasText(dto.getLicenseNo()) ? dto.getLicenseNo().trim() : null;
        if (licenseNo != null) {
            // 被驳回的允许用同一执照重新提交，其余状态视为重复申请
            long dup = count(new LambdaQueryWrapper<MallMerchant>()
                    .eq(MallMerchant::getLicenseNo, licenseNo)
                    .ne(MallMerchant::getStatus, ST_REJECTED));
            if (dup > 0) {
                throw new BusinessException(400, "该营业执照已提交过入驻申请，请勿重复提交");
            }
        }
        int rate = dto.getSettleRate() != null ? dto.getSettleRate() : defaultSettleRate();
        if (rate < 0 || rate > 999) {
            throw new BusinessException(400, "抽成费率需在 0~999‰ 之间");
        }
        MallMerchant merchant = new MallMerchant();
        merchant.setMerchantNo(nextMerchantNo());
        merchant.setName(dto.getName().trim());
        merchant.setLegalName(dto.getLegalName());
        merchant.setLicenseNo(licenseNo);
        merchant.setLicenseImg(dto.getLicenseImg());
        merchant.setContactName(dto.getContactName());
        merchant.setContactPhone(dto.getContactPhone());
        merchant.setCategory(dto.getCategory());
        merchant.setLogo(dto.getLogo());
        merchant.setIntro(dto.getIntro());
        merchant.setSettleRate(rate);
        merchant.setStatus(ST_PENDING);
        merchant.setAccountBalance(0L);
        merchant.setSettledTotal(0L);
        save(merchant);
        auditLogService.log(merchant.getId(), MallMerchantAuditLog.ACTION_APPLY, operator, "提交入驻申请");
        return merchant;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean pass, String rejectReason, String operator) {
        MallMerchant merchant = requireMerchant(id);
        if (!Integer.valueOf(ST_PENDING).equals(merchant.getStatus())) {
            throw new BusinessException(400, "仅待审商家可审核，当前状态：" + statusText(merchant.getStatus()));
        }
        String reason = null;
        if (!pass) {
            if (!StringUtils.hasText(rejectReason)) {
                throw new BusinessException(400, "驳回必须填写原因");
            }
            reason = rejectReason.trim();
        }
        boolean updated = update(new LambdaUpdateWrapper<MallMerchant>()
                .eq(MallMerchant::getId, id)
                .eq(MallMerchant::getStatus, ST_PENDING)
                .set(MallMerchant::getStatus, pass ? ST_ENABLED : ST_REJECTED)
                .set(MallMerchant::getRejectReason, reason));
        if (!updated) {
            throw new BusinessException(400, "商家状态已变更，请刷新后重试");
        }
        auditLogService.log(id, pass ? MallMerchantAuditLog.ACTION_PASS : MallMerchantAuditLog.ACTION_REJECT,
                operator, pass ? "审核通过，店铺已启用" : "审核驳回：" + reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, boolean enable, String operator) {
        MallMerchant merchant = requireMerchant(id);
        if (Integer.valueOf(ST_PENDING).equals(merchant.getStatus())
                || Integer.valueOf(ST_REJECTED).equals(merchant.getStatus())) {
            throw new BusinessException(400, "待审/已驳回的商家不可启停，请先完成审核");
        }
        int from = enable ? ST_DISABLED : ST_ENABLED;
        int to = enable ? ST_ENABLED : ST_DISABLED;
        boolean updated = update(new LambdaUpdateWrapper<MallMerchant>()
                .eq(MallMerchant::getId, id)
                .eq(MallMerchant::getStatus, from)
                .set(MallMerchant::getStatus, to));
        if (!updated) {
            throw new BusinessException(400, "店铺状态不支持该操作（当前：" + statusText(merchant.getStatus()) + "）");
        }
        auditLogService.log(id, enable ? MallMerchantAuditLog.ACTION_ENABLE : MallMerchantAuditLog.ACTION_DISABLE,
                operator, enable ? "启用店铺" : "停用店铺");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(MallMerchantSaveDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        requireMerchant(dto.getId());
        if (dto.getSettleRate() != null && (dto.getSettleRate() < 0 || dto.getSettleRate() > 999)) {
            throw new BusinessException(400, "抽成费率需在 0~999‰ 之间");
        }
        LambdaUpdateWrapper<MallMerchant> wrapper = new LambdaUpdateWrapper<MallMerchant>()
                .eq(MallMerchant::getId, dto.getId());
        if (StringUtils.hasText(dto.getName())) {
            wrapper.set(MallMerchant::getName, dto.getName().trim());
        }
        wrapper.set(dto.getLegalName() != null, MallMerchant::getLegalName, dto.getLegalName())
                .set(dto.getLicenseImg() != null, MallMerchant::getLicenseImg, dto.getLicenseImg())
                .set(dto.getContactName() != null, MallMerchant::getContactName, dto.getContactName())
                .set(dto.getContactPhone() != null, MallMerchant::getContactPhone, dto.getContactPhone())
                .set(dto.getCategory() != null, MallMerchant::getCategory, dto.getCategory())
                .set(dto.getLogo() != null, MallMerchant::getLogo, dto.getLogo())
                .set(dto.getIntro() != null, MallMerchant::getIntro, dto.getIntro())
                .set(dto.getSettleRate() != null, MallMerchant::getSettleRate, dto.getSettleRate());
        update(wrapper);
    }

    @Override
    public MallMerchant requireEnabled(Long id) {
        MallMerchant merchant = requireMerchant(id);
        if (!Integer.valueOf(ST_ENABLED).equals(merchant.getStatus())) {
            throw new BusinessException(400, "店铺未启用：" + statusText(merchant.getStatus()));
        }
        return merchant;
    }

    private MallMerchant requireMerchant(Long id) {
        if (id == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        MallMerchant merchant = getById(id);
        if (merchant == null) {
            throw new BusinessException(400, "商家不存在");
        }
        return merchant;
    }

    /** 全局默认抽成费率（‰）：字典缺失或非法时回落 50 */
    private int defaultSettleRate() {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT value FROM sys_dict_data WHERE dict_type = ? AND deleted = 0 ORDER BY sort LIMIT 1",
                    String.class, DICT_SETTLE_RATE);
            return value == null ? FALLBACK_SETTLE_RATE : Integer.parseInt(value.trim());
        } catch (Exception e) {
            log.warn("读取全局抽成费率失败，回落 {}‰：{}", FALLBACK_SETTLE_RATE, e.getMessage());
            return FALLBACK_SETTLE_RATE;
        }
    }

    /** 商家编号：S + 8 位（时间戳低位 + 随机），撞唯一键概率极低 */
    private String nextMerchantNo() {
        long seed = System.currentTimeMillis() % 1000000L;
        return "S" + String.format("%06d%02d", seed, ThreadLocalRandom.current().nextInt(100));
    }

    public static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case ST_PENDING -> "待审";
            case ST_ENABLED -> "启用";
            case ST_DISABLED -> "停用";
            case ST_REJECTED -> "驳回";
            default -> "未知";
        };
    }
}
