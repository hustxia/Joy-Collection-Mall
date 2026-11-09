package com.mall.controller;

import com.mall.common.result.Result;
import com.mall.dto.MessageTemplateSaveDTO;
import com.mall.service.MallMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台消息日志（V14）：排查「谁没收到通知」的第一入口。
 *
 * <p>过滤条件里 {@code degraded=1} 就是「发生过降级」的全部记录，
 * 与验收口径「短信故障时站内信仍写入且日志标记降级」一一对应。
 */
@Tag(name = "消息日志", description = "消息发送台账")
@RestController
@RequestMapping("/mall_message_log")
public class MallMessageLogController {

    private final MallMessageService messageService;

    public MallMessageLogController(MallMessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary = "消息日志分页（状态/是否降级/场景码）")
    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) Integer degraded,
                                            @RequestParam(required = false) String sceneCode,
                                            @RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(messageService.pageLog(status, degraded, sceneCode, pageNum, pageSize));
    }

    @Operation(summary = "模板分页")
    @GetMapping("/template/page")
    public Result<Map<String, Object>> templatePage(@RequestParam(required = false) String sceneCode,
                                                    @RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(messageService.pageTemplate(sceneCode, pageNum, pageSize));
    }

    @Operation(summary = "模板保存（新增/修改）")
    @PostMapping("/template")
    public Result<Void> saveTemplate(@RequestBody MessageTemplateSaveDTO dto) {
        messageService.saveTemplate(dto);
        return Result.ok();
    }
}
