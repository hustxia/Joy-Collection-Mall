package com.mall.support;

import com.mall.common.exception.BusinessException;
import com.mall.entity.MallTicket;
import com.mall.entity.MallTicketReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单链路纯单测（V14，REQ-P1-08）。
 *
 * <p>不用 Spring、不连库：{@link TicketFlow} 只依赖 {@link TicketStore} 与 {@link Clock}，
 * 这里用内存 Store + 可拨动时钟，把需求文档的两条验收口径锁死：
 * 「提交 → 后台可见 → 回复 → 会员可见」「超 24 小时未首响标记待处理超时」。
 * SQL 层（分页、条件更新）由服务级集成测试与手工冒烟覆盖。
 */
class TicketFlowTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private MutableClock clock;
    private InMemoryTicketStore store;
    private TicketFlow flow;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(LocalDateTime.of(2026, 11, 1, 9, 0).atZone(ZONE).toInstant());
        store = new InMemoryTicketStore();
        flow = new TicketFlow(store, clock);
    }

    @Test
    @DisplayName("提交 → 后台可见 → 客服回复 → 会员可见（首响时间落库、状态置已回复）")
    void submitReplyVisible() {
        MallTicket ticket = flow.submit(9001L, 6001L, "SO20261101001", MallTicket.CATEGORY_ORDER,
                "发货两天没物流", "下单两天了还是待发货，帮忙催一下", 3);

        assertNotNull(ticket.getId(), "落库后必须有主键");
        assertTrue(ticket.getTicketNo().startsWith("T20261101"), "工单号应为 T+日期+随机");
        assertEquals(1, ticket.getStatus());

        List<MallTicket> adminRows = flow.pageForAdmin(
                new TicketQuery(null, null, null, null, false, 1, 10));
        assertEquals(1, adminRows.size(), "后台必须能看到会员刚提交的工单");
        assertEquals(9001L, adminRows.get(0).getMemberId());

        flow.replyByAgent(ticket.getId(), 1L, "已联系仓库，今天下午出库");

        MallTicket afterReply = store.findTicket(ticket.getId());
        assertEquals(2, afterReply.getStatus(), "客服回复后状态应为已回复");
        assertNotNull(afterReply.getFirstReplyTime(), "首响时间必须写入，SLA 停止计时");

        List<MallTicketReply> replies = flow.replies(ticket.getId());
        assertEquals(1, replies.size());
        assertEquals(2, replies.get(0).getReplierType(), "客服回复 type=2");

        List<MallTicket> memberRows = flow.pageForMember(9001L, 1, 10);
        assertEquals(1, memberRows.size(), "会员端必须能看到自己的工单");
        assertEquals(2, memberRows.get(0).getStatus());
    }

    @Test
    @DisplayName("超 24 小时未首响：惰性标记 timeout_flag=1，overdue 判定为 true")
    void overdueAfter24h() {
        MallTicket ticket = flow.submit(9002L, null, null, MallTicket.CATEGORY_GOODS,
                "尺码咨询", "这款夹克偏大还是偏小", null);

        // 23 小时：还不算超时
        clock.plus(Duration.ofHours(23));
        List<MallTicket> rows = flow.pageForAdmin(new TicketQuery(null, null, null, null, false, 1, 10));
        assertEquals(0, rows.get(0).getTimeoutFlag(), "未满 24h 不应标超时");
        assertFalse(TicketFlow.firstReplyOverdue(rows.get(0), LocalDateTime.now(clock)));

        // 25 小时：进后台列表即被置位
        clock.plus(Duration.ofHours(2));
        List<MallTicket> overdueRows = flow.pageForAdmin(
                new TicketQuery(null, null, null, null, true, 1, 10));
        assertEquals(1, overdueRows.size(), "只看超时的过滤条件应命中该工单");
        assertEquals(1, overdueRows.get(0).getTimeoutFlag());
        assertTrue(TicketFlow.firstReplyOverdue(store.findTicket(ticket.getId()), LocalDateTime.now(clock)));
    }

    @Test
    @DisplayName("已首响的工单不会再被标超时（即使之后又过了很久）")
    void repliedTicketNeverOverdue() {
        MallTicket ticket = flow.submit(9003L, null, null, MallTicket.CATEGORY_LOGISTICS,
                "物流停滞", "快递三天没更新", null);
        clock.plus(Duration.ofHours(2));
        flow.replyByAgent(ticket.getId(), 1L, "已催促快递，预计明天更新");

        clock.plus(Duration.ofHours(48));
        flow.pageForAdmin(new TicketQuery(null, null, null, null, false, 1, 10));
        MallTicket fresh = store.findTicket(ticket.getId());
        assertEquals(0, fresh.getTimeoutFlag(), "已首响的工单不能被刷成超时");
        assertFalse(TicketFlow.firstReplyOverdue(fresh, LocalDateTime.now(clock)));
    }

    @Test
    @DisplayName("会员留言：刷新 lastReplyTime、不改状态；他人越权 403；已关闭 400")
    void memberReplyRules() {
        MallTicket ticket = flow.submit(9004L, null, null, MallTicket.CATEGORY_PAYMENT,
                "支付失败", "微信支付提示失败但钱扣了", null);
        clock.plus(Duration.ofMinutes(10));
        flow.replyByMember(ticket.getId(), 9004L, "补充：订单号 SO20261101009");

        MallTicket fresh = store.findTicket(ticket.getId());
        assertEquals(1, fresh.getStatus(), "会员留言不改变状态");
        assertNotNull(fresh.getLastReplyTime());
        assertEquals(1, flow.replies(ticket.getId()).size(), "只有会员这条留言，客服尚未回复");

        BusinessException forbidden = assertThrows(BusinessException.class,
                () -> flow.replyByMember(ticket.getId(), 9999L, "我不是本人"));
        assertEquals(403, forbidden.getCode(), "越权查看/留言必须 403");

        flow.changeStatus(ticket.getId(), 3);
        BusinessException closed = assertThrows(BusinessException.class,
                () -> flow.replyByMember(ticket.getId(), 9004L, "关闭后还能留言吗"));
        assertEquals(400, closed.getCode());
    }

    @Test
    @DisplayName("沟通记录：会员与客服消息按时间顺序排列，两个方向都可读")
    void conversationOrder() {
        MallTicket ticket = flow.submit(9005L, null, null, MallTicket.CATEGORY_OTHER,
                "发票问题", "电子发票什么时候能开", null);
        clock.plus(Duration.ofMinutes(5));
        flow.replyByMember(ticket.getId(), 9005L, "补充：需要专票");
        clock.plus(Duration.ofMinutes(5));
        flow.replyByAgent(ticket.getId(), 1L, "专票需要 3 个工作日，已登记");

        List<MallTicketReply> replies = flow.replies(ticket.getId());
        assertEquals(2, replies.size());
        assertEquals(MallTicketReply.REPLIER_MEMBER, replies.get(0).getReplierType());
        assertEquals(MallTicketReply.REPLIER_AGENT, replies.get(1).getReplierType());
        assertTrue(replies.get(0).getCreateTime().isBefore(replies.get(1).getCreateTime()));
    }

    @Test
    @DisplayName("校验：标题/内容为空、分类越界一律 400，不产生脏数据")
    void validation() {
        assertEquals(400, assertThrows(BusinessException.class, () -> flow.submit(
                9006L, null, null, 1, "  ", "内容", null)).getCode());
        assertEquals(400, assertThrows(BusinessException.class, () -> flow.submit(
                9006L, null, null, 1, "标题", "", null)).getCode());
        assertEquals(400, assertThrows(BusinessException.class, () -> flow.submit(
                9006L, null, null, 9, "标题", "内容", null)).getCode());
        assertEquals(0, store.tickets.size(), "校验失败不能落库");
    }

    @Test
    @DisplayName("状态机：关闭写 closeTime 且幂等；重新打开清空 closeTime")
    void statusMachine() {
        MallTicket ticket = flow.submit(9007L, null, null, MallTicket.CATEGORY_ORDER, "退换货", "想换货", null);
        flow.changeStatus(ticket.getId(), 3);
        LocalDateTime firstClose = store.findTicket(ticket.getId()).getCloseTime();
        assertNotNull(firstClose);

        clock.plus(Duration.ofHours(1));
        flow.changeStatus(ticket.getId(), 3);
        assertEquals(firstClose, store.findTicket(ticket.getId()).getCloseTime(), "重复关闭不刷新关闭时间");

        flow.changeStatus(ticket.getId(), 1);
        assertNull(store.findTicket(ticket.getId()).getCloseTime(), "重新打开需清空关闭时间");
        assertEquals(400, assertThrows(BusinessException.class,
                () -> flow.changeStatus(ticket.getId(), 9)).getCode());
    }

    // ===== 测试替身 =====

    /** 可拨动的时钟：让「24 小时」在毫秒内被测完 */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void plus(Duration d) {
            instant = instant.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    /** 内存 Store：语义对齐 MybatisTicketStore（含 markTimeouts 的 SQL 条件） */
    private static final class InMemoryTicketStore implements TicketStore {
        private final AtomicLong seq = new AtomicLong(1000);
        private final List<MallTicket> tickets = new ArrayList<>();
        private final List<MallTicketReply> replies = new ArrayList<>();

        @Override
        public void saveTicket(MallTicket ticket) {
            ticket.setId(seq.incrementAndGet());
            if (ticket.getDeleted() == null) {
                ticket.setDeleted(0);
            }
            tickets.add(ticket);
        }

        @Override
        public void updateTicket(MallTicket ticket) {
            // 实体本身就是同一个引用，update 只为与生产语义一致（存在性校验）
            if (findTicket(ticket.getId()) == null) {
                throw new IllegalStateException("工单不存在：" + ticket.getId());
            }
        }

        @Override
        public MallTicket findTicket(Long id) {
            return tickets.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public List<MallTicket> listTickets(TicketQuery query) {
            List<MallTicket> out = new ArrayList<>();
            for (MallTicket t : tickets) {
                if (query.memberId() != null && !query.memberId().equals(t.getMemberId())) {
                    continue;
                }
                if (query.status() != null && !query.status().equals(t.getStatus())) {
                    continue;
                }
                if (query.category() != null && !query.category().equals(t.getCategory())) {
                    continue;
                }
                if (query.timeoutOnly() && !Integer.valueOf(1).equals(t.getTimeoutFlag())) {
                    continue;
                }
                out.add(t);
            }
            out.sort(Comparator.comparing(MallTicket::getId).reversed());
            int from = (int) Math.min(query.offset(), out.size());
            int to = (int) Math.min(from + query.limit(), out.size());
            return new ArrayList<>(out.subList(from, to));
        }

        @Override
        public long countTickets(TicketQuery query) {
            return listTickets(new TicketQuery(query.memberId(), query.status(), query.category(),
                    query.keyword(), query.timeoutOnly(), 1, 1000)).size();
        }

        @Override
        public void saveReply(MallTicketReply reply) {
            reply.setId(seq.incrementAndGet());
            replies.add(reply);
        }

        @Override
        public List<MallTicketReply> listReplies(Long ticketId) {
            return replies.stream()
                    .filter(r -> r.getTicketId().equals(ticketId))
                    .sorted(Comparator.comparing(MallTicketReply::getCreateTime))
                    .toList();
        }

        @Override
        public int markTimeouts(LocalDateTime deadline) {
            int marked = 0;
            for (MallTicket t : tickets) {
                boolean hit = Integer.valueOf(1).equals(t.getStatus())
                        && t.getFirstReplyTime() == null
                        && t.getCreateTime() != null
                        && t.getCreateTime().isBefore(deadline)
                        && !Integer.valueOf(1).equals(t.getTimeoutFlag());
                if (hit) {
                    t.setTimeoutFlag(1);
                    marked++;
                }
            }
            return marked;
        }
    }
}
