package com.mall.controller.inner;

import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.SkuBrief;
import com.mall.entity.MallGoods;
import com.mall.entity.MallSku;
import com.mall.service.MallGoodsService;
import com.mall.service.MallSkuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 商品域内部端点（/inner/**，免鉴权）：供订单域跨服务读取 SKU/商品、扣减/回补库存、累加销量。
 *
 * <p>内部 RPC 直接返回原始载荷（不套 Result 壳），与 Feign 接口签名严格一致。
 */
@RestController
@RequestMapping("/inner")
public class InnerGoodsController {

    private final MallSkuService mallSkuService;
    private final MallGoodsService mallGoodsService;
    private final com.mall.service.MallFreightTemplateService freightTemplateService;

    public InnerGoodsController(MallSkuService mallSkuService, MallGoodsService mallGoodsService,
                                com.mall.service.MallFreightTemplateService freightTemplateService) {
        this.mallSkuService = mallSkuService;
        this.mallGoodsService = mallGoodsService;
        this.freightTemplateService = freightTemplateService;
    }

    /**
     * 运费试算（V12）：订单域按店铺一次调用，回填金额与包邮提示。
     * <p>无模板不报错，返回 matched=false + amount=0，由订单域决定是否提示。
     */
    @PostMapping("/freight/quote")
    public com.mall.common.feign.dto.FreightQuoteResult freightQuote(
            @RequestBody com.mall.common.feign.dto.FreightQuoteRequest request) {
        return freightTemplateService.quote(request);
    }

    @GetMapping("/sku/by-ids")
    public List<SkuBrief> skuByIds(@RequestParam("ids") List<Long> ids) {
        return mallSkuService.listByIds(ids).stream().map(this::toSkuBrief).toList();
    }

    @GetMapping("/goods/by-ids")
    public List<GoodsBrief> goodsByIds(@RequestParam("ids") List<Long> ids) {
        return mallGoodsService.listByIds(ids).stream().map(this::toGoodsBrief).toList();
    }

    /** 按商品查 SKU 列表（V8 抽奖实物奖品建单时解析默认 SKU 用） */
    @GetMapping("/sku/by-goods")
    public List<SkuBrief> skuByGoods(@RequestParam("goodsId") Long goodsId) {
        return mallSkuService.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
                <com.mall.entity.MallSku>().eq(com.mall.entity.MallSku::getGoodsId, goodsId))
                .stream().map(this::toSkuBrief).toList();
    }

    @PostMapping("/sku/deduct")
    public Boolean deductStock(@RequestBody Map<Long, Integer> deltas) {
        return mallSkuService.deductStock(deltas);
    }

    @PostMapping("/sku/restore")
    public void restoreStock(@RequestBody Map<Long, Integer> deltas) {
        mallSkuService.restoreStock(deltas);
    }

    @PostMapping("/sku/sales")
    public void incrementSkuSales(@RequestBody Map<Long, Integer> deltas) {
        mallSkuService.incrementSales(deltas);
    }

    @PostMapping("/goods/sales")
    public void incrementGoodsSales(@RequestBody Map<Long, Integer> deltas) {
        mallGoodsService.incrementSales(deltas);
    }

    private SkuBrief toSkuBrief(MallSku s) {
        SkuBrief b = new SkuBrief();
        b.setId(s.getId());
        b.setGoodsId(s.getGoodsId());
        b.setSkuCode(s.getSkuCode());
        b.setSpecJson(s.getSpecJson());
        b.setPrice(s.getPrice());
        b.setStock(s.getStock());
        b.setImage(s.getImage());
        b.setStatus(s.getStatus());
        return b;
    }

    private GoodsBrief toGoodsBrief(MallGoods g) {
        GoodsBrief b = new GoodsBrief();
        b.setId(g.getId());
        b.setName(g.getName());
        b.setMainImage(g.getMainImage());
        b.setPriceMin(g.getPriceMin());
        b.setPriceMax(g.getPriceMax());
        b.setStatus(g.getStatus());
        b.setMerchantId(g.getMerchantId());
        // V13：类目级促销活动需要类目归属，随商品快照一起下发
        b.setCategoryId(g.getCategoryId());
        return b;
    }
}
