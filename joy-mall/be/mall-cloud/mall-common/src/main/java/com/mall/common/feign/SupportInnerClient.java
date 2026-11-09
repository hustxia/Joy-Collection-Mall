package com.mall.common.feign;

import com.mall.common.feign.dto.TicketInnerCreateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** 客服域内部契约（V16）：AI 转人工时建单 */
@FeignClient(name = "mall-support", url = "${feign.clients.mall-support:http://localhost:8208}")
public interface SupportInnerClient {

    @PostMapping("/inner/ticket/create")
    Map<String, Object> createTicket(@RequestBody TicketInnerCreateDTO dto);
}
