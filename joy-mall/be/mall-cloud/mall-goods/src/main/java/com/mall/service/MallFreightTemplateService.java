package com.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.feign.dto.FreightQuoteRequest;
import com.mall.common.feign.dto.FreightQuoteResult;
import com.mall.dto.FreightTemplateSaveDTO;
import com.mall.entity.MallFreightTemplate;

import java.util.List;
import java.util.Map;

/**
 * 运费模板服务（V12）：模板与地区规则维护 + 下单运费试算。
 */
public interface MallFreightTemplateService extends IService<MallFreightTemplate> {

    Page<MallFreightTemplate> pageQuery(String keyword, Integer status, long pageNum, long pageSize);

    /** 详情：模板主体 + 全部规则（按 sort 升序） */
    Map<String, Object> detail(Long id);

    /** 保存模板与规则（规则全量覆盖） */
    void saveTemplate(FreightTemplateSaveDTO dto);

    /** 删除模板（同时逻辑删除其规则）；默认模板不可删 */
    void removeTemplate(Long id);

    /** 设为全局默认：同库互斥 */
    void setDefault(Long id);

    /** 启用中的模板下拉 */
    List<Map<String, Object>> options();

    /**
     * 运费试算：模板解析顺序 商品 → 店铺默认 → 全局默认；
     * 无任何模板时返回 amount=0 且 matched=false（由调用方打 WARN，不阻断下单）。
     */
    FreightQuoteResult quote(FreightQuoteRequest request);
}
