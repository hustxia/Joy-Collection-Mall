package com.mall.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 拼团（V8）：状态机 0拼团中 → 1已成团 / 2已失败。
 * 过期判定走懒加载：读取/参团时发现 status=0 且 expire_time 已过 → 置 2。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_group_team")
public class MallGroupTeam extends BaseEntity {

    /** 拼团活动ID */
    private Long activityId;

    /** 团长会员ID */
    private Long leaderId;

    /** 当前人数（含团长） */
    private Integer currentSize;

    /** 状态：0拼团中1已成团2已失败 */
    private Integer status;

    /** 成团截止时间 */
    private LocalDateTime expireTime;
}
