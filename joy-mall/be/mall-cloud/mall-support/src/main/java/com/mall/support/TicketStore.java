package com.mall.support;

import com.mall.entity.MallTicket;
import com.mall.entity.MallTicketReply;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单存储端口（V14）。
 *
 * <p>领域逻辑 {@link TicketFlow} 只依赖这个接口，因此「提交 → 后台可见 → 回复 → 会员可见 →
 * 24h 超时标记」这条链路可以在纯单测里用内存实现跑完（不连库、不起 Spring）。
 * 生产实现是 {@code MybatisTicketStore}（MyBatis-Plus）。
 */
public interface TicketStore {

    /** 新增工单，回填主键与创建时间 */
    void saveTicket(MallTicket ticket);

    /** 全字段更新（回复会同时改 status / firstReplyTime / lastReplyTime） */
    void updateTicket(MallTicket ticket);

    /** 按主键取（不存在返回 null） */
    MallTicket findTicket(Long id);

    /** 条件分页查询（按 id 降序） */
    List<MallTicket> listTickets(TicketQuery query);

    /** 条件计数 */
    long countTickets(TicketQuery query);

    /** 新增回复，回填主键与创建时间 */
    void saveReply(MallTicketReply reply);

    /** 某工单的全部回复（按时间升序） */
    List<MallTicketReply> listReplies(Long ticketId);

    /**
     * 批量置位首响超时标记：{@code status=1 AND first_reply_time IS NULL AND create_time < deadline}
     * 的工单置 timeout_flag=1，返回受影响行数。
     */
    int markTimeouts(LocalDateTime deadline);
}
