package com.mall.support;

import com.mall.common.exception.BusinessException;
import com.mall.entity.MallTicket;
import com.mall.entity.MallTicketReply;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工单领域逻辑（V14 纯逻辑，无 Spring / 无数据库）。
 *
 * <p>把「谁能改、改成什么状态、什么时候算超时」全部收在这里，controller 只做参数搬运：
 * <ul>
 *   <li>提交：分类/标题/内容校验，状态置「待处理」，优先级缺省「中」；</li>
 *   <li>客服回复：首次回复写入 {@code firstReplyTime}（SLA 停止计时），状态置「已回复」；</li>
 *   <li>会员回复：只允许本人、且工单未关闭；不改状态，只刷新 {@code lastReplyTime}；</li>
 *   <li>关闭：幂等，重复关闭不报错但要保留首次关闭时间；</li>
 *   <li>超时：{@code firstReplyTime == null && now - createTime > SLA} → 「待处理超时」。</li>
 * </ul>
 *
 * <p>时间一律走注入的 {@link Clock}，这样「24 小时未回复」可以在单测里用固定时钟断言，
 * 不用 sleep、不用改系统时间。
 */
@Slf4j
public class TicketFlow {

    /** 首响 SLA：需求 REQ-P1-08「超 24 小时未回复标记待处理超时」 */
    public static final Duration FIRST_REPLY_SLA = Duration.ofHours(24);

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TicketStore store;
    private final Clock clock;

    public TicketFlow(TicketStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** 会员提交工单 */
    public MallTicket submit(Long memberId, Long orderId, String orderNo, Integer category,
                             String title, String content, Integer priority) {
        if (memberId == null) {
            throw new BusinessException(401, "请先登录");
        }
        String cleanTitle = requireText(title, "标题不能为空", 128);
        String cleanContent = requireText(content, "问题描述不能为空", 2000);
        int cat = category == null ? MallTicket.CATEGORY_OTHER : category;
        if (cat < MallTicket.CATEGORY_GOODS || cat > MallTicket.CATEGORY_OTHER) {
            throw new BusinessException(400, "工单分类不正确");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        MallTicket ticket = new MallTicket();
        ticket.setTicketNo(nextTicketNo(now));
        ticket.setMemberId(memberId);
        ticket.setOrderId(orderId);
        ticket.setOrderNo(orderNo);
        ticket.setCategory(cat);
        ticket.setTitle(cleanTitle);
        ticket.setContent(cleanContent);
        ticket.setStatus(1);
        ticket.setPriority(priority == null || priority < 1 || priority > 3 ? 2 : priority);
        ticket.setTimeoutFlag(0);
        ticket.setCreateTime(now);
        ticket.setUpdateTime(now);
        store.saveTicket(ticket);
        return ticket;
    }

    /** 客服回复：首响时间只写一次，之后只刷新 lastReplyTime */
    public MallTicket replyByAgent(Long ticketId, Long agentId, String content) {
        MallTicket ticket = requireTicket(ticketId);
        String text = requireText(content, "回复内容不能为空", 2000);
        LocalDateTime now = LocalDateTime.now(clock);
        if (ticket.getFirstReplyTime() == null) {
            ticket.setFirstReplyTime(now);
            // 首响完成即解除超时标记：客服处理过的工单不该继续挂在「超时」列表里
            ticket.setTimeoutFlag(0);
        }
        ticket.setLastReplyTime(now);
        ticket.setStatus(2);
        ticket.setUpdateTime(now);
        store.updateTicket(ticket);
        store.saveReply(reply(ticketId, MallTicketReply.REPLIER_AGENT, agentId, text, now));
        return ticket;
    }

    /** 会员追加留言：必须本人、工单未关闭 */
    public MallTicket replyByMember(Long ticketId, Long memberId, String content) {
        MallTicket ticket = requireTicket(ticketId);
        if (memberId == null || !memberId.equals(ticket.getMemberId())) {
            throw new BusinessException(403, "无权操作他人工单");
        }
        if (Integer.valueOf(3).equals(ticket.getStatus())) {
            throw new BusinessException(400, "工单已关闭，请新建工单");
        }
        String text = requireText(content, "回复内容不能为空", 2000);
        LocalDateTime now = LocalDateTime.now(clock);
        ticket.setLastReplyTime(now);
        ticket.setUpdateTime(now);
        store.updateTicket(ticket);
        store.saveReply(reply(ticketId, MallTicketReply.REPLIER_MEMBER, memberId, text, now));
        return ticket;
    }

    /** 后台改状态（1 待处理 / 2 已回复 / 3 已关闭）：幂等 */
    public MallTicket changeStatus(Long ticketId, Integer status) {
        MallTicket ticket = requireTicket(ticketId);
        if (status == null || status < 1 || status > 3) {
            throw new BusinessException(400, "工单状态不正确");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ticket.setStatus(status);
        ticket.setUpdateTime(now);
        if (status == 3 && ticket.getCloseTime() == null) {
            ticket.setCloseTime(now);
        }
        if (status != 3) {
            ticket.setCloseTime(null);
        }
        store.updateTicket(ticket);
        return ticket;
    }

    /** 后台列表：先惰性置位超时（老工单无需定时任务也能被标出来），再按条件查 */
    public List<MallTicket> pageForAdmin(TicketQuery query) {
        int marked = store.markTimeouts(LocalDateTime.now(clock).minus(FIRST_REPLY_SLA));
        if (marked > 0) {
            // 只在真的改到行时打日志，避免每次查询都刷一行
            log.info("工单首响超时惰性标记：{} 条", marked);
        }
        return store.listTickets(query);
    }

    /** 会员端「我的工单」 */
    public List<MallTicket> pageForMember(Long memberId, long pageNum, long pageSize) {
        if (memberId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return store.listTickets(TicketQuery.ofMember(memberId, pageNum, pageSize));
    }

    /** 详情（含回复）；会员只能看自己的 */
    public MallTicket detail(Long ticketId, Long memberId) {
        MallTicket ticket = requireTicket(ticketId);
        if (memberId != null && !memberId.equals(ticket.getMemberId())) {
            throw new BusinessException(403, "无权查看他人工单");
        }
        return ticket;
    }

    public List<MallTicketReply> replies(Long ticketId) {
        return store.listReplies(ticketId);
    }

    /** 首响是否已超时（不落库，纯判定：列表展示与告警共用同一口径） */
    public static boolean firstReplyOverdue(MallTicket ticket, LocalDateTime now) {
        if (ticket == null || ticket.getFirstReplyTime() != null || ticket.getCreateTime() == null) {
            return false;
        }
        return ticket.getCreateTime().plus(FIRST_REPLY_SLA).isBefore(now);
    }

    private MallTicket requireTicket(Long ticketId) {
        if (ticketId == null) {
            throw new BusinessException(400, "工单ID不能为空");
        }
        MallTicket ticket = store.findTicket(ticketId);
        if (ticket == null) {
            throw new BusinessException(400, "工单不存在");
        }
        return ticket;
    }

    private MallTicketReply reply(Long ticketId, int type, Long replierId, String content, LocalDateTime now) {
        MallTicketReply reply = new MallTicketReply();
        reply.setTicketId(ticketId);
        reply.setReplierType(type);
        reply.setReplierId(replierId);
        reply.setContent(content);
        reply.setCreateTime(now);
        reply.setUpdateTime(now);
        return reply;
    }

    private String requireText(String value, String message, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, message);
        }
        String v = value.trim();
        if (v.length() > maxLength) {
            throw new BusinessException(400, message.replace("不能为空", "过长（最多 " + maxLength + " 字）"));
        }
        return v;
    }

    /** 工单号：T + yyyyMMdd + 6 位随机（同日撞号概率 1e-6，且有 uk 兜底重试由前端重提） */
    static String nextTicketNo(LocalDateTime now) {
        return "T" + NO_DATE.format(now) + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
