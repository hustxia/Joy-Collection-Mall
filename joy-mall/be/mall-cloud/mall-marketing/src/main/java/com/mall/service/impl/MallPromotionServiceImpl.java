package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.dto.PromotionCalcRequest;
import com.mall.common.feign.dto.PromotionCalcResult;
import com.mall.common.feign.dto.PromotionHitWriteDTO;
import com.mall.dto.MallPromotionPageDTO;
import com.mall.dto.MallPromotionSaveDTO;
import com.mall.entity.MallCoupon;
import com.mall.entity.MallCouponRecord;
import com.mall.entity.MallPromotion;
import com.mall.entity.MallPromotionHit;
import com.mall.entity.MallPromotionScope;
import com.mall.mapper.MallPromotionHitMapper;
import com.mall.mapper.MallPromotionMapper;
import com.mall.mapper.MallPromotionScopeMapper;
import com.mall.promotion.CartLine;
import com.mall.promotion.PromotionCalculator;
import com.mall.promotion.PromotionResult;
import com.mall.promotion.PromotionRule;
import com.mall.promotion.PromotionRuleCodec;
import com.mall.promotion.PromotionTypes;
import com.mall.promotion.StackMode;
import com.mall.service.MallCouponRecordService;
import com.mall.service.MallCouponService;
import com.mall.service.MallPromotionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 促销活动服务实现（V13）。
 *
 * <p>职责边界：
 * <ul>
 *   <li>配置侧：CRUD + 上下线，作用范围「先删后插」全量覆盖（与运费模板同一套做法）；</li>
 *   <li>计算侧：把库里的活动装配成 {@link PromotionRule}，交给纯函数 {@link PromotionCalculator} 算；</li>
 *   <li>留痕侧：订单创建成功后回写 {@code mall_promotion_hit}，活动过期或未命中的订单不写。</li>
 * </ul>
 *
 * <p>不缓存活动列表：运营改完活动必须立刻生效（「配了活动用户看不到」比「多查一次库」贵得多），
 * 单次试算只有 1 次主表 + 1 次范围表查询，量级可忽略。
 */
@Slf4j
@Service
public class MallPromotionServiceImpl extends ServiceImpl<MallPromotionMapper, MallPromotion>
        implements MallPromotionService {

    private final MallPromotionScopeMapper scopeMapper;
    private final MallPromotionHitMapper hitMapper;
    private final MallCouponRecordService couponRecordService;
    private final MallCouponService couponService;
    private final JdbcTemplate jdbcTemplate;

    /** 叠加策略：best=互斥取最优（默认），stack=可叠加活动依次生效 */
    @Value("${mall.promotion.stack-mode:best}")
    private String stackModeConfig;

    public MallPromotionServiceImpl(MallPromotionScopeMapper scopeMapper,
                                    MallPromotionHitMapper hitMapper,
                                    MallCouponRecordService couponRecordService,
                                    MallCouponService couponService,
                                    JdbcTemplate jdbcTemplate) {
        this.scopeMapper = scopeMapper;
        this.hitMapper = hitMapper;
        this.couponRecordService = couponRecordService;
        this.couponService = couponService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String stackMode() {
        return StackMode.of(stackModeConfig).code();
    }

    // ===== 后台 =====

    @Override
    public Page<Map<String, Object>> pageQuery(MallPromotionPageDTO dto) {
        LambdaQueryWrapper<MallPromotion> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.like(MallPromotion::getName, dto.getKeyword().trim());
        }
        if (dto.getType() != null) {
            wrapper.eq(MallPromotion::getType, dto.getType());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(MallPromotion::getStatus, dto.getStatus());
        }
        wrapper.orderByAsc(MallPromotion::getPriority).orderByDesc(MallPromotion::getId);
        Page<MallPromotion> page = page(new Page<>(dto.getPageNum(), Math.min(dto.getPageSize(), 100)), wrapper);
        Page<Map<String, Object>> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::rowOf).toList());
        return voPage;
    }

    @Override
    public Map<String, Object> detail(Long id) {
        MallPromotion promotion = getById(id);
        if (promotion == null) {
            throw new BusinessException(400, "活动不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>(rowOf(promotion));
        PromotionRule rule = new PromotionRule();
        PromotionRuleCodec.apply(rule, promotion.getRuleJson());
        data.put("threshold", rule.getThreshold());
        data.put("discount", rule.getDiscount());
        data.put("discountRate", rule.getDiscountRate());
        data.put("bundleQty", rule.getBundleQty());
        data.put("bundlePrice", rule.getBundlePrice());
        data.put("addOnPrice", rule.getAddOnPrice());
        data.put("refIds", scopesOf(id).stream().map(MallPromotionScope::getRefId).map(String::valueOf).toList());
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePromotion(MallPromotionSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new BusinessException(400, "活动名称不能为空");
        }
        int type = dto.getType() == null ? PromotionTypes.FULL_CUT : dto.getType();
        if (type < PromotionTypes.FULL_CUT || type > PromotionTypes.ADD_ON) {
            throw new BusinessException(400, "活动类型不正确");
        }
        int scopeType = dto.getScopeType() == null ? PromotionTypes.SCOPE_ALL : dto.getScopeType();
        if (scopeType < PromotionTypes.SCOPE_ALL || scopeType > PromotionTypes.SCOPE_MERCHANT) {
            throw new BusinessException(400, "作用范围不正确");
        }
        if (scopeType != PromotionTypes.SCOPE_ALL && (dto.getRefIds() == null || dto.getRefIds().isEmpty())) {
            throw new BusinessException(400, "请选择活动作用的类目/商品/店铺");
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null
                && !dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        MallPromotion entity = dto.getId() == null ? new MallPromotion() : getById(dto.getId());
        if (entity == null) {
            throw new BusinessException(400, "活动不存在");
        }
        entity.setName(dto.getName().trim());
        entity.setType(type);
        entity.setScopeType(scopeType);
        entity.setRuleJson(ruleJsonOf(dto, type));
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setPriority(dto.getPriority() == null ? 100 : dto.getPriority());
        entity.setStackable(Integer.valueOf(1).equals(dto.getStackable()) ? 1 : 0);
        entity.setStatus(dto.getStatus() == null ? MallPromotion.STATUS_ON : dto.getStatus());
        entity.setRemark(dto.getRemark());
        saveOrUpdate(entity);

        // 作用范围全量覆盖：先删后插，避免「改了几个商品还剩旧商品」的脏数据
        scopeMapper.delete(new LambdaQueryWrapper<MallPromotionScope>()
                .eq(MallPromotionScope::getPromotionId, entity.getId()));
        if (scopeType != PromotionTypes.SCOPE_ALL && dto.getRefIds() != null) {
            Set<Long> distinct = new LinkedHashSet<>(dto.getRefIds());
            for (Long refId : distinct) {
                if (refId == null) {
                    continue;
                }
                MallPromotionScope scope = new MallPromotionScope();
                scope.setPromotionId(entity.getId());
                scope.setScopeType(scopeType);
                scope.setRefId(refId);
                scopeMapper.insert(scope);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePromotion(Long id) {
        MallPromotion promotion = getById(id);
        if (promotion == null) {
            throw new BusinessException(400, "活动不存在");
        }
        scopeMapper.delete(new LambdaQueryWrapper<MallPromotionScope>()
                .eq(MallPromotionScope::getPromotionId, id));
        removeById(id);
    }

    @Override
    public void setStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(400, "状态只能是 0（下线）或 1（上线）");
        }
        MallPromotion promotion = getById(id);
        if (promotion == null) {
            throw new BusinessException(400, "活动不存在");
        }
        update(new LambdaUpdateWrapper<MallPromotion>()
                .eq(MallPromotion::getId, id)
                .set(MallPromotion::getStatus, status));
    }

    // ===== 计算 =====

    @Override
    public PromotionCalcResult calculate(PromotionCalcRequest request) {
        List<CartLine> lines = new ArrayList<>();
        long total = 0L;
        if (request != null && request.getItems() != null) {
            for (PromotionCalcRequest.Item item : request.getItems()) {
                if (item == null) {
                    continue;
                }
                long price = item.getPrice() == null ? 0L : Math.max(item.getPrice(), 0L);
                int qty = item.getQuantity() == null ? 0 : Math.max(item.getQuantity(), 0);
                lines.add(new CartLine(item.getSkuId(), item.getGoodsId(), item.getCategoryId(),
                        item.getMerchantId(), price, qty));
                total += price * qty;
            }
        }
        StackMode mode = request != null && StringUtils.hasText(request.getStackMode())
                ? StackMode.of(request.getStackMode())
                : StackMode.of(stackModeConfig);
        PromotionResult result = PromotionCalculator.calculate(loadActiveRules(LocalDateTime.now()),
                lines, LocalDateTime.now(), mode);

        PromotionCalcResult vo = new PromotionCalcResult();
        vo.setTotalAmount(result.getTotalAmount());
        vo.setPromotionDiscount(result.getPromotionDiscount());
        vo.setPayableAmount(Math.max(result.getPayableAmount(), 0L));
        vo.setStackMode(result.getStackMode());
        vo.setTip(result.getPromotionDiscount() > 0 ? "" : "暂无可用活动");
        for (PromotionResult.LineResult lr : result.getLines()) {
            PromotionCalcResult.Line line = new PromotionCalcResult.Line();
            line.setSkuId(lr.getSkuId());
            line.setGoodsId(lr.getGoodsId());
            line.setMerchantId(lr.getMerchantId());
            line.setOriginalAmount(lr.getOriginalAmount());
            line.setDiscountAmount(lr.getDiscountAmount());
            line.setPayableAmount(lr.getPayableAmount());
            vo.getLines().add(line);
        }
        for (PromotionResult.HitResult hit : result.getHits()) {
            PromotionCalcResult.Hit voHit = new PromotionCalcResult.Hit();
            voHit.setPromotionId(hit.getPromotionId());
            voHit.setPromotionName(hit.getPromotionName());
            voHit.setType(hit.getType());
            voHit.setTypeText(hit.getTypeText());
            voHit.setRuleText(hit.getRuleText());
            voHit.setAmount(hit.getAmount());
            for (PromotionResult.HitLine hl : hit.getLineDetails()) {
                PromotionCalcResult.HitLine voLine = new PromotionCalcResult.HitLine();
                voLine.setSkuId(hl.getSkuId());
                voLine.setGoodsId(hl.getGoodsId());
                voLine.setMerchantId(hl.getMerchantId());
                voLine.setAmount(hl.getAmount());
                voHit.getLineDetails().add(voLine);
            }
            vo.getHits().add(voHit);
        }
        log.debug("促销试算：总额 {} 分，优惠 {} 分，策略 {}，命中 {} 个活动",
                vo.getTotalAmount(), vo.getPromotionDiscount(), vo.getStackMode(), vo.getHits().size());
        return vo;
    }

    @Override
    public List<Map<String, Object>> available(Long goodsId) {
        if (goodsId == null) {
            return List.of();
        }
        Long categoryId = null;
        Long merchantId = null;
        try {
            // 同库只读一列，用于类目/店铺活动的范围判定（与运费模板读 mall_merchant 同一取舍）
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT category_id, merchant_id FROM mall_goods WHERE id = ? AND deleted = 0", goodsId);
            if (!rows.isEmpty()) {
                Object c = rows.get(0).get("category_id");
                Object m = rows.get(0).get("merchant_id");
                categoryId = c == null ? null : ((Number) c).longValue();
                merchantId = m == null ? null : ((Number) m).longValue();
            }
        } catch (Exception e) {
            log.debug("读取商品类目/店铺失败（活动按全场匹配）：goodsId={}, {}", goodsId, e.getMessage());
        }
        CartLine probe = new CartLine(null, goodsId, categoryId, merchantId, 0L, 1);
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PromotionRule rule : loadActiveRules(now)) {
            if (!rule.matches(probe)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(rule.getId()));
            row.put("name", rule.getName());
            row.put("type", rule.getType());
            row.put("typeText", PromotionTypes.typeText(rule.getType()));
            row.put("ruleText", rule.ruleText());
            row.put("scopeType", rule.getScopeType());
            row.put("discountRate", rule.getDiscountRate());
            row.put("threshold", rule.getThreshold());
            row.put("discount", rule.getDiscount());
            row.put("endTime", rule.getEndTime());
            row.put("countdownSeconds", rule.getEndTime() == null ? 0L
                    : Math.max(Duration.between(now, rule.getEndTime()).getSeconds(), 0L));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public Map<String, Object> nextTier(Long memberId, long amount, Long merchantId) {
        LocalDateTime now = LocalDateTime.now();
        long gap = Long.MAX_VALUE;
        long discount = 0L;
        String name = null;
        for (PromotionRule rule : loadActiveRules(now)) {
            if (rule.getType() == null || rule.getType() != PromotionTypes.FULL_CUT) {
                continue;
            }
            if (rule.getScopeType() != null && rule.getScopeType() == PromotionTypes.SCOPE_MERCHANT
                    && merchantId != null && !rule.getMerchantIds().contains(merchantId)) {
                continue;
            }
            long threshold = rule.getThreshold() == null ? 0L : rule.getThreshold();
            if (threshold <= amount) {
                continue;
            }
            long g = threshold - amount;
            if (g < gap) {
                gap = g;
                discount = rule.getDiscount() == null ? 0L : rule.getDiscount();
                name = rule.getName();
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalAmount", amount);
        data.put("gap", gap == Long.MAX_VALUE ? 0L : gap);
        data.put("discount", discount);
        data.put("name", name);
        data.put("tip", gap == Long.MAX_VALUE || discount <= 0 ? ""
                : "再买 " + yuan(gap) + " 元可减 " + yuan(discount) + " 元");

        // 可用券：只看会员手上还没用、门槛比当前金额高的满减券，取「差得最少」的那张
        String couponTip = "";
        if (memberId != null) {
            CouponGap couponGap = nearestCoupon(memberId, amount, now);
            if (couponGap != null) {
                couponTip = "再买 " + yuan(couponGap.gap) + " 元可用「" + couponGap.name + "」减 "
                        + yuan(couponGap.amount) + " 元";
            }
        }
        data.put("couponTip", couponTip);
        return data;
    }

    @Override
    public void writeHits(PromotionHitWriteDTO dto) {
        if (dto == null || dto.getRows() == null || dto.getRows().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PromotionHitWriteDTO.Row row : dto.getRows()) {
            if (row == null || row.getPromotionId() == null || row.getOrderId() == null) {
                continue;
            }
            long amount = row.getDiscountAmount() == null ? 0L : row.getDiscountAmount();
            if (amount <= 0) {
                // 0 元命中（加价购等）不写留痕，避免对账时出现「有记录没优惠」的噪音
                continue;
            }
            MallPromotionHit hit = new MallPromotionHit();
            hit.setPromotionId(row.getPromotionId());
            hit.setPromotionName(row.getPromotionName());
            hit.setOrderId(row.getOrderId());
            hit.setMemberId(row.getMemberId());
            hit.setDiscountAmount(amount);
            hit.setHitTime(now);
            hitMapper.insert(hit);
        }
    }

    // ===== 内部辅助 =====

    /** 当前生效的活动（上线 + 时间窗内），装配成计算模型 */
    private List<PromotionRule> loadActiveRules(LocalDateTime now) {
        LambdaQueryWrapper<MallPromotion> wrapper = new LambdaQueryWrapper<MallPromotion>()
                .eq(MallPromotion::getStatus, MallPromotion.STATUS_ON)
                .and(w -> w.isNull(MallPromotion::getStartTime).or().le(MallPromotion::getStartTime, now))
                .and(w -> w.isNull(MallPromotion::getEndTime).or().ge(MallPromotion::getEndTime, now))
                .orderByAsc(MallPromotion::getPriority)
                .orderByAsc(MallPromotion::getId);
        List<MallPromotion> list = list(wrapper);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<MallPromotionScope>> scopeMap = scopesOf(list.stream().map(MallPromotion::getId).toList());
        List<PromotionRule> rules = new ArrayList<>();
        for (MallPromotion promotion : list) {
            PromotionRule rule = new PromotionRule();
            rule.setId(promotion.getId());
            rule.setName(promotion.getName());
            rule.setType(promotion.getType());
            rule.setScopeType(promotion.getScopeType());
            rule.setPriority(promotion.getPriority());
            rule.setStackable(Integer.valueOf(1).equals(promotion.getStackable()));
            rule.setStartTime(promotion.getStartTime());
            rule.setEndTime(promotion.getEndTime());
            PromotionRuleCodec.apply(rule, promotion.getRuleJson());
            for (MallPromotionScope scope : scopeMap.getOrDefault(promotion.getId(), List.of())) {
                switch (scope.getScopeType() == null ? 0 : scope.getScopeType()) {
                    case PromotionTypes.SCOPE_CATEGORY -> rule.getCategoryIds().add(scope.getRefId());
                    case PromotionTypes.SCOPE_GOODS -> rule.getGoodsIds().add(scope.getRefId());
                    case PromotionTypes.SCOPE_MERCHANT -> rule.getMerchantIds().add(scope.getRefId());
                    default -> {
                        // 全场活动不落范围明细；脏数据直接忽略
                    }
                }
            }
            rules.add(rule);
        }
        return rules;
    }

    private List<MallPromotionScope> scopesOf(Long promotionId) {
        return scopeMapper.selectList(new LambdaQueryWrapper<MallPromotionScope>()
                .eq(MallPromotionScope::getPromotionId, promotionId));
    }

    private Map<Long, List<MallPromotionScope>> scopesOf(List<Long> promotionIds) {
        if (promotionIds == null || promotionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<MallPromotionScope>> map = new LinkedHashMap<>();
        for (MallPromotionScope scope : scopeMapper.selectList(new LambdaQueryWrapper<MallPromotionScope>()
                .in(MallPromotionScope::getPromotionId, promotionIds))) {
            map.computeIfAbsent(scope.getPromotionId(), k -> new ArrayList<>()).add(scope);
        }
        return map;
    }

    /** 结构化字段优先，全空时退回 ruleJson（后台表单与脚本两条路都能用） */
    private String ruleJsonOf(MallPromotionSaveDTO dto, int type) {
        boolean structured = dto.getThreshold() != null || dto.getDiscount() != null
                || dto.getDiscountRate() != null || dto.getBundleQty() != null
                || dto.getBundlePrice() != null || dto.getAddOnPrice() != null;
        if (!structured && StringUtils.hasText(dto.getRuleJson())) {
            PromotionRule probe = new PromotionRule();
            PromotionRuleCodec.apply(probe, dto.getRuleJson());
            return PromotionRuleCodec.toJson(probe);
        }
        PromotionRule rule = new PromotionRule();
        rule.setThreshold(dto.getThreshold() == null ? 0L : Math.max(dto.getThreshold(), 0L));
        rule.setDiscount(dto.getDiscount() == null ? 0L : Math.max(dto.getDiscount(), 0L));
        rule.setDiscountRate(dto.getDiscountRate() == null
                ? defaultRate(type) : Math.max(Math.min(dto.getDiscountRate(), 100), 1));
        rule.setBundleQty(dto.getBundleQty() == null ? 0 : Math.max(dto.getBundleQty(), 0));
        rule.setBundlePrice(dto.getBundlePrice() == null ? 0L : Math.max(dto.getBundlePrice(), 0L));
        rule.setAddOnPrice(dto.getAddOnPrice() == null ? 0L : Math.max(dto.getAddOnPrice(), 0L));
        return PromotionRuleCodec.toJson(rule);
    }

    private int defaultRate(int type) {
        // 第二件半价默认 50，其余折扣类默认 90（9 折），非折扣类给 100 表示不生效
        if (type == PromotionTypes.SECOND_HALF) {
            return 50;
        }
        return type == PromotionTypes.FULL_DISCOUNT || type == PromotionTypes.LIMITED_DISCOUNT ? 90 : 100;
    }

    private Map<String, Object> rowOf(MallPromotion promotion) {
        int type = promotion.getType() == null ? PromotionTypes.FULL_CUT : promotion.getType();
        PromotionRule rule = new PromotionRule();
        rule.setType(type);
        PromotionRuleCodec.apply(rule, promotion.getRuleJson());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", String.valueOf(promotion.getId()));
        row.put("name", promotion.getName());
        row.put("type", type);
        row.put("typeText", PromotionTypes.typeText(type));
        row.put("scopeType", promotion.getScopeType());
        row.put("scopeText", PromotionTypes.scopeText(promotion.getScopeType()));
        row.put("ruleJson", promotion.getRuleJson());
        row.put("ruleText", rule.ruleText());
        row.put("startTime", promotion.getStartTime());
        row.put("endTime", promotion.getEndTime());
        row.put("priority", promotion.getPriority());
        row.put("stackable", promotion.getStackable());
        row.put("status", promotion.getStatus());
        row.put("remark", promotion.getRemark());
        row.put("createTime", promotion.getCreateTime());
        return row;
    }

    private CouponGap nearestCoupon(Long memberId, long amount, LocalDateTime now) {
        List<MallCouponRecord> records = couponRecordService.list(new LambdaQueryWrapper<MallCouponRecord>()
                .eq(MallCouponRecord::getMemberId, memberId)
                .eq(MallCouponRecord::getStatus, 1)
                .isNull(MallCouponRecord::getOrderId));
        if (records.isEmpty()) {
            return null;
        }
        List<Long> couponIds = records.stream().map(MallCouponRecord::getCouponId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (couponIds.isEmpty()) {
            return null;
        }
        CouponGap best = null;
        List<MallCoupon> coupons = couponService.list(new LambdaQueryWrapper<MallCoupon>()
                .in(MallCoupon::getId, couponIds)
                .eq(MallCoupon::getStatus, 1)
                .eq(MallCoupon::getType, 1));
        for (MallCoupon coupon : coupons) {
            if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())) {
                continue;
            }
            if (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
                continue;
            }
            long threshold = coupon.getThresholdAmount() == null ? 0L : coupon.getThresholdAmount();
            if (threshold <= amount) {
                continue;
            }
            long gap = threshold - amount;
            if (best == null || gap < best.gap) {
                best = new CouponGap(gap, coupon.getAmount() == null ? 0L : coupon.getAmount(), coupon.getName());
            }
        }
        return best;
    }

    private static String yuan(long fen) {
        return (fen % 100 == 0) ? String.valueOf(fen / 100) : String.format("%.2f", fen / 100.0);
    }

    private record CouponGap(long gap, long amount, String name) {
    }
}
