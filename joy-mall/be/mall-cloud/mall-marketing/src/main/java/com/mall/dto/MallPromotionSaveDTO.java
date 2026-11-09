package com.mall.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 促销活动保存入参（V13）：主体 + 全量作用范围（先删后插，以本次提交为准）。
 *
 * <p>规则字段既接受结构化字段（threshold/discount/discountRate/bundleQty/bundlePrice/addOnPrice，
 * 后台表单直接填），也接受 ruleJson（兼容脚本/种子）。两者都传时结构化字段优先。
 */
@Data
public class MallPromotionSaveDTO {

    /** 主键：空=新增 */
    private Long id;

    private String name;

    /** 1满减2满折3限时折扣4第二件半价5套装6加价购 */
    private Integer type;

    /** 1全场2类目3商品4店铺 */
    private Integer scopeType;

    /** 门槛（分） */
    private Long threshold;

    /** 满减金额（分） */
    private Long discount;

    /** 折扣率：90=9折 */
    private Integer discountRate;

    /** 套装件数 */
    private Integer bundleQty;

    /** 套装打包价（分） */
    private Long bundlePrice;

    /** 加价购换购价（分） */
    private Long addOnPrice;

    /** 兼容入口：结构化字段全空时用这个 JSON */
    private String ruleJson;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 优先级：越小越优先 */
    private Integer priority;

    /** 是否可叠加：1是0否 */
    private Integer stackable;

    private Integer status;

    private String remark;

    /** 作用范围对象ID（类目/商品/店铺），scopeType=1 时可空 */
    private List<Long> refIds = new ArrayList<>();
}
