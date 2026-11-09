package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员等级变更日志（V13）：升级/降级都写一行，含变更前后等级与原因。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_member_level_log")
public class MallMemberLevelLog extends BaseEntity {

    private Long memberId;

    private Long beforeLevelId;

    private Long afterLevelId;

    private String beforeLevelName;

    private String afterLevelName;

    /** 变更时成长值 */
    private Long growth;

    /** 原因：order消费 / signin签到 / review评价 / manual手动 */
    private String reason;

    private String remark;
}
