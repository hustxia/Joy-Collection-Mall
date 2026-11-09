package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.dto.MallLotteryPageDTO;
import com.mall.dto.MallLotteryPrizeSaveDTO;
import com.mall.dto.MallLotterySaveDTO;
import com.mall.entity.MallLotteryActivity;
import com.mall.entity.MallLotteryPrize;
import com.mall.service.MallLotteryActivityService;
import com.mall.service.MallLotteryPrizeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 抽奖活动管理（V8）。奖品池整池保存走 /mall_lottery/prize-save。
 */
@Tag(name = "抽奖活动管理", description = "抽奖活动管理")
@RestController
@RequestMapping("/mall_lottery")
public class MallLotteryController {

    private final MallLotteryActivityService lotteryActivityService;
    private final MallLotteryPrizeService lotteryPrizeService;

    public MallLotteryController(MallLotteryActivityService lotteryActivityService,
                                 MallLotteryPrizeService lotteryPrizeService) {
        this.lotteryActivityService = lotteryActivityService;
        this.lotteryPrizeService = lotteryPrizeService;
    }

    @Operation(summary = "分页查询抽奖活动（含奖品数）")
    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestBody MallLotteryPageDTO dto) {
        Page<MallLotteryActivity> page = new Page<>(dto.getPageNum(), Math.min(dto.getPageSize(), 100));
        LambdaQueryWrapper<MallLotteryActivity> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (dto.getStatus() != null) {
                wrapper.eq(MallLotteryActivity::getStatus, dto.getStatus());
            }
            if (dto.getName() != null && !dto.getName().isBlank()) {
                wrapper.like(MallLotteryActivity::getName, dto.getName());
            }
        }
        wrapper.orderByDesc(MallLotteryActivity::getId);
        lotteryActivityService.page(page, wrapper);

        List<Long> activityIds = page.getRecords().stream().map(MallLotteryActivity::getId).toList();
        Map<Long, Long> prizeCountMap = activityIds.isEmpty() ? Map.of()
                : lotteryPrizeService.list(new LambdaQueryWrapper<MallLotteryPrize>()
                        .in(MallLotteryPrize::getActivityId, activityIds))
                .stream().collect(Collectors.groupingBy(MallLotteryPrize::getActivityId, Collectors.counting()));

        Page<Map<String, Object>> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", String.valueOf(a.getId()));
            m.put("name", a.getName());
            m.put("dailyLimit", a.getDailyLimit());
            m.put("consumePoints", a.getConsumePoints());
            m.put("startTime", a.getStartTime());
            m.put("endTime", a.getEndTime());
            m.put("status", a.getStatus());
            m.put("prizeCount", prizeCountMap.getOrDefault(a.getId(), 0L));
            m.put("createTime", a.getCreateTime());
            return m;
        }).toList());
        return Result.ok(voPage);
    }

    @Operation(summary = "根据ID查询抽奖活动（含奖品池）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@PathVariable("id") Long id) {
        MallLotteryActivity activity = lotteryActivityService.getById(id);
        if (activity == null) {
            throw new BusinessException(400, "活动不存在");
        }
        LambdaQueryWrapper<MallLotteryPrize> prizeWrapper = new LambdaQueryWrapper<>();
        prizeWrapper.eq(MallLotteryPrize::getActivityId, id)
                .orderByAsc(MallLotteryPrize::getSort).orderByAsc(MallLotteryPrize::getId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("activity", activity);
        data.put("prizes", lotteryPrizeService.list(prizeWrapper));
        return Result.ok(data);
    }

    @Operation(summary = "新增抽奖活动")
    @Log(title = "抽奖活动", type = "INSERT")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody MallLotterySaveDTO dto) {
        validate(dto);
        MallLotteryActivity entity = new MallLotteryActivity();
        BeanUtils.copyProperties(dto, entity, "id");
        entity.setConsumePoints(dto.getConsumePoints() == null ? 0 : dto.getConsumePoints());
        entity.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        lotteryActivityService.save(entity);
        return Result.ok();
    }

    @Operation(summary = "修改抽奖活动")
    @Log(title = "抽奖活动", type = "UPDATE")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody MallLotterySaveDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        MallLotteryActivity old = lotteryActivityService.getById(dto.getId());
        if (old == null) {
            throw new BusinessException(400, "活动不存在");
        }
        validate(dto);
        MallLotteryActivity entity = new MallLotteryActivity();
        BeanUtils.copyProperties(dto, entity);
        entity.setConsumePoints(dto.getConsumePoints() == null ? 0 : dto.getConsumePoints());
        entity.setStatus(dto.getStatus() == null ? old.getStatus() : dto.getStatus());
        lotteryActivityService.updateById(entity);
        return Result.ok();
    }

    @Operation(summary = "删除抽奖活动（连带逻辑删除奖品池）")
    @Log(title = "抽奖活动", type = "DELETE")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable("id") Long id) {
        lotteryActivityService.removeById(id);
        lotteryPrizeService.remove(new LambdaQueryWrapper<MallLotteryPrize>()
                .eq(MallLotteryPrize::getActivityId, id));
        return Result.ok();
    }

    @Operation(summary = "奖品池整池保存：先按活动逻辑删再批量插")
    @Log(title = "抽奖活动", type = "UPDATE")
    @PostMapping("/prize-save")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> prizeSave(@Valid @RequestBody MallLotteryPrizeSaveDTO dto) {
        MallLotteryActivity activity = lotteryActivityService.getById(dto.getActivityId());
        if (activity == null) {
            throw new BusinessException(400, "活动不存在");
        }
        List<MallLotteryPrizeSaveDTO.PrizeItem> items = dto.getPrizes();
        if (items.isEmpty()) {
            throw new BusinessException(400, "奖品列表不能为空");
        }
        for (MallLotteryPrizeSaveDTO.PrizeItem item : items) {
            validatePrize(item);
        }
        lotteryPrizeService.remove(new LambdaQueryWrapper<MallLotteryPrize>()
                .eq(MallLotteryPrize::getActivityId, dto.getActivityId()));
        List<MallLotteryPrize> entities = items.stream().map(item -> {
            MallLotteryPrize p = new MallLotteryPrize();
            p.setActivityId(dto.getActivityId());
            p.setPrizeType(item.getPrizeType());
            p.setName(item.getName());
            p.setRefId(item.getRefId());
            p.setWeight(item.getWeight());
            p.setStock(item.getStock() == null ? -1 : item.getStock());
            p.setSort(item.getSort() == null ? 0 : item.getSort());
            return p;
        }).toList();
        lotteryPrizeService.saveBatch(entities);
        return Result.ok();
    }

    private void validate(MallLotterySaveDTO dto) {
        if (dto.getEndTime() == null || dto.getStartTime() == null
                || !dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        if (dto.getDailyLimit() != null && dto.getDailyLimit() < 1) {
            throw new BusinessException(400, "每日限次至少 1 次");
        }
        if (dto.getConsumePoints() != null && dto.getConsumePoints() < 0) {
            throw new BusinessException(400, "消耗积分不能为负数");
        }
    }

    private void validatePrize(MallLotteryPrizeSaveDTO.PrizeItem item) {
        if (item.getPrizeType() < 0 || item.getPrizeType() > 3) {
            throw new BusinessException(400, "奖品类型非法：" + item.getName());
        }
        if (item.getWeight() < 0) {
            throw new BusinessException(400, "权重不能为负数：" + item.getName());
        }
        if (item.getPrizeType() != 3 && item.getRefId() == null) {
            throw new BusinessException(400, "积分/优惠券/实物奖品必须配置关联ID：" + item.getName());
        }
        if (item.getPrizeType() == 0 && item.getRefId() <= 0) {
            throw new BusinessException(400, "积分奖品分值必须为正数：" + item.getName());
        }
        if (item.getStock() != null && item.getStock() < -1) {
            throw new BusinessException(400, "库存最小为 -1（不限量）：" + item.getName());
        }
    }
}
