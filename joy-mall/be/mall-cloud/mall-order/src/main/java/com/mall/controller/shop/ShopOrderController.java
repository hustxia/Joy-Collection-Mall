package com.mall.controller.shop;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.common.context.UserContext;
import com.mall.dto.ShopConfirmDTO;
import com.mall.dto.ShopOrderIdDTO;
import com.mall.dto.ShopSubmitDTO;
import com.mall.service.ShopOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端订单接口（V3 交易闭环）。
 *
 * <p>鉴权：网关 C 端路由（/shop/order/**）强制登录，memberId 取自 JWT。
 * 业务规则（状态机、拆单、扣库存、优惠券、售后回填）见 {@link ShopOrderService} 与其实现类，
 * 本类只做参数绑定、登录态透传与 Result 包装。
 *
 * <p>安全设计：cancel/confirmReceipt 先 ownOrder 校验属主，再条件 UPDATE（WHERE status=前置态），
 * 防越权 + 防并发重复操作；金额全部 long 分，无浮点误差。
 */
@Tag(name = "C端订单接口", description = "确认预览、提交、我的订单、详情、取消、确认收货")
@RestController
@RequestMapping("/shop/order")
public class ShopOrderController {

    private final ShopOrderService shopOrderService;

    public ShopOrderController(ShopOrderService shopOrderService) {
        this.shopOrderService = shopOrderService;
    }

    @Operation(summary = "确认页预览（不落库）：重算价格/库存/运费 + 可用券 + 按店铺分组")
    @PostMapping("/confirm")
    public Result<Map<String, Object>> confirm(@RequestBody(required = false) ShopConfirmDTO dto) {
        return Result.ok(shopOrderService.confirm(UserContext.getUserId(), dto));
    }

    @Operation(summary = "提交订单：按商家拆单→校验→条件扣库存→写单+明细+log→清购物车（事务）")
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody ShopSubmitDTO dto) {
        return Result.ok(shopOrderService.submit(UserContext.getUserId(), dto));
    }

    @Operation(summary = "我的订单分页（status 可选：10/20/30/40）")
    @GetMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "1") long pageNum,
                                                  @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(shopOrderService.page(UserContext.getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "订单详情：订单+明细+状态时间线+最新售后单")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.ok(shopOrderService.detail(UserContext.getUserId(), id));
    }

    @Operation(summary = "取消订单：仅待付款，回补库存+解锁券")
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestBody ShopOrderIdDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            throw new BusinessException(400, "参数错误：orderId 必填");
        }
        shopOrderService.cancel(UserContext.getUserId(), dto.getOrderId());
        return Result.ok();
    }

    @Operation(summary = "确认收货：仅已发货 → 已完成，触发商家结算与完成通知")
    @PostMapping("/confirm-receipt")
    public Result<Void> confirmReceipt(@RequestBody ShopOrderIdDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            throw new BusinessException(400, "参数错误：orderId 必填");
        }
        shopOrderService.confirmReceipt(UserContext.getUserId(), dto.getOrderId());
        return Result.ok();
    }
}
