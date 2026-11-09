package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会员协议同意留痕（V11 合规）。
 *
 * <p>每次注册/重新同意都追加一条，不做更新：监管口径要的是「谁在什么时候同意了哪一版」，
 * 可追溯性比存储成本重要。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_member_agreement_log")
public class MallMemberAgreementLog extends BaseEntity {

    /** 会员ID */
    private Long memberId;

    /** 协议ID */
    private Long agreementId;

    /** 协议类型（冗余，便于按类型统计） */
    private Integer agreementType;

    /** 同意的版本 */
    private String version;

    /** 同意时间 */
    private LocalDateTime agreeTime;

    /** 客户端 IP */
    private String ip;
}
