package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.entity.MallMerchant;
import com.mall.service.MallMerchantService;
import com.mall.service.impl.MallMerchantServiceImpl;
import com.mall.vo.MerchantShopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端店铺（V10）：店铺列表与店铺信息。
 *
 * <p>店内商品由商品域 {@code GET /shop/goods?merchantId=xx} 提供，
 * 商家域不跨域拉商品数据，避免两个服务相互依赖。
 */
@Tag(name = "商城前台-店铺", description = "店铺首页信息")
@RestController
@RequestMapping("/shop/merchant")
public class ShopMerchantController {

    private final MallMerchantService merchantService;

    public ShopMerchantController(MallMerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @Operation(summary = "启用中的店铺列表（店铺导航用）")
    @GetMapping("/list")
    public Result<List<MerchantShopVO>> list() {
        List<MallMerchant> list = merchantService.list(new LambdaQueryWrapper<MallMerchant>()
                .eq(MallMerchant::getStatus, MallMerchantServiceImpl.ST_ENABLED)
                .orderByAsc(MallMerchant::getId));
        return Result.ok(list.stream().map(this::toVO).toList());
    }

    @Operation(summary = "店铺信息（停用/不存在均 400）")
    @GetMapping("/{id}")
    public Result<MerchantShopVO> detail(@PathVariable("id") Long id) {
        MallMerchant merchant = merchantService.getById(id);
        if (merchant == null) {
            throw new BusinessException(400, "店铺不存在");
        }
        if (!Integer.valueOf(MallMerchantServiceImpl.ST_ENABLED).equals(merchant.getStatus())) {
            throw new BusinessException(400, "店铺已停业");
        }
        return Result.ok(toVO(merchant));
    }

    private MerchantShopVO toVO(MallMerchant merchant) {
        MerchantShopVO vo = new MerchantShopVO();
        vo.setId(merchant.getId());
        vo.setMerchantNo(merchant.getMerchantNo());
        vo.setName(merchant.getName());
        vo.setLogo(merchant.getLogo());
        vo.setIntro(merchant.getIntro());
        vo.setCategory(merchant.getCategory());
        return vo;
    }
}
