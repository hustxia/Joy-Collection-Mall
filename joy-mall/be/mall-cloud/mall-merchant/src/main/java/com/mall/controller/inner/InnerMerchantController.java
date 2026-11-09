package com.mall.controller.inner;

import com.mall.common.feign.dto.MerchantBrief;
import com.mall.entity.MallMerchant;
import com.mall.service.MallMerchantBillDetailService;
import com.mall.service.MallMerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家域内部端点（/inner/**，免鉴权）：供订单域结算、售后域冲减、商品域展示店铺信息。
 *
 * <p>内部 RPC 直接返回原始载荷（不套 Result 壳），与 Feign 接口签名严格一致。
 * <p>两个写端点均幂等：重复事件返回 false，不会重复动账。
 */
@Slf4j
@Tag(name = "商家域内部接口", description = "结算/冲减/店铺信息")
@RestController
@RequestMapping("/inner/merchant")
public class InnerMerchantController {

    private final MallMerchantService merchantService;
    private final MallMerchantBillDetailService billDetailService;

    public InnerMerchantController(MallMerchantService merchantService,
                                   MallMerchantBillDetailService billDetailService) {
        this.merchantService = merchantService;
        this.billDetailService = billDetailService;
    }

    @Operation(summary = "店铺信息（不存在返回 null，不抛错，展示侧可降级）")
    @GetMapping("/info/{id}")
    public MerchantBrief info(@PathVariable("id") Long id) {
        MallMerchant merchant = merchantService.getById(id);
        if (merchant == null) {
            return null;
        }
        MerchantBrief brief = new MerchantBrief();
        brief.setId(merchant.getId());
        brief.setMerchantNo(merchant.getMerchantNo());
        brief.setName(merchant.getName());
        brief.setLogo(merchant.getLogo());
        brief.setIntro(merchant.getIntro());
        brief.setCategory(merchant.getCategory());
        brief.setStatus(merchant.getStatus());
        return brief;
    }

    @Operation(summary = "订单完成结算入账（幂等，重复事件返回 false）")
    @PostMapping("/settle")
    public Boolean settle(@RequestParam("orderId") Long orderId,
                          @RequestParam("merchantId") Long merchantId,
                          @RequestParam(value = "orderNo", required = false) String orderNo,
                          @RequestParam("payAmount") Long payAmount) {
        return billDetailService.settleOrder(orderId, merchantId, orderNo, payAmount);
    }

    @Operation(summary = "退款冲减（按 refundId 幂等）")
    @PostMapping("/refund-adjust")
    public Boolean refundAdjust(@RequestParam("refundId") Long refundId,
                                @RequestParam("orderId") Long orderId,
                                @RequestParam("merchantId") Long merchantId,
                                @RequestParam("refundAmount") Long refundAmount) {
        return billDetailService.refundAdjust(refundId, orderId, merchantId, refundAmount);
    }
}
