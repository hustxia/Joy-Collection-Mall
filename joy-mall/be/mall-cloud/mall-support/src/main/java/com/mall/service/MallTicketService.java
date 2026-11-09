package com.mall.service;

import com.mall.dto.TicketCreateDTO;
import com.mall.dto.TicketPageDTO;

import java.util.List;
import java.util.Map;

/**
 * 客服工单服务（V14，REQ-P1-08）。
 *
 * <p>领域规则在 {@code com.mall.support.TicketFlow}（纯逻辑、可单测），
 * 这里只负责「DTO → 领域 → 视图 Map」的装配与分页包装。
 */
public interface MallTicketService {

    /** 会员提交工单，返回工单号 */
    Map<String, Object> create(Long memberId, TicketCreateDTO dto);

    /** 后台工单分页（含超时惰性标记） */
    Map<String, Object> adminPage(TicketPageDTO dto);

    /** 会员「我的工单」分页 */
    Map<String, Object> memberPage(Long memberId, long pageNum, long pageSize);

    /** 工单详情（含回复）；memberId 非空时做归属校验 */
    Map<String, Object> detail(Long ticketId, Long memberId);

    /** 客服回复 */
    void agentReply(Long ticketId, Long agentId, String content);

    /** 会员回复 */
    void memberReply(Long ticketId, Long memberId, String content);

    /** 后台改状态 */
    void updateStatus(Long ticketId, Integer status);

    /** 分类统计：每个分类的总数 / 待处理数 / 超时数 */
    List<Map<String, Object>> stat();
}
