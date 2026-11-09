package com.mall.controller.inner;

import com.mall.common.feign.dto.TicketInnerCreateDTO;
import com.mall.dto.TicketCreateDTO;
import com.mall.service.MallTicketService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 客服域内部端点（V16）：AI 转人工建单。
 *
 * <p>走与 C 端完全相同的 {@link MallTicketService#create}，工单号规则、首响 SLA、
 * 超时仲裁口径全部一致——AI 建的单和会员自建的单在后台没有区别。
 * 内部端点直接返回裸 Map，不套 Result 壳（Feign 反序列化约定）。
 */
@RestController
@RequestMapping("/inner/ticket")
public class InnerTicketController {

    private final MallTicketService ticketService;

    public InnerTicketController(MallTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody TicketInnerCreateDTO dto) {
        TicketCreateDTO create = new TicketCreateDTO();
        create.setOrderId(dto.getOrderId());
        create.setOrderNo(dto.getOrderNo());
        create.setCategory(dto.getCategory());
        create.setTitle(dto.getTitle());
        create.setContent(dto.getContent());
        create.setPriority(dto.getPriority());
        return ticketService.create(dto.getMemberId(), create);
    }
}
