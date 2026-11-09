package com.mall.system;

import com.mall.common.result.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 操作日志查询。只读接口——能改的日志没有审计价值。
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/system/oper-log")
public class OpeLogController {

    @Autowired
    private OpeLogMapper opeLogMapper;

    @Operation(summary = "分页查询操作日志")
    @GetMapping("/page")
    public Result<Page<OpeLog>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String operName,
            @RequestParam(required = false) String operType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Page<OpeLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<OpeLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(title), OpeLog::getTitle, title);
        wrapper.like(StringUtils.hasText(operName), OpeLog::getOperName, operName);
        wrapper.eq(StringUtils.hasText(operType), OpeLog::getOperType, operType);
        wrapper.eq(status != null, OpeLog::getStatus, status);
        wrapper.ge(startTime != null, OpeLog::getOperTime, startTime);
        wrapper.le(endTime != null, OpeLog::getOperTime, endTime);
        wrapper.orderByDesc(OpeLog::getId);
        opeLogMapper.selectPage(page, wrapper);
        return Result.ok(page);
    }

    @Operation(summary = "清理指定时间之前的日志")
    @DeleteMapping("/before")
    public Result<Integer> cleanBefore(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime before) {
        LambdaQueryWrapper<OpeLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(OpeLog::getOperTime, before);
        return Result.ok(opeLogMapper.delete(wrapper));
    }
}
