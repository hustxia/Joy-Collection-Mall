package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会员精简视图（会员域 → 订单域 内部传输）
 */
@Data
public class MemberBrief implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String nickname;
    private String avatar;
    private String phone;
}
