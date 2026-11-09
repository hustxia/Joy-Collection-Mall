package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallAgreement;

import java.util.List;

/**
 * 协议服务（V11 合规）：取当前生效版本、写同意留痕。
 */
public interface MallAgreementService extends IService<MallAgreement> {

    /** 当前生效版本：status=1 且已到生效时间，按生效时间倒序取第一条；没有返回 null */
    MallAgreement current(int type);

    /** 已发布的协议（用户协议 + 隐私政策） */
    List<MallAgreement> listPublished();

    /**
     * 写同意留痕：按当前生效版本落一条，返回写入的协议（无生效协议时返回 null 并跳过）。
     *
     * @param memberId 会员 ID
     * @param type     协议类型 1/2
     * @param ip       客户端 IP
     */
    MallAgreement record(Long memberId, int type, String ip);
}
