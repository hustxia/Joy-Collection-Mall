package com.mall.controller.inner;

import com.mall.common.result.Result;
import com.mall.dto.MessageSendDTO;
import com.mall.service.MallMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务间消息发送（V14）：{@code POST /inner/message/send}。
 *
 * <p>只在集群内可达（网关 {@code /inner/**} 一律 403），供 order（支付成功/发货）与
 * refund（退款到账）调用。本期不改这两条主链路——接线方案见报告「未完成/降级项」：
 * 调用方只需在状态变更成功后 fire-and-forget 调本接口，失败不影响主流程。
 */
@Tag(name = "内部-消息触达", description = "场景消息发送")
@RestController
@RequestMapping("/inner/message")
public class InnerMessageController {

    private final MallMessageService messageService;

    public InnerMessageController(MallMessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary = "按场景发送消息（场景码 + 接收人 + 变量）")
    @PostMapping("/send")
    public Result<Map<String, Object>> send(@RequestBody MessageSendDTO dto) {
        return Result.ok(messageService.send(dto));
    }

    /**
     * 兼容旧调用方的简写入参（订单号 + 金额分）：不建议新代码使用。
     */
    @Operation(summary = "按场景发送消息（简化入参：订单号 + 金额）", deprecated = true)
    @PostMapping("/send-simple")
    public Result<Map<String, Object>> sendSimple(@RequestBody SimpleSendDTO dto) {
        MessageSendDTO full = new MessageSendDTO();
        full.setSceneCode(dto.getSceneCode());
        full.setMemberId(dto.getMemberId());
        full.setBizId(dto.getOrderNo());
        java.util.Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("orderNo", dto.getOrderNo());
        vars.put("amount", dto.getAmount());
        full.setVars(vars);
        return Result.ok(messageService.send(full));
    }

    @Data
    public static class SimpleSendDTO {
        private String sceneCode;
        private Long memberId;
        private String orderNo;
        /** 金额（分） */
        private Long amount;
    }
}
