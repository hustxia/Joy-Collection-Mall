package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.dto.MallGroupPageDTO;
import com.mall.dto.MallGroupSaveDTO;
import com.mall.entity.MallGroupActivity;
import com.mall.entity.MallGroupTeam;
import com.mall.service.MallGroupActivityService;
import com.mall.service.MallGroupTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.BeanUtils;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 拼团活动管理（V8）。团台账走 /mall_group/team-page。
 */
@Tag(name = "拼团活动管理", description = "拼团活动管理")
@RestController
@RequestMapping("/mall_group")
public class MallGroupController {

    private final MallGroupActivityService groupActivityService;
    private final MallGroupTeamService groupTeamService;
    private final GoodsInnerClient goodsInnerClient;

    public MallGroupController(MallGroupActivityService groupActivityService,
                               MallGroupTeamService groupTeamService,
                               GoodsInnerClient goodsInnerClient) {
        this.groupActivityService = groupActivityService;
        this.groupTeamService = groupTeamService;
        this.goodsInnerClient = goodsInnerClient;
    }

    @Operation(summary = "分页查询拼团活动（含商品名）")
    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestBody MallGroupPageDTO dto) {
        Page<MallGroupActivity> page = new Page<>(dto.getPageNum(), Math.min(dto.getPageSize(), 100));
        LambdaQueryWrapper<MallGroupActivity> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (dto.getStatus() != null) {
                wrapper.eq(MallGroupActivity::getStatus, dto.getStatus());
            }
            if (dto.getGoodsId() != null) {
                wrapper.eq(MallGroupActivity::getGoodsId, dto.getGoodsId());
            }
        }
        wrapper.orderByDesc(MallGroupActivity::getId);
        groupActivityService.page(page, wrapper);

        List<Long> goodsIds = page.getRecords().stream()
                .map(MallGroupActivity::getGoodsId).distinct().toList();
        Map<Long, GoodsBrief> goodsMap = goodsIds.isEmpty() ? Map.of()
                : goodsInnerClient.goodsByIds(goodsIds).stream()
                        .collect(Collectors.toMap(GoodsBrief::getId, Function.identity()));
        Page<Map<String, Object>> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            GoodsBrief g = goodsMap.get(a.getGoodsId());
            m.put("id", String.valueOf(a.getId()));
            m.put("goodsId", String.valueOf(a.getGoodsId()));
            m.put("skuId", String.valueOf(a.getSkuId()));
            m.put("goodsName", g == null ? null : g.getName());
            m.put("originPrice", a.getOriginPrice());
            m.put("groupPrice", a.getGroupPrice());
            m.put("groupSize", a.getGroupSize());
            m.put("validHours", a.getValidHours());
            m.put("startTime", a.getStartTime());
            m.put("endTime", a.getEndTime());
            m.put("status", a.getStatus());
            m.put("createTime", a.getCreateTime());
            return m;
        }).toList());
        return Result.ok(voPage);
    }

    @Operation(summary = "根据ID查询拼团活动")
    @GetMapping("/{id}")
    public Result<MallGroupActivity> getById(@PathVariable("id") Long id) {
        return Result.ok(groupActivityService.getById(id));
    }

    @Operation(summary = "新增拼团活动")
    @Log(title = "拼团活动", type = "INSERT")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody MallGroupSaveDTO dto) {
        validate(dto);
        MallGroupActivity entity = new MallGroupActivity();
        BeanUtils.copyProperties(dto, entity, "id");
        entity.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        groupActivityService.save(entity);
        return Result.ok();
    }

    @Operation(summary = "修改拼团活动")
    @Log(title = "拼团活动", type = "UPDATE")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody MallGroupSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        MallGroupActivity old = groupActivityService.getById(dto.getId());
        if (old == null) {
            throw new BusinessException(400, "活动不存在");
        }
        validate(dto);
        MallGroupActivity entity = new MallGroupActivity();
        BeanUtils.copyProperties(dto, entity);
        entity.setStatus(dto.getStatus() == null ? old.getStatus() : dto.getStatus());
        groupActivityService.updateById(entity);
        return Result.ok();
    }

    @Operation(summary = "删除拼团活动")
    @Log(title = "拼团活动", type = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        groupActivityService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "团台账分页（按活动/状态过滤）")
    @PostMapping("/team-page")
    public Result<Page<Map<String, Object>>> teamPage(@RequestBody TeamPageDTO dto) {
        Page<MallGroupTeam> page = new Page<>(dto.getPageNum(), Math.min(dto.getPageSize(), 100));
        LambdaQueryWrapper<MallGroupTeam> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (dto.getActivityId() != null) {
                wrapper.eq(MallGroupTeam::getActivityId, dto.getActivityId());
            }
            if (dto.getStatus() != null) {
                wrapper.eq(MallGroupTeam::getStatus, dto.getStatus());
            }
        }
        wrapper.orderByDesc(MallGroupTeam::getId);
        groupTeamService.page(page, wrapper);

        List<Long> activityIds = page.getRecords().stream()
                .map(MallGroupTeam::getActivityId).distinct().toList();
        Map<Long, MallGroupActivity> activityMap = activityIds.isEmpty() ? Map.of()
                : groupActivityService.listByIds(activityIds).stream()
                        .collect(Collectors.toMap(MallGroupActivity::getId, Function.identity()));

        Page<Map<String, Object>> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            MallGroupActivity a = activityMap.get(t.getActivityId());
            m.put("id", String.valueOf(t.getId()));
            m.put("activityId", String.valueOf(t.getActivityId()));
            m.put("leaderId", String.valueOf(t.getLeaderId()));
            m.put("currentSize", t.getCurrentSize());
            m.put("groupSize", a == null ? null : a.getGroupSize());
            m.put("status", t.getStatus());
            m.put("expireTime", t.getExpireTime());
            m.put("createTime", t.getCreateTime());
            return m;
        }).toList());
        return Result.ok(voPage);
    }

    private void validate(MallGroupSaveDTO dto) {
        if (dto.getEndTime() == null || dto.getStartTime() == null
                || !dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        if (dto.getGroupPrice() != null && dto.getOriginPrice() != null
                && dto.getGroupPrice() > dto.getOriginPrice()) {
            throw new BusinessException(400, "拼团价不能高于原价");
        }
        if (dto.getGroupSize() != null && dto.getGroupSize() < 2) {
            throw new BusinessException(400, "成团人数至少 2 人");
        }
        if (dto.getValidHours() != null && dto.getValidHours() < 1) {
            throw new BusinessException(400, "成团时限至少 1 小时");
        }
    }

    /** 团台账查询 DTO */
    @Data
    public static class TeamPageDTO {
        private long pageNum = 1;
        private long pageSize = 10;
        /** 按活动过滤（可选） */
        private Long activityId;
        /** 团状态：0拼团中1已成团2已失败（可选） */
        private Integer status;
    }
}
