package com.mall.common.flow;

import com.mall.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 全局审批流端点：前端 flowSubmit/flowApprove 等走 body 风格（flowCode+bizId），
 * 与各业务 controller 的 /{module}/flow/* 路径风格互补，两套并存契约都成立。
 */
@RestController
@RequestMapping("/flow")
public class FlowController {

    @Autowired
    private FlowService flowService;

    @Operation(summary = "提交审批")
    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody Map<String, Object> body) {
        flowService.submit((String) body.get("flowCode"), asLong(body.get("bizId")),
                (String) body.get("comment"));
        return Result.ok();
    }

    @Operation(summary = "审批通过")
    @PostMapping("/approve")
    public Result<Void> approve(@RequestBody Map<String, Object> body) {
        flowService.approve((String) body.get("flowCode"), asLong(body.get("bizId")),
                (String) body.get("comment"));
        return Result.ok();
    }

    @Operation(summary = "审批驳回")
    @PostMapping("/reject")
    public Result<Void> reject(@RequestBody Map<String, Object> body) {
        flowService.reject((String) body.get("flowCode"), asLong(body.get("bizId")),
                (String) body.get("comment"));
        return Result.ok();
    }

    @Operation(summary = "审批记录")
    @GetMapping("/records")
    public Result<List<FlowRecord>> records(@RequestParam String flowCode,
                                            @RequestParam Long bizId) {
        return Result.ok(flowService.records(flowCode, bizId));
    }

    @Operation(summary = "流程状态")
    @GetMapping("/state")
    public Result<Map<String, Object>> state(@RequestParam String flowCode,
                                             @RequestParam(required = false) Long bizId) {
        return Result.ok(flowService.state(flowCode, bizId));
    }

    @Operation(summary = "我的待办（V6.1）")
    @GetMapping("/todo")
    public Result<List<Map<String, Object>>> todo() {
        return Result.ok(flowService.todo());
    }

    @Operation(summary = "我的已办（V6.1）")
    @GetMapping("/done")
    public Result<List<Map<String, Object>>> done() {
        return Result.ok(flowService.done());
    }

    @Operation(summary = "任务详情（V6.1）")
    @GetMapping("/task/{taskId}")
    public Result<Map<String, Object>> taskDetail(@PathVariable String taskId) {
        return Result.ok(flowService.taskDetail(taskId));
    }

    private Long asLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }
}
