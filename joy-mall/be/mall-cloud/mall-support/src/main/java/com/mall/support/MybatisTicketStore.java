package com.mall.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.entity.MallTicket;
import com.mall.entity.MallTicketReply;
import com.mall.mapper.MallTicketMapper;
import com.mall.mapper.MallTicketReplyMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单存储的 MyBatis 实现（V14）。
 *
 * <p>只写 {@code mall_ticket} / {@code mall_ticket_reply} 两张表；所有列表查询都带
 * {@code deleted=0}（MyBatis-Plus 逻辑删除自动加，无需手写）。
 */
@Component
public class MybatisTicketStore implements TicketStore {

    private final MallTicketMapper ticketMapper;
    private final MallTicketReplyMapper replyMapper;

    public MybatisTicketStore(MallTicketMapper ticketMapper, MallTicketReplyMapper replyMapper) {
        this.ticketMapper = ticketMapper;
        this.replyMapper = replyMapper;
    }

    @Override
    public void saveTicket(MallTicket ticket) {
        ticketMapper.insert(ticket);
    }

    @Override
    public void updateTicket(MallTicket ticket) {
        ticketMapper.updateById(ticket);
    }

    @Override
    public MallTicket findTicket(Long id) {
        return id == null ? null : ticketMapper.selectById(id);
    }

    @Override
    public List<MallTicket> listTickets(TicketQuery query) {
        return ticketMapper.selectList(wrapperOf(query)
                .orderByDesc(MallTicket::getId)
                .last("LIMIT " + query.limit() + " OFFSET " + query.offset()));
    }

    @Override
    public long countTickets(TicketQuery query) {
        Long count = ticketMapper.selectCount(wrapperOf(query));
        return count == null ? 0L : count;
    }

    @Override
    public void saveReply(MallTicketReply reply) {
        replyMapper.insert(reply);
    }

    @Override
    public List<MallTicketReply> listReplies(Long ticketId) {
        return replyMapper.selectList(new LambdaQueryWrapper<MallTicketReply>()
                .eq(MallTicketReply::getTicketId, ticketId)
                .orderByAsc(MallTicketReply::getCreateTime)
                .orderByAsc(MallTicketReply::getId));
    }

    @Override
    public int markTimeouts(LocalDateTime deadline) {
        return ticketMapper.update(null, new LambdaUpdateWrapper<MallTicket>()
                .set(MallTicket::getTimeoutFlag, 1)
                .eq(MallTicket::getStatus, 1)
                .isNull(MallTicket::getFirstReplyTime)
                .lt(MallTicket::getCreateTime, deadline)
                .eq(MallTicket::getTimeoutFlag, 0));
    }

    private LambdaQueryWrapper<MallTicket> wrapperOf(TicketQuery query) {
        LambdaQueryWrapper<MallTicket> wrapper = new LambdaQueryWrapper<>();
        if (query.memberId() != null) {
            wrapper.eq(MallTicket::getMemberId, query.memberId());
        }
        if (query.status() != null) {
            wrapper.eq(MallTicket::getStatus, query.status());
        }
        if (query.category() != null) {
            wrapper.eq(MallTicket::getCategory, query.category());
        }
        if (query.timeoutOnly()) {
            wrapper.eq(MallTicket::getTimeoutFlag, 1);
        }
        if (StringUtils.hasText(query.keyword())) {
            String kw = query.keyword().trim();
            wrapper.and(w -> w.like(MallTicket::getTitle, kw)
                    .or().like(MallTicket::getTicketNo, kw)
                    .or().like(MallTicket::getOrderNo, kw));
        }
        return wrapper;
    }
}
