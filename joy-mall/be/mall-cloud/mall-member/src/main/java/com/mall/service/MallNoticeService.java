package com.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallNotice;

/**
 * 站内消息 Service（V7 手写）
 */
public interface MallNoticeService extends IService<MallNotice> {

    /**
     * 当前会员消息分页（新→旧），type 可空。
     */
    Page<MallNotice> pageOf(Long memberId, Integer type, int pageNum, int pageSize);

    /**
     * 当前会员未读数。
     */
    long unreadCount(Long memberId);

    /**
     * 已读一条：仅本人消息可操作，已读的重复调用幂等成功。
     */
    void markRead(Long id, Long memberId);

    /**
     * 当前会员全部未读标记为已读，返回影响行数。
     */
    int markAllRead(Long memberId);

    /**
     * 内部推送：直接插入一条未读消息，返回消息ID。
     */
    Long push(Long memberId, Integer type, String title, String content, String bizId);
}
