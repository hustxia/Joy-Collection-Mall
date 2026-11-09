package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MarketingInnerClient;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.SkuBrief;
import com.mall.common.result.Result;
import com.mall.entity.MallCart;
import com.mall.mapper.MallCartMapper;
import com.mall.service.MallCartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C 端购物车（V3）：服务端持久化，memberId 只从 UserContext 取。
 * 列表联查出商品名/图/价/实时库存（商品信息经商品域 /inner 接口获取），下架行照返由前端置灰。
 */
@Tag(name = "商城前台-购物车", description = "购物车增删改查")
@RestController
@RequestMapping("/shop/cart")
public class ShopCartController {

    private final MallCartService mallCartService;
    private final MallCartMapper mallCartMapper;
    private final GoodsInnerClient goodsInnerClient;
    private final MarketingInnerClient marketingInnerClient;

    public ShopCartController(MallCartService mallCartService, MallCartMapper mallCartMapper,
                              GoodsInnerClient goodsInnerClient, MarketingInnerClient marketingInnerClient) {
        this.mallCartService = mallCartService;
        this.mallCartMapper = mallCartMapper;
        this.goodsInnerClient = goodsInnerClient;
        this.marketingInnerClient = marketingInnerClient;
    }

    @Operation(summary = "购物车列表（联表出商品/SKU 快照+实时状态）")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(rowsOf(cartsOf(UserContext.getUserId())));
    }

    /**
     * V13 购物车分区：有效商品 / 失效商品（下架 1 / 售罄 2 分开标注）。
     *
     * <p>为什么不改 /list 的返回结构：H5 与 PC 两端的购物车页面都在用 {@code List}，
     * 改成对象会让旧前端直接白屏。新页面用 /sections，老页面继续用 /list，两边并存一个版本再收敛。
     */
    @Operation(summary = "购物车分区（validItems + invalidItems，失效原因分开）")
    @GetMapping("/sections")
    public Result<Map<String, Object>> sections() {
        List<Map<String, Object>> rows = rowsOf(cartsOf(UserContext.getUserId()));
        List<Map<String, Object>> valid = new ArrayList<>();
        List<Map<String, Object>> invalid = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int reason = invalidReasonOf(row);
            if (reason == 0) {
                valid.add(row);
                continue;
            }
            row.put("invalidReason", reason);
            row.put("invalidReasonText", reason == INVALID_SOLD_OUT ? "已售罄" : "已下架");
            invalid.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("validItems", valid);
        data.put("invalidItems", invalid);
        data.put("offShelfCount", invalid.stream()
                .filter(r -> Integer.valueOf(INVALID_OFF_SHELF).equals(r.get("invalidReason"))).count());
        data.put("soldOutCount", invalid.stream()
                .filter(r -> Integer.valueOf(INVALID_SOLD_OUT).equals(r.get("invalidReason"))).count());
        data.put("checkedAmount", checkedAmountOf(valid));
        return Result.ok(data);
    }

    /**
     * V13 凑单提示：已勾选金额离下一档满减还差多少、有没有能用的券。
     *
     * <p>活动与券的数据都在营销域，会员域只做「取已勾选金额 → 问营销域 → 原样回传」，
     * 避免把营销规则复制一份到会员域（两份规则一定会走偏）。
     */
    @Operation(summary = "凑单提示（再买 X 元可减 Y / 可用券 Z）")
    @GetMapping("/promotion-tip")
    public Result<Map<String, Object>> promotionTip() {
        Long memberId = UserContext.getUserId();
        List<Map<String, Object>> rows = rowsOf(cartsOf(memberId));
        long amount = checkedAmountOf(rows.stream().filter(r -> invalidReasonOf(r) == 0).toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalAmount", amount);
        data.put("gap", 0L);
        data.put("discount", 0L);
        data.put("tip", "");
        data.put("couponTip", "");
        try {
            Map<String, Object> tip = marketingInnerClient.nextTier(memberId, amount, null);
            if (tip != null) {
                data.putAll(tip);
            }
        } catch (Exception e) {
            // 营销域不可用：凑单提示留空，不阻断购物车
            data.put("tip", "");
        }
        return Result.ok(data);
    }

    /** 失效原因：0 有效 / 1 下架（含规格下架）/ 2 售罄 */
    private static final int INVALID_OFF_SHELF = 1;
    private static final int INVALID_SOLD_OUT = 2;

    private int invalidReasonOf(Map<String, Object> row) {
        Object goodsStatus = row.get("goodsStatus");
        Object skuStatus = row.get("skuStatus");
        Object stock = row.get("stock");
        if (goodsStatus == null || !Integer.valueOf(1).equals(goodsStatus)
                || skuStatus == null || !Integer.valueOf(1).equals(skuStatus)) {
            return INVALID_OFF_SHELF;
        }
        if (stock == null || ((Number) stock).intValue() <= 0) {
            return INVALID_SOLD_OUT;
        }
        return 0;
    }

    private long checkedAmountOf(List<Map<String, Object>> rows) {
        long amount = 0L;
        for (Map<String, Object> row : rows) {
            if (!Integer.valueOf(1).equals(row.get("checked"))) {
                continue;
            }
            Object price = row.get("price");
            Object num = row.get("num");
            if (price instanceof Number p && num instanceof Number n) {
                amount += p.longValue() * n.longValue();
            }
        }
        return amount;
    }

    private List<MallCart> cartsOf(Long memberId) {
        LambdaQueryWrapper<MallCart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallCart::getMemberId, memberId).orderByDesc(MallCart::getId);
        return mallCartService.list(wrapper);
    }

    private List<Map<String, Object>> rowsOf(List<MallCart> carts) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (carts.isEmpty()) {
            return rows;
        }
        List<Long> skuIds = carts.stream().map(MallCart::getSkuId).toList();
        Map<Long, SkuBrief> skuMap = goodsInnerClient.skuByIds(skuIds).stream()
                .collect(Collectors.toMap(SkuBrief::getId, Function.identity()));
        List<Long> goodsIds = skuMap.values().stream().map(SkuBrief::getGoodsId).distinct().toList();
        Map<Long, GoodsBrief> goodsMap = goodsIds.isEmpty() ? Map.of()
                : goodsInnerClient.goodsByIds(goodsIds).stream()
                        .collect(Collectors.toMap(GoodsBrief::getId, Function.identity()));
        for (MallCart cart : carts) {
            SkuBrief sku = skuMap.get(cart.getSkuId());
            GoodsBrief goods = sku == null ? null : goodsMap.get(sku.getGoodsId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(cart.getId()));
            row.put("skuId", String.valueOf(cart.getSkuId()));
            row.put("goodsId", sku == null ? null : String.valueOf(sku.getGoodsId()));
            row.put("goodsName", goods == null ? null : goods.getName());
            row.put("skuImage", sku != null && sku.getImage() != null ? sku.getImage()
                    : goods == null ? null : goods.getMainImage());
            row.put("specJson", sku == null ? null : sku.getSpecJson());
            row.put("price", sku == null ? null : sku.getPrice());
            row.put("stock", sku == null ? 0 : sku.getStock());
            row.put("num", cart.getNum());
            row.put("checked", cart.getChecked());
            row.put("goodsStatus", goods == null ? 0 : goods.getStatus());
            row.put("skuStatus", sku == null ? 0 : sku.getStatus());
            rows.add(row);
        }
        return rows;
    }

    @Operation(summary = "加购：同 SKU 已存在则数量累加，累加后超库存 400")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody AddDTO dto) {
        if (dto == null || dto.getSkuId() == null || dto.getNum() == null || dto.getNum() < 1) {
            throw new BusinessException(400, "参数错误：skuId/num 必填且 num≥1");
        }
        Long memberId = UserContext.getUserId();
        SkuBrief sku = firstSku(dto.getSkuId());
        if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
            throw new BusinessException(400, "商品规格不存在或已下架");
        }
        GoodsBrief goods = firstGoods(sku.getGoodsId());
        if (goods == null || !Integer.valueOf(1).equals(goods.getStatus())) {
            throw new BusinessException(400, "商品已下架");
        }
        LambdaQueryWrapper<MallCart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallCart::getMemberId, memberId).eq(MallCart::getSkuId, dto.getSkuId());
        MallCart exist = mallCartService.getOne(wrapper);
        int newNum = dto.getNum() + (exist == null ? 0 : exist.getNum());
        if (sku.getStock() != null && newNum > sku.getStock()) {
            throw new BusinessException(400, "库存不足，当前可购 " + sku.getStock() + " 件");
        }
        if (exist != null) {
            exist.setNum(newNum);
            mallCartService.updateById(exist);
        } else {
            MallCart cart = new MallCart();
            cart.setMemberId(memberId);
            cart.setSkuId(dto.getSkuId());
            cart.setNum(dto.getNum());
            cart.setChecked(1);
            mallCartService.save(cart);
        }
        return Result.ok();
    }

    @Operation(summary = "改数量/勾选")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody UpdateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        MallCart cart = ownCart(dto.getId());
        if (dto.getNum() != null) {
            if (dto.getNum() < 1) {
                throw new BusinessException(400, "数量至少为 1");
            }
            SkuBrief sku = firstSku(cart.getSkuId());
            if (sku != null && sku.getStock() != null && dto.getNum() > sku.getStock()) {
                throw new BusinessException(400, "库存不足，当前可购 " + sku.getStock() + " 件");
            }
            cart.setNum(dto.getNum());
        }
        if (dto.getChecked() != null) {
            cart.setChecked(dto.getChecked() == 1 ? 1 : 0);
        }
        mallCartService.updateById(cart);
        return Result.ok();
    }

    @Operation(summary = "删除购物车行")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        ownCart(id);
        // 物理删除：mall_cart 的 uk_member_sku 含 deleted 列，软删第二次删同一 SKU 会撞唯一键 500
        mallCartMapper.hardDelete(id, UserContext.getUserId());
        return Result.ok();
    }

    private SkuBrief firstSku(Long skuId) {
        List<SkuBrief> list = goodsInnerClient.skuByIds(List.of(skuId));
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private GoodsBrief firstGoods(Long goodsId) {
        if (goodsId == null) {
            return null;
        }
        List<GoodsBrief> list = goodsInnerClient.goodsByIds(List.of(goodsId));
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private MallCart ownCart(Long id) {
        MallCart cart = mallCartService.getById(id);
        if (cart == null) {
            throw new BusinessException(400, "购物车记录不存在");
        }
        if (!cart.getMemberId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权操作他人购物车");
        }
        return cart;
    }

    @Data
    public static class AddDTO {
        private Long skuId;
        private Integer num;
    }

    @Data
    public static class UpdateDTO {
        private Long id;
        private Integer num;
        private Integer checked;
    }
}
