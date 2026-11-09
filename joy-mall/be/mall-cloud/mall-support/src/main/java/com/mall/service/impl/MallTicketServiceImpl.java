package com.mall.service.impl;

import com.mall.dto.TicketCreateDTO;
import com.mall.dto.TicketPageDTO;
import com.mall.entity.MallTicket;
import com.mall.entity.MallTicketReply;
import com.mall.service.MallTicketService;
import com.mall.support.TicketFlow;
import com.mall.support.TicketQuery;
import com.mall.support.TicketStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服工单服务实现（V14）。
 *
 * <p>超时策略：<b>查询时惰性置位 + 展示时实时判定</b>两套口径并存——
 * <ul>
 *   <li>惰性置位（{@code mark_timeouts}）：后台进列表页时把「超 24h 未首响」的工单刷成
 *       {@code timeout_flag=1}，好处是不依赖定时任务，调度器挂了也不会漏标；</li>
 *   <li>实时判定（{@link TicketFlow#firstReplyOverdue}）：返回行里带 {@code overdue} 布尔值，
 *       两次刷新之间刚过 24h 的工单在页面上也不会显示成「正常」。</li>
 * </ul>
 */
@Slf4j
@Service
public class MallTicketServiceImpl implements MallTicketService {

    private final TicketStore store;
    private final TicketFlow flow;

    public MallTicketServiceImpl(TicketStore store) {
        this.store = store;
        this.flow = new TicketFlow(store, Clock.systemDefaultZone());
    }

    @Override
    public Map<String, Object> create(Long memberId, TicketCreateDTO dto) {
        if (dto == null) {
            throw new com.mall.common.exception.BusinessException(400, "参数不能为空");
        }
        MallTicket ticket = flow.submit(memberId, dto.getOrderId(), dto.getOrderNo(), dto.getCategory(),
                dto.getTitle(), dto.getContent(), dto.getPriority());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", String.valueOf(ticket.getId()));
        data.put("ticketNo", ticket.getTicketNo());
        data.put("status", ticket.getStatus());
        return data;
    }

    @Override
    public Map<String, Object> adminPage(TicketPageDTO dto) {
        TicketQuery query = new TicketQuery(null, dto.getStatus(), dto.getCategory(), dto.getKeyword(),
                Boolean.TRUE.equals(dto.getTimeoutOnly()), dto.getPageNum(), dto.getPageSize());
        List<MallTicket> rows = flow.pageForAdmin(query);
        return pageOf(rows, store.countTickets(query), dto.getPageNum(), dto.getPageSize());
    }

    @Override
    public Map<String, Object> memberPage(Long memberId, long pageNum, long pageSize) {
        List<MallTicket> rows = flow.pageForMember(memberId, pageNum, pageSize);
        long total = store.countTickets(TicketQuery.ofMember(memberId, pageNum, pageSize));
        return pageOf(rows, total, pageNum, pageSize);
    }

    @Override
    public Map<String, Object> detail(Long ticketId, Long memberId) {
        MallTicket ticket = flow.detail(ticketId, memberId);
        List<MallTicketReply> replies = flow.replies(ticketId);
        Map<String, Object> data = rowOf(ticket);
        List<Map<String, Object>> replyRows = new ArrayList<>();
        for (MallTicketReply r : replies) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(r.getId()));
            row.put("replierType", r.getReplierType());
            row.put("replierTypeText", Integer.valueOf(MallTicketReply.REPLIER_AGENT).equals(r.getReplierType())
                    ? "客服" : "我");
            row.put("content", r.getContent());
            row.put("createTime", r.getCreateTime());
            replyRows.add(row);
        }
        data.put("replies", replyRows);
        return data;
    }

    @Override
    public void agentReply(Long ticketId, Long agentId, String content) {
        flow.replyByAgent(ticketId, agentId, content);
    }

    @Override
    public void memberReply(Long ticketId, Long memberId, String content) {
        flow.replyByMember(ticketId, memberId, content);
    }

    @Override
    public void updateStatus(Long ticketId, Integer status) {
        flow.changeStatus(ticketId, status);
    }

    @Override
    public List<Map<String, Object>> stat() {
        // 先置位再统计：保证「超时数」与列表页看到的一致
        flow.pageForAdmin(new TicketQuery(null, null, null, null, false, 1, 1));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int category = MallTicket.CATEGORY_GOODS; category <= MallTicket.CATEGORY_OTHER; category++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", category);
            row.put("categoryText", MallTicket.categoryText(category));
            row.put("total", store.countTickets(new TicketQuery(null, null, category, null, false, 1, 1)));
            row.put("pending", store.countTickets(new TicketQuery(null, 1, category, null, false, 1, 1)));
            row.put("timeout", store.countTickets(new TicketQuery(null, null, category, null, true, 1, 1)));
            rows.add(row);
        }
        return rows;
    }

    // ===== 装配 =====

    private Map<String, Object> pageOf(List<MallTicket> rows, long total, long pageNum, long pageSize) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (MallTicket t : rows) {
            list.add(rowOf(t));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        // 分页字段统一为 records/total/pageNum/pageSize，与 MyBatis-Plus IPage 及各后台列表页同口径
        data.put("records", list);
        data.put("total", total);
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return data;
    }

    private Map<String, Object> rowOf(MallTicket t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", String.valueOf(t.getId()));
        row.put("ticketNo", t.getTicketNo());
        row.put("memberId", t.getMemberId() == null ? null : String.valueOf(t.getMemberId()));
        row.put("orderId", t.getOrderId() == null ? null : String.valueOf(t.getOrderId()));
        row.put("orderNo", t.getOrderNo());
        row.put("category", t.getCategory());
        row.put("categoryText", MallTicket.categoryText(t.getCategory()));
        row.put("title", t.getTitle());
        row.put("content", t.getContent());
        row.put("status", t.getStatus());
        row.put("statusText", MallTicket.statusText(t.getStatus()));
        row.put("priority", t.getPriority());
        row.put("firstReplyTime", t.getFirstReplyTime());
        row.put("lastReplyTime", t.getLastReplyTime());
        row.put("timeoutFlag", t.getTimeoutFlag());
        row.put("overdue", TicketFlow.firstReplyOverdue(t, LocalDateTime.now())
                || Integer.valueOf(1).equals(t.getTimeoutFlag()));
        row.put("closeTime", t.getCloseTime());
        row.put("createTime", t.getCreateTime());
        return row;
    }
}
