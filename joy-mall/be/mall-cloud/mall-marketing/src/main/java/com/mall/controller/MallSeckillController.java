package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.dto.MallSeckillPageDTO;
import com.mall.dto.MallSeckillSaveDTO;
import com.mall.entity.MallSeckillActivity;
import com.mall.service.MallSeckillActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 秒杀活动管理（V8）。新增/修改时重算 availableStock：已售 = 旧总量 - 旧剩余。
 */
@Tag(name = "秒杀活动管理", description = "秒杀活动管理")
@RestController
@RequestMapping("/mall_seckill")
public class MallSeckillController {

    private final MallSeckillActivityService seckillActivityService;
    private final GoodsInnerClient goodsInnerClient;

    public MallSeckillController(MallSeckillActivityService seckillActivityService,
                                 GoodsInnerClient goodsInnerClient) {
        this.seckillActivityService = seckillActivityService;
        this.goodsInnerClient = goodsInnerClient;
    }

    @Operation(summary = "分页查询秒杀活动（含商品名）")
    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestBody MallSeckillPageDTO dto) {
        Page<MallSeckillActivity> page = new Page<>(dto.getPageNum(), Math.min(dto.getPageSize(), 100));
        LambdaQueryWrapper<MallSeckillActivity> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (dto.getStatus() != null) {
                wrapper.eq(MallSeckillActivity::getStatus, dto.getStatus());
            }
            if (dto.getGoodsId() != null) {
                wrapper.eq(MallSeckillActivity::getGoodsId, dto.getGoodsId());
            }
        }
        wrapper.orderByDesc(MallSeckillActivity::getId);
        seckillActivityService.page(page, wrapper);

        List<Long> goodsIds = page.getRecords().stream()
                .map(MallSeckillActivity::getGoodsId).distinct().toList();
        Map<Long, GoodsBrief> goodsMap = goodsIds.isEmpty() ? Map.of()
                : goodsInnerClient.goodsByIds(goodsIds).stream()
                        .collect(Collectors.toMap(GoodsBrief::getId, Function.identity()));
        Page<Map<String, Object>> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(a -> {
            Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            GoodsBrief g = goodsMap.get(a.getGoodsId());
            m.put("id", String.valueOf(a.getId()));
            m.put("goodsId", String.valueOf(a.getGoodsId()));
            m.put("skuId", String.valueOf(a.getSkuId()));
            m.put("goodsName", g == null ? null : g.getName());
            m.put("originPrice", a.getOriginPrice());
            m.put("seckillPrice", a.getSeckillPrice());
            m.put("totalStock", a.getTotalStock());
            m.put("availableStock", a.getAvailableStock());
            m.put("perLimit", a.getPerLimit());
            m.put("startTime", a.getStartTime());
            m.put("endTime", a.getEndTime());
            m.put("status", a.getStatus());
            m.put("createTime", a.getCreateTime());
            return m;
        }).toList());
        return Result.ok(voPage);
    }

    @Operation(summary = "根据ID查询秒杀活动")
    @GetMapping("/{id}")
    public Result<MallSeckillActivity> getById(@PathVariable("id") Long id) {
        return Result.ok(seckillActivityService.getById(id));
    }

    @Operation(summary = "新增秒杀活动")
    @Log(title = "秒杀活动", type = "INSERT")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody MallSeckillSaveDTO dto) {
        validate(dto);
        MallSeckillActivity entity = new MallSeckillActivity();
        BeanUtils.copyProperties(dto, entity, "id");
        entity.setAvailableStock(dto.getTotalStock());
        entity.setPerLimit(dto.getPerLimit() == null ? 1 : dto.getPerLimit());
        entity.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        seckillActivityService.save(entity);
        return Result.ok();
    }

    @Operation(summary = "修改秒杀活动")
    @Log(title = "秒杀活动", type = "UPDATE")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody MallSeckillSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        MallSeckillActivity old = seckillActivityService.getById(dto.getId());
        if (old == null) {
            throw new BusinessException(400, "活动不存在");
        }
        validate(dto);
        MallSeckillActivity entity = new MallSeckillActivity();
        BeanUtils.copyProperties(dto, entity);
        // 剩余库存跟随总量调整：新剩余 = 新总量 - 已售（已售 = 旧总量 - 旧剩余），不为负
        int sold = (old.getTotalStock() == null ? 0 : old.getTotalStock())
                - (old.getAvailableStock() == null ? 0 : old.getAvailableStock());
        entity.setAvailableStock(Math.max(dto.getTotalStock() - Math.max(sold, 0), 0));
        entity.setPerLimit(dto.getPerLimit() == null ? 1 : dto.getPerLimit());
        entity.setStatus(dto.getStatus() == null ? old.getStatus() : dto.getStatus());
        seckillActivityService.updateById(entity);
        return Result.ok();
    }

    @Operation(summary = "结束秒杀活动（条件更新，幂等）")
    @Log(title = "秒杀活动", type = "UPDATE")
    @PostMapping("/close/{id}")
    public Result<Void> close(@PathVariable("id") Long id) {
        MallSeckillActivity old = seckillActivityService.getById(id);
        if (old == null) {
            throw new BusinessException(400, "活动不存在");
        }
        MallSeckillActivity entity = new MallSeckillActivity();
        entity.setId(id);
        entity.setStatus(2);
        seckillActivityService.updateById(entity);
        return Result.ok();
    }

    @Operation(summary = "删除秒杀活动")
    @Log(title = "秒杀活动", type = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        seckillActivityService.removeById(id);
        return Result.ok();
    }

    private void validate(MallSeckillSaveDTO dto) {
        if (dto.getEndTime() == null || dto.getStartTime() == null
                || !dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        if (dto.getSeckillPrice() != null && dto.getOriginPrice() != null
                && dto.getSeckillPrice() > dto.getOriginPrice()) {
            throw new BusinessException(400, "秒杀价不能高于原价");
        }
        if (dto.getTotalStock() != null && dto.getTotalStock() < 1) {
            throw new BusinessException(400, "秒杀总库存必须大于 0");
        }
    }
}
