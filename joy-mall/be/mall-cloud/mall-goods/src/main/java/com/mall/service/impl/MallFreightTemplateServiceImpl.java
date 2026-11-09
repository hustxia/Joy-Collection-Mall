package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.dto.FreightQuoteRequest;
import com.mall.common.feign.dto.FreightQuoteResult;
import com.mall.dto.FreightTemplateSaveDTO;
import com.mall.entity.MallFreightRule;
import com.mall.entity.MallFreightTemplate;
import com.mall.entity.MallGoods;
import com.mall.entity.MallSku;
import com.mall.freight.FreightCalculator;
import com.mall.mapper.MallFreightRuleMapper;
import com.mall.mapper.MallFreightTemplateMapper;
import com.mall.mapper.MallGoodsMapper;
import com.mall.mapper.MallSkuMapper;
import com.mall.service.MallFreightTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运费模板服务实现（V12）。
 *
 * <p>模板解析顺序：商品 {@code mall_goods.freight_template_id} → 店铺 {@code mall_merchant.freight_template_id}
 * → 全局默认（{@code is_default=1}）。三级都空时按包邮处理并打 WARN —— 宁可少收运费，
 * 也不能因为配置缺失让用户下不了单（验收口径见需求文档 REQ-P0-02 第 5 条）。
 *
 * <p>规则匹配：先按 sort 升序找「region_codes 命中收货省/市」的精确规则，未命中再用
 * region_codes 为空的默认规则。同一模板内规则不叠加，命中一条即止。
 */
@Slf4j
@Service
public class MallFreightTemplateServiceImpl extends ServiceImpl<MallFreightTemplateMapper, MallFreightTemplate>
        implements MallFreightTemplateService {

    private final MallFreightRuleMapper ruleMapper;
    private final MallGoodsMapper goodsMapper;
    private final MallSkuMapper skuMapper;
    private final JdbcTemplate jdbcTemplate;

    public MallFreightTemplateServiceImpl(MallFreightRuleMapper ruleMapper, MallGoodsMapper goodsMapper,
                                         MallSkuMapper skuMapper, JdbcTemplate jdbcTemplate) {
        this.ruleMapper = ruleMapper;
        this.goodsMapper = goodsMapper;
        this.skuMapper = skuMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<MallFreightTemplate> pageQuery(String keyword, Integer status, long pageNum, long pageSize) {
        LambdaQueryWrapper<MallFreightTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(MallFreightTemplate::getName, keyword);
        }
        if (status != null) {
            wrapper.eq(MallFreightTemplate::getStatus, status);
        }
        wrapper.orderByDesc(MallFreightTemplate::getIsDefault).orderByDesc(MallFreightTemplate::getId);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Map<String, Object> detail(Long id) {
        MallFreightTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException(400, "运费模板不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("template", template);
        data.put("chargeTypeText", MallFreightTemplate.chargeTypeText(template.getChargeType()));
        data.put("rules", rulesOf(id).stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("regionCodes", r.getRegionCodes());
            row.put("firstUnit", r.getFirstUnit());
            row.put("firstFee", r.getFirstFee());
            row.put("extraUnit", r.getExtraUnit());
            row.put("extraFee", r.getExtraFee());
            row.put("freeThreshold", r.getFreeThreshold());
            row.put("sort", r.getSort());
            return row;
        }).toList());
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTemplate(FreightTemplateSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new BusinessException(400, "模板名称不能为空");
        }
        Integer chargeType = dto.getChargeType() == null ? MallFreightTemplate.CHARGE_BY_PIECE : dto.getChargeType();
        if (chargeType < 1 || chargeType > 3) {
            throw new BusinessException(400, "计费方式不正确");
        }
        if (dto.getRules() == null || dto.getRules().isEmpty()) {
            throw new BusinessException(400, "至少需要一条地区规则");
        }
        MallFreightTemplate template = dto.getId() == null ? new MallFreightTemplate() : getById(dto.getId());
        if (template == null) {
            throw new BusinessException(400, "运费模板不存在");
        }
        template.setName(dto.getName().trim());
        template.setChargeType(chargeType);
        template.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        template.setRemark(dto.getRemark());
        template.setIsDefault(Integer.valueOf(1).equals(dto.getIsDefault()) ? 1 : 0);
        if (template.getIsDefault() == 1) {
            clearOtherDefault(template.getId());
        }
        saveOrUpdate(template);

        // 规则全量覆盖：先逻辑删除旧规则再插入，避免「删一条留一条」的脏数据
        ruleMapper.delete(new LambdaQueryWrapper<MallFreightRule>()
                .eq(MallFreightRule::getTemplateId, template.getId()));
        int sort = 0;
        for (FreightTemplateSaveDTO.Rule r : dto.getRules()) {
            MallFreightRule rule = new MallFreightRule();
            rule.setTemplateId(template.getId());
            rule.setRegionCodes(StringUtils.hasText(r.getRegionCodes()) ? r.getRegionCodes().trim() : null);
            rule.setFirstUnit(r.getFirstUnit() == null ? 1 : Math.max(r.getFirstUnit(), 1));
            rule.setFirstFee(r.getFirstFee() == null ? 0L : Math.max(r.getFirstFee(), 0L));
            rule.setExtraUnit(r.getExtraUnit() == null ? 1 : Math.max(r.getExtraUnit(), 1));
            rule.setExtraFee(r.getExtraFee() == null ? 0L : Math.max(r.getExtraFee(), 0L));
            rule.setFreeThreshold(r.getFreeThreshold() == null ? 0L : Math.max(r.getFreeThreshold(), 0L));
            rule.setSort(r.getSort() == null ? sort : r.getSort());
            sort++;
            ruleMapper.insert(rule);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTemplate(Long id) {
        MallFreightTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException(400, "运费模板不存在");
        }
        if (Integer.valueOf(1).equals(template.getIsDefault())) {
            throw new BusinessException(400, "默认模板不可删除，请先将其他模板设为默认");
        }
        Long used = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_goods WHERE freight_template_id = ? AND deleted = 0", Long.class, id);
        if (used != null && used > 0) {
            throw new BusinessException(400, "该模板已被 " + used + " 个商品使用，请先解绑");
        }
        ruleMapper.delete(new LambdaQueryWrapper<MallFreightRule>().eq(MallFreightRule::getTemplateId, id));
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        MallFreightTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException(400, "运费模板不存在");
        }
        if (!Integer.valueOf(1).equals(template.getStatus())) {
            throw new BusinessException(400, "停用的模板不能设为默认");
        }
        clearOtherDefault(id);
        update(new LambdaUpdateWrapper<MallFreightTemplate>()
                .eq(MallFreightTemplate::getId, id)
                .set(MallFreightTemplate::getIsDefault, 1));
    }

    private void clearOtherDefault(Long keepId) {
        update(new LambdaUpdateWrapper<MallFreightTemplate>()
                .set(MallFreightTemplate::getIsDefault, 0)
                .eq(MallFreightTemplate::getIsDefault, 1)
                .ne(keepId != null, MallFreightTemplate::getId, keepId));
    }

    @Override
    public List<Map<String, Object>> options() {
        List<MallFreightTemplate> list = list(new LambdaQueryWrapper<MallFreightTemplate>()
                .eq(MallFreightTemplate::getStatus, 1)
                .orderByDesc(MallFreightTemplate::getIsDefault)
                .orderByAsc(MallFreightTemplate::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MallFreightTemplate t : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(t.getId()));
            row.put("name", t.getName());
            row.put("chargeType", t.getChargeType());
            row.put("isDefault", t.getIsDefault());
            rows.add(row);
        }
        return rows;
    }

    @Override
    public FreightQuoteResult quote(FreightQuoteRequest request) {
        FreightQuoteResult result = new FreightQuoteResult();
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            result.setMatched(false);
            result.setReason("无计费明细");
            return result;
        }
        long goodsAmount = request.getGoodsAmount() == null ? 0L : request.getGoodsAmount();
        MallFreightTemplate template = resolveTemplate(request);
        if (template == null) {
            result.setMatched(false);
            result.setReason("未配置运费模板，按包邮处理");
            log.warn("运费试算未命中任何模板，按包邮处理：merchantId={}, goodsAmount={}",
                    request.getMerchantId(), goodsAmount);
            return result;
        }
        List<MallFreightRule> rules = rulesOf(template.getId());
        MallFreightRule rule = matchRule(rules, request.getProvince(), request.getCity());
        if (rule == null) {
            result.setMatched(false);
            result.setReason("模板「" + template.getName() + "」无可用规则，按包邮处理");
            log.warn("运费模板 {} 没有可用规则，按包邮处理", template.getId());
            result.setTemplateId(template.getId());
            result.setTemplateName(template.getName());
            return result;
        }
        long units = unitsOf(template.getChargeType(), request.getItems());
        long amount = FreightCalculator.calc(template.getChargeType(),
                rule.getFirstUnit() == null ? 1 : rule.getFirstUnit(),
                rule.getFirstFee() == null ? 0L : rule.getFirstFee(),
                rule.getExtraUnit() == null ? 1 : rule.getExtraUnit(),
                rule.getExtraFee() == null ? 0L : rule.getExtraFee(),
                rule.getFreeThreshold() == null ? 0L : rule.getFreeThreshold(),
                units, goodsAmount);
        long gap = FreightCalculator.freeGap(rule.getFreeThreshold() == null ? 0L : rule.getFreeThreshold(),
                goodsAmount);
        result.setAmount(amount);
        result.setFreeGap(gap);
        result.setTemplateId(template.getId());
        result.setTemplateName(template.getName());
        result.setMatched(true);
        result.setTip(tipOf(rule.getFreeThreshold() == null ? 0L : rule.getFreeThreshold(), amount, gap));
        return result;
    }

    /** 模板解析：商品 → 店铺默认 → 全局默认 */
    private MallFreightTemplate resolveTemplate(FreightQuoteRequest request) {
        Set<Long> goodsIds = new LinkedHashSet<>();
        for (FreightQuoteRequest.Item item : request.getItems()) {
            if (item.getGoodsId() != null) {
                goodsIds.add(item.getGoodsId());
            }
        }
        for (Long goodsId : goodsIds) {
            MallGoods goods = goodsMapper.selectById(goodsId);
            if (goods != null && goods.getFreightTemplateId() != null) {
                MallFreightTemplate t = usableTemplate(goods.getFreightTemplateId());
                if (t != null) {
                    return t;
                }
            }
        }
        Long merchantTemplateId = merchantDefaultTemplateId(request.getMerchantId());
        if (merchantTemplateId != null) {
            MallFreightTemplate t = usableTemplate(merchantTemplateId);
            if (t != null) {
                return t;
            }
        }
        return getOne(new LambdaQueryWrapper<MallFreightTemplate>()
                .eq(MallFreightTemplate::getIsDefault, 1)
                .eq(MallFreightTemplate::getStatus, 1)
                .orderByAsc(MallFreightTemplate::getId)
                .last("LIMIT 1"), false);
    }

    private MallFreightTemplate usableTemplate(Long id) {
        MallFreightTemplate t = getById(id);
        return t != null && Integer.valueOf(1).equals(t.getStatus()) ? t : null;
    }

    /**
     * 店铺默认模板：mall_merchant 属于商家域，这里只读一列做运费回落（同库只读，不写、不 join）。
     * 查询失败（商家域表缺失等）静默回落全局默认，不影响下单。
     */
    private Long merchantDefaultTemplateId(Long merchantId) {
        if (merchantId == null) {
            return null;
        }
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT freight_template_id FROM mall_merchant WHERE id = ? AND deleted = 0",
                    Long.class, merchantId);
            return ids.isEmpty() ? null : ids.get(0);
        } catch (Exception e) {
            log.debug("读取店铺默认运费模板失败（回落全局默认）：merchantId={}, {}", merchantId, e.getMessage());
            return null;
        }
    }

    private List<MallFreightRule> rulesOf(Long templateId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<MallFreightRule>()
                .eq(MallFreightRule::getTemplateId, templateId)
                .orderByAsc(MallFreightRule::getSort)
                .orderByAsc(MallFreightRule::getId));
    }

    /** 精确地区优先，其次默认规则（region_codes 为空） */
    private MallFreightRule matchRule(List<MallFreightRule> rules, String province, String city) {
        MallFreightRule fallback = null;
        for (MallFreightRule rule : rules) {
            String regions = rule.getRegionCodes();
            if (!StringUtils.hasText(regions)) {
                if (fallback == null) {
                    fallback = rule;
                }
                continue;
            }
            for (String region : regions.split(",")) {
                String rc = region.trim();
                if (rc.isEmpty()) {
                    continue;
                }
                if (rc.equals(province) || rc.equals(city)) {
                    return rule;
                }
            }
        }
        return fallback;
    }

    /** 计费量：按件=件数，按重量=克数（重量未维护按 0 克计），满额包邮不看量 */
    private long unitsOf(Integer chargeType, List<FreightQuoteRequest.Item> items) {
        if (chargeType != null && chargeType == MallFreightTemplate.CHARGE_BY_WEIGHT) {
            List<Long> skuIds = items.stream().map(FreightQuoteRequest.Item::getSkuId)
                    .filter(java.util.Objects::nonNull).toList();
            Map<Long, Integer> weights = new LinkedHashMap<>();
            if (!skuIds.isEmpty()) {
                for (MallSku sku : skuMapper.selectBatchIds(skuIds)) {
                    weights.put(sku.getId(), sku.getWeight() == null ? 0 : sku.getWeight());
                }
            }
            long grams = 0L;
            for (FreightQuoteRequest.Item item : items) {
                int qty = item.getQuantity() == null ? 0 : item.getQuantity();
                grams += (long) weights.getOrDefault(item.getSkuId(), 0) * qty;
            }
            return grams;
        }
        long pieces = 0L;
        for (FreightQuoteRequest.Item item : items) {
            pieces += item.getQuantity() == null ? 0 : item.getQuantity();
        }
        return pieces;
    }

    private String tipOf(long freeThreshold, long amount, long gap) {
        if (freeThreshold <= 0) {
            return "";
        }
        String threshold = FreightCalculator.yuan(freeThreshold);
        if (gap <= 0) {
            return "已满 " + threshold + " 免运费";
        }
        if (amount <= 0) {
            return "满 " + threshold + " 免运费，还差 " + FreightCalculator.yuan(gap);
        }
        return "满 " + threshold + " 免运费，还差 " + FreightCalculator.yuan(gap) + "（本单运费 "
                + FreightCalculator.yuan(amount) + "）";
    }
}
