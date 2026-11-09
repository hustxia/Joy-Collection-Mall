package com.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.feign.dto.PromotionCalcRequest;
import com.mall.common.feign.dto.PromotionCalcResult;
import com.mall.common.feign.dto.PromotionHitWriteDTO;
import com.mall.dto.MallPromotionPageDTO;
import com.mall.dto.MallPromotionSaveDTO;
import com.mall.entity.MallPromotion;

import java.util.List;
import java.util.Map;

/**
 * 促销活动服务（V13）：后台 CRUD + 上下线 + 统一试算 + 命中留痕 + 凑单提示。
 */
public interface MallPromotionService extends IService<MallPromotion> {

    /** 后台分页（带类型/范围文案，前端不再自己翻译字典） */
    Page<Map<String, Object>> pageQuery(MallPromotionPageDTO dto);

    /** 活动详情（含作用范围明细与结构化规则，供编辑弹窗回显） */
    Map<String, Object> detail(Long id);

    /** 新增/修改（作用范围全量覆盖） */
    void savePromotion(MallPromotionSaveDTO dto);

    /** 删除（连同范围明细与命中留痕不动：留痕是对账数据，活动删了也要保留） */
    void removePromotion(Long id);

    /** 上下线 */
    void setStatus(Long id, Integer status);

    /** 统一试算：C 端试算、订单确认页、下单提交、后台预览共用 */
    PromotionCalcResult calculate(PromotionCalcRequest request);

    /** 商品可用活动（含倒计时秒数），商品卡与详情页用 */
    List<Map<String, Object>> available(Long goodsId);

    /** 凑单提示：再买 X 元可减 Y + 可用券 */
    Map<String, Object> nextTier(Long memberId, long amount, Long merchantId);

    /** 命中留痕落库（订单创建成功后调用） */
    void writeHits(PromotionHitWriteDTO dto);

    /** 当前生效的叠加策略 best/stack */
    String stackMode();
}
