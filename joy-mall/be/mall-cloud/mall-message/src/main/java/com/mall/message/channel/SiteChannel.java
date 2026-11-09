package com.mall.message.channel;

import com.mall.entity.MallMessageTemplate;
import com.mall.entity.SiteNotice;
import com.mall.mapper.SiteNoticeMapper;
import com.mall.message.MessageChannel;
import com.mall.message.MessageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 站内信通道（V14）：写 V7 已有的 {@code mall_notice} 表，不新建表、不改会员域代码。
 *
 * <p>站内信永远可用（同库写入），是降级链路的最终兜底；写入失败说明数据库有问题，
 * 此时调度器会把整条消息记为失败并保留 error_msg。
 *
 * <p>字段限制按表结构硬编码：{@code title} 64 字、{@code content} 255 字——
 * 超长直接截断而不是抛错（消息通知宁可少几个字，也不能因为模板写长了就发不出去）。
 */
@Slf4j
@Component
public class SiteChannel implements MessageChannel {

    private static final int MAX_TITLE = 64;
    private static final int MAX_CONTENT = 255;

    private final SiteNoticeMapper siteNoticeMapper;

    public SiteChannel(SiteNoticeMapper siteNoticeMapper) {
        this.siteNoticeMapper = siteNoticeMapper;
    }

    @Override
    public String code() {
        return MessageChannel.CODE_SITE;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public SendResult send(MessageRequest request) {
        try {
            SiteNotice notice = new SiteNotice();
            notice.setMemberId(request.memberId());
            notice.setType(typeOf(request.sceneCode()));
            notice.setTitle(clip(request.title(), MAX_TITLE));
            notice.setContent(clip(request.content(), MAX_CONTENT));
            notice.setBizId(request.bizId() == null ? "" : clip(request.bizId(), 64));
            notice.setReadStatus(0);
            siteNoticeMapper.insert(notice);
            log.info("站内信已写入：member={}, scene={}, noticeId={}",
                    request.memberId(), request.sceneCode(), notice.getId());
            return SendResult.ok();
        } catch (Exception e) {
            return SendResult.fail("站内信写入失败：" + e.getMessage());
        }
    }

    /** 退款到账归「售后」，支付/发货归「订单」——与 mall_notice.type 的口径一致 */
    private int typeOf(String sceneCode) {
        return MallMessageTemplate.SCENE_REFUND_ARRIVED.equals(sceneCode)
                ? SiteNotice.TYPE_REFUND : SiteNotice.TYPE_ORDER;
    }

    private String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
