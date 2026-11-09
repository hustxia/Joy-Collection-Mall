package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.dto.MemberBenefitBrief;
import com.mall.dto.MallMemberLevelSaveDTO;
import com.mall.entity.MallMember;
import com.mall.entity.MallMemberLevel;
import com.mall.entity.MallMemberLevelLog;
import com.mall.mapper.MallMemberLevelLogMapper;
import com.mall.mapper.MallMemberLevelMapper;
import com.mall.mapper.MallMemberMapper;
import com.mall.service.MallMemberLevelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员等级与权益实现（V13）。
 *
 * <p>升级判定「每次成长值变动都重算一遍」而不是「只在跨阈值时加一级」，理由：运营会把阈值调高调低，
 * 也会插入新的中间等级；重算永远收敛到当前配置，而增量加级会留下历史脏等级。
 * 重算是纯内存比较，成本可以忽略。
 *
 * <p>包邮次数按「自然月」计：{@code free_shipping_month} 记录统计月份，跨月第一条请求把计数归 1，
 * 用条件 UPDATE 一次完成「判额度 + 扣次数」，并发下不会出现两个人各扣一次却只记 1 次。
 */
@Slf4j
@Service
public class MallMemberLevelServiceImpl extends ServiceImpl<MallMemberLevelMapper, MallMemberLevel>
        implements MallMemberLevelService {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MallMemberMapper memberMapper;
    private final MallMemberLevelLogMapper levelLogMapper;
    private final JdbcTemplate jdbcTemplate;

    public MallMemberLevelServiceImpl(MallMemberMapper memberMapper,
                                      MallMemberLevelLogMapper levelLogMapper,
                                      JdbcTemplate jdbcTemplate) {
        this.memberMapper = memberMapper;
        this.levelLogMapper = levelLogMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<MallMemberLevel> enabledLevels() {
        return list(new LambdaQueryWrapper<MallMemberLevel>()
                .eq(MallMemberLevel::getStatus, 1)
                .orderByAsc(MallMemberLevel::getGrowthThreshold)
                .orderByAsc(MallMemberLevel::getLevelNo));
    }

    @Override
    public MemberBenefitBrief benefitOf(Long memberId) {
        MemberBenefitBrief brief = new MemberBenefitBrief();
        if (memberId == null) {
            brief.setDiscountRate(100);
            brief.setFreeShippingRemain(0);
            return brief;
        }
        MallMember member = memberMapper.selectById(memberId);
        if (member == null) {
            brief.setMemberId(memberId);
            brief.setDiscountRate(100);
            brief.setFreeShippingRemain(0);
            return brief;
        }
        long growth = member.getGrowth() == null ? 0L : member.getGrowth();
        List<MallMemberLevel> levels = enabledLevels();
        MallMemberLevel current = resolveLevel(levels, growth, member.getLevelId());
        brief.setMemberId(memberId);
        brief.setGrowth(growth);
        if (current == null) {
            brief.setDiscountRate(100);
            brief.setFreeShippingRemain(0);
            return brief;
        }
        brief.setLevelId(current.getId());
        brief.setLevelName(current.getName());
        brief.setDiscountRate(current.getDiscountRate() == null ? 100 : current.getDiscountRate());
        brief.setFreeShippingRemain(freeShippingRemain(member, current));
        MallMemberLevel next = nextLevel(levels, current);
        if (next != null) {
            brief.setNextLevelName(next.getName());
            long threshold = next.getGrowthThreshold() == null ? 0L : next.getGrowthThreshold();
            brief.setNextLevelGap(Math.max(threshold - growth, 0L));
        } else {
            brief.setNextLevelGap(0L);
        }
        return brief;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grantGrowth(Long memberId, long value, String reason) {
        if (memberId == null || value == 0L) {
            return 0;
        }
        MallMember member = memberMapper.selectById(memberId);
        if (member == null) {
            log.debug("成长值变动跳过：会员不存在 {}", memberId);
            return 0;
        }
        long before = member.getGrowth() == null ? 0L : member.getGrowth();
        long after = Math.max(before + value, 0L);
        if (after == before) {
            return Math.toIntExact(before);
        }
        // 原子自增/自减：并发送达两笔成长值时不会互相覆盖
        jdbcTemplate.update("UPDATE mall_member SET growth = GREATEST(growth + ?, 0) WHERE id = ? AND deleted = 0",
                value, memberId);

        List<MallMemberLevel> levels = enabledLevels();
        MallMemberLevel target = resolveLevel(levels, after, member.getLevelId());
        Long beforeLevelId = member.getLevelId();
        if (target != null && !target.getId().equals(beforeLevelId)) {
            memberMapper.update(null, new LambdaUpdateWrapper<MallMember>()
                    .eq(MallMember::getId, memberId)
                    .set(MallMember::getLevelId, target.getId()));
            MallMemberLevel beforeLevel = beforeLevelId == null ? null : getById(beforeLevelId);
            MallMemberLevelLog logRow = new MallMemberLevelLog();
            logRow.setMemberId(memberId);
            logRow.setBeforeLevelId(beforeLevelId);
            logRow.setAfterLevelId(target.getId());
            logRow.setBeforeLevelName(beforeLevel == null ? null : beforeLevel.getName());
            logRow.setAfterLevelName(target.getName());
            logRow.setGrowth(after);
            logRow.setReason(StringUtils.hasText(reason) ? reason : "manual");
            logRow.setRemark("成长值 " + before + " → " + after);
            levelLogMapper.insert(logRow);
            log.info("会员等级变更：memberId={}, {} → {}, growth={}", memberId,
                    logRow.getBeforeLevelName(), logRow.getAfterLevelName(), after);
        }
        return Math.toIntExact(after);
    }

    @Override
    public boolean consumeFreeShipping(Long memberId) {
        if (memberId == null) {
            return false;
        }
        MemberBenefitBrief brief = benefitOf(memberId);
        if (brief.getFreeShippingRemain() == null || brief.getFreeShippingRemain() <= 0) {
            return false;
        }
        MallMemberLevel level = brief.getLevelId() == null ? null : getById(brief.getLevelId());
        int quota = level == null || level.getFreeShippingCount() == null ? 0 : level.getFreeShippingCount();
        if (quota <= 0) {
            return false;
        }
        String month = YearMonth.now().format(MONTH_FMT);
        // 一次条件 UPDATE 完成「跨月归零 / 判额度 / 扣次数」，并发下只有一个人能扣到最后一 次
        int rows = jdbcTemplate.update(
                "UPDATE mall_member SET free_shipping_used = IF(free_shipping_month = ?, free_shipping_used + 1, 1),"
                        + " free_shipping_month = ?"
                        + " WHERE id = ? AND deleted = 0"
                        + " AND (free_shipping_month IS NULL OR free_shipping_month <> ? OR free_shipping_used < ?)",
                month, month, memberId, month, quota);
        return rows > 0;
    }

    @Override
    public Map<String, Object> myLevel(Long memberId) {
        MallMember member = memberId == null ? null : memberMapper.selectById(memberId);
        long growth = member == null || member.getGrowth() == null ? 0L : member.getGrowth();
        List<MallMemberLevel> levels = enabledLevels();
        MallMemberLevel current = resolveLevel(levels, growth, member == null ? null : member.getLevelId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("growth", growth);
        data.put("levelId", current == null ? null : String.valueOf(current.getId()));
        data.put("levelName", current == null ? "普通会员" : current.getName());
        data.put("discountRate", current == null || current.getDiscountRate() == null
                ? 100 : current.getDiscountRate());
        data.put("discountText", current == null || current.getDiscountRate() == null
                || current.getDiscountRate() >= 100 ? "无折扣" : rateText(current.getDiscountRate()));
        data.put("freeShippingRemain", member == null || current == null ? 0 : freeShippingRemain(member, current));
        data.put("freeShippingCount", current == null || current.getFreeShippingCount() == null
                ? 0 : current.getFreeShippingCount());
        data.put("freeShippingUsed", member == null || member.getFreeShippingUsed() == null
                ? 0 : member.getFreeShippingUsed());
        MallMemberLevel next = current == null ? null : nextLevel(levels, current);
        data.put("nextLevelName", next == null ? null : next.getName());
        long nextThreshold = next == null || next.getGrowthThreshold() == null ? 0L : next.getGrowthThreshold();
        long currentThreshold = current == null || current.getGrowthThreshold() == null
                ? 0L : current.getGrowthThreshold();
        data.put("nextLevelGap", next == null ? 0L : Math.max(nextThreshold - growth, 0L));
        long span = Math.max(nextThreshold - currentThreshold, 0L);
        int progress = next == null ? 100
                : (int) Math.min(Math.max(span == 0 ? 100 : (growth - currentThreshold) * 100 / span, 0), 100);
        data.put("progress", progress);
        data.put("levels", levels.stream().map(l -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(l.getId()));
            row.put("name", l.getName());
            row.put("growthThreshold", l.getGrowthThreshold());
            row.put("discountRate", l.getDiscountRate());
            row.put("freeShippingCount", l.getFreeShippingCount());
            row.put("benefits", parseBenefits(l.getBenefitsJson()));
            row.put("current", current != null && current.getId().equals(l.getId()));
            return row;
        }).toList());
        List<MallMemberLevelLog> logs = levelLogMapper.selectList(new LambdaQueryWrapper<MallMemberLevelLog>()
                .eq(MallMemberLevelLog::getMemberId, memberId)
                .orderByDesc(MallMemberLevelLog::getId)
                .last("LIMIT 10"));
        data.put("logs", logs);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLevel(MallMemberLevelSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new BusinessException(400, "等级名称不能为空");
        }
        Integer rate = dto.getDiscountRate() == null ? 100 : dto.getDiscountRate();
        if (rate < 1 || rate > 100) {
            throw new BusinessException(400, "折扣率范围 1~100（100=不打折）");
        }
        MallMemberLevel entity = dto.getId() == null ? new MallMemberLevel() : getById(dto.getId());
        if (entity == null) {
            throw new BusinessException(400, "等级不存在");
        }
        entity.setName(dto.getName().trim());
        entity.setLevelNo(dto.getLevelNo() == null ? 1 : dto.getLevelNo());
        entity.setGrowthThreshold(dto.getGrowthThreshold() == null ? 0L : Math.max(dto.getGrowthThreshold(), 0L));
        entity.setDiscountRate(rate);
        entity.setFreeShippingCount(dto.getFreeShippingCount() == null
                ? 0 : Math.max(dto.getFreeShippingCount(), 0));
        entity.setBenefitsJson(toBenefitsJson(dto.getBenefits()));
        entity.setIcon(dto.getIcon());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setSort(dto.getSort() == null ? entity.getLevelNo() : dto.getSort());
        saveOrUpdate(entity);
    }

    @Override
    public void removeLevel(Long id) {
        MallMemberLevel level = getById(id);
        if (level == null) {
            throw new BusinessException(400, "等级不存在");
        }
        Long used = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_member WHERE level_id = ? AND deleted = 0", Long.class, id);
        if (used != null && used > 0) {
            throw new BusinessException(400, "该等级下还有 " + used + " 个会员，请先调整等级配置");
        }
        removeById(id);
    }

    // ===== 内部辅助 =====

    /** 成长值对应的等级：取阈值不超过成长值的最高一档；都不到就取最低一档 */
    private MallMemberLevel resolveLevel(List<MallMemberLevel> levels, long growth, Long currentLevelId) {
        if (levels.isEmpty()) {
            return currentLevelId == null ? null : getById(currentLevelId);
        }
        MallMemberLevel hit = null;
        for (MallMemberLevel level : levels) {
            long threshold = level.getGrowthThreshold() == null ? 0L : level.getGrowthThreshold();
            if (threshold <= growth) {
                hit = level;
            }
        }
        return hit == null ? levels.get(0) : hit;
    }

    private MallMemberLevel nextLevel(List<MallMemberLevel> levels, MallMemberLevel current) {
        if (current == null) {
            return levels.isEmpty() ? null : levels.get(0);
        }
        // levels 已按阈值升序，当前等级的下一个就是「下一档」
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).getId().equals(current.getId())) {
                return i + 1 < levels.size() ? levels.get(i + 1) : null;
            }
        }
        return null;
    }

    private int freeShippingRemain(MallMember member, MallMemberLevel level) {
        int quota = level.getFreeShippingCount() == null ? 0 : level.getFreeShippingCount();
        if (quota <= 0) {
            return 0;
        }
        String month = YearMonth.now().format(MONTH_FMT);
        int used = month.equals(member.getFreeShippingMonth()) && member.getFreeShippingUsed() != null
                ? member.getFreeShippingUsed() : 0;
        return Math.max(quota - used, 0);
    }

    private List<String> parseBenefits(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return OM.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            // 兼容运营直接在库里填「9折,包邮」这种逗号分隔的老数据
            return List.of(json.split("[,，]"));
        }
    }

    private String toBenefitsJson(List<String> benefits) {
        List<String> clean = benefits == null ? new ArrayList<>()
                : benefits.stream().filter(StringUtils::hasText).map(String::trim).toList();
        try {
            return OM.writeValueAsString(clean);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String rateText(int rate) {
        if (rate % 10 == 0) {
            return (rate / 10) + "折";
        }
        return (rate / 10.0) + "折";
    }

    /** 供其它服务/测试判断「今天」是哪个月，避免各处自己拼格式 */
    public static String currentMonth() {
        return LocalDate.now().format(MONTH_FMT);
    }
}
