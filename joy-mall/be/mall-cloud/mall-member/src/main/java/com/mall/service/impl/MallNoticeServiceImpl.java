package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.entity.MallNotice;
import com.mall.mapper.MallNoticeMapper;
import com.mall.service.MallNoticeService;
import org.springframework.stereotype.Service;

/**
 * 站内消息 Service（V7 手写）
 */
@Service
public class MallNoticeServiceImpl extends ServiceImpl<MallNoticeMapper, MallNotice> implements MallNoticeService {

    @Override
    public Page<MallNotice> pageOf(Long memberId, Integer type, int pageNum, int pageSize) {
        Page<MallNotice> page = new Page<>(Math.max(pageNum, 1), Math.max(pageSize, 1));
        LambdaQueryWrapper<MallNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallNotice::getMemberId, memberId)
                .eq(type != null, MallNotice::getType, type)
                .orderByDesc(MallNotice::getId);
        return page(page, wrapper);
    }

    @Override
    public long unreadCount(Long memberId) {
        LambdaQueryWrapper<MallNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallNotice::getMemberId, memberId).eq(MallNotice::getReadStatus, 0);
        return count(wrapper);
    }

    @Override
    public void markRead(Long id, Long memberId) {
        MallNotice notice = getById(id);
        if (notice == null) {
            throw new BusinessException(400, "消息不存在");
        }
        if (!notice.getMemberId().equals(memberId)) {
            throw new BusinessException(403, "无权操作他人消息");
        }
        // 条件更新（read_status=0 才置 1）：已读的消息重复调用幂等成功
        LambdaUpdateWrapper<MallNotice> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MallNotice::getId, id)
                .eq(MallNotice::getMemberId, memberId)
                .eq(MallNotice::getReadStatus, 0)
                .set(MallNotice::getReadStatus, 1);
        update(wrapper);
    }

    @Override
    public int markAllRead(Long memberId) {
        LambdaUpdateWrapper<MallNotice> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MallNotice::getMemberId, memberId)
                .eq(MallNotice::getReadStatus, 0)
                .set(MallNotice::getReadStatus, 1);
        return getBaseMapper().update(null, wrapper);
    }

    @Override
    public Long push(Long memberId, Integer type, String title, String content, String bizId) {
        MallNotice notice = new MallNotice();
        notice.setMemberId(memberId);
        notice.setType(type);
        notice.setTitle(title);
        notice.setContent(content == null ? "" : content);
        notice.setBizId(bizId == null ? "" : bizId);
        notice.setReadStatus(0);
        save(notice);
        return notice.getId();
    }
}
