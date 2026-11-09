package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.dto.MallNoticePageDTO;
import com.mall.entity.MallNotice;
import com.mall.service.MallNoticeService;
import com.mall.vo.MallNoticeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内消息后台只读台账（V7 手写）：消息由内部端点 /inner/notice/push 产生，后台只查不改。
 */
@Tag(name = "站内消息管理", description = "站内消息管理")
@RestController
@RequestMapping("/mall_notice")
public class MallNoticeController {

    @Autowired
    private MallNoticeService mallNoticeService;

    @Operation(summary = "分页查询站内消息（支持 memberId/type/readStatus 筛选）")
    @PostMapping("/page")
    public Result<Page<MallNoticeVO>> page(@RequestBody MallNoticePageDTO dto) {
        Page<MallNotice> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<MallNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getMemberId() != null, MallNotice::getMemberId, dto.getMemberId());
        wrapper.eq(dto.getType() != null, MallNotice::getType, dto.getType());
        wrapper.eq(dto.getReadStatus() != null, MallNotice::getReadStatus, dto.getReadStatus());
        wrapper.orderByDesc(MallNotice::getId);
        mallNoticeService.page(page, wrapper);
        Page<MallNoticeVO> voPage = new Page<>();
        BeanUtils.copyProperties(page, voPage);
        List<MallNoticeVO> records = page.getRecords().stream()
                .map(e -> {
                    MallNoticeVO vo = new MallNoticeVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        voPage.setRecords(records);
        return Result.ok(voPage);
    }
}
