package com.mall.support;

/**
 * 工单查询条件（V14）。字段全可空：null 表示不过滤。
 *
 * @param memberId    会员ID（C 端「我的工单」必填；后台为 null）
 * @param status      状态：1待处理2已回复3已关闭
 * @param category    分类：1~5
 * @param keyword     关键字（标题/工单号/订单号模糊）
 * @param timeoutOnly 只看首响超时
 * @param pageNum     页码（从 1 开始）
 * @param pageSize    每页条数
 */
public record TicketQuery(Long memberId, Integer status, Integer category, String keyword,
                          boolean timeoutOnly, long pageNum, long pageSize) {

    public static TicketQuery ofMember(Long memberId, long pageNum, long pageSize) {
        return new TicketQuery(memberId, null, null, null, false, pageNum, pageSize);
    }

    public long offset() {
        return Math.max(0, (Math.max(pageNum, 1) - 1) * Math.max(pageSize, 1));
    }

    public long limit() {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}
