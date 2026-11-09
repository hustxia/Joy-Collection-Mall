package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 平台协议（V11 合规）：用户协议 / 隐私政策，按版本发布。
 *
 * <p>「当前生效版本」= 该类型下 status=1 且 effective_time 已到，按 effective_time 倒序取第一条。
 * 版本不可改，只能发新版——用户同意的是具体版本，改正文会让历史留痕失去意义。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_agreement")
public class MallAgreement extends BaseEntity {

    /** 用户协议 */
    public static final int TYPE_USER = 1;
    /** 隐私政策 */
    public static final int TYPE_PRIVACY = 2;

    /** 协议类型：1用户协议2隐私政策 */
    private Integer type;

    /** 版本号 */
    private String version;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 生效时间，空=立即生效 */
    private LocalDateTime effectiveTime;

    /** 状态：1发布0下线 */
    private Integer status;

    /** 备注 */
    private String remark;

    public static String typeText(Integer type) {
        if (type == null) {
            return "-";
        }
        return switch (type) {
            case TYPE_USER -> "用户协议";
            case TYPE_PRIVACY -> "隐私政策";
            default -> String.valueOf(type);
        };
    }
}
