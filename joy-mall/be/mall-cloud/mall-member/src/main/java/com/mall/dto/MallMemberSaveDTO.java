package com.mall.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会员保存 DTO（新增/修改）
 */
@Data
public class MallMemberSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;
    @Size(max = 500, message = "头像URL长度不能超过500")
    private String avatar;
    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;
    @Size(max = 64, message = "微信openid长度不能超过64")
    private String openid;
    @Size(max = 64, message = "微信unionid长度不能超过64")
    private String unionid;
    @NotNull(message = "性别不能为空")
    private Integer gender;
    @NotNull(message = "注册来源不能为空")
    private Integer source;
    @NotNull(message = "积分不能为空")
    private Integer points;
    @NotNull(message = "状态不能为空")
    private Integer status;
    private LocalDateTime lastLoginTime;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}