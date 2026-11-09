package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员等级（V13）：成长值阈值决定等级，等级决定折扣率与每月包邮次数。
 *
 * <p>设计取舍：等级是<b>配置</b>不是<b>会员字段</b>，会员只存 {@code level_id} + {@code growth}。
 * 这样运营调整阈值（比如金卡从 2000 调到 1500）后，会员下次成长值变动就会自动重算，
 * 不需要批量刷数据；历史等级变化另有 {@link MallMemberLevelLog} 留痕。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_member_level")
public class MallMemberLevel extends BaseEntity {

    /** 等级名称 */
    private String name;

    /** 等级序号：1 最低 */
    private Integer levelNo;

    /** 成长值阈值 */
    private Long growthThreshold;

    /** 折扣率：100=不打折，95=9.5折 */
    private Integer discountRate;

    /** 每月包邮次数 */
    private Integer freeShippingCount;

    /** 权益说明 JSON 数组 */
    private String benefitsJson;

    /** 等级图标 */
    private String icon;

    /** 状态：1启用0停用 */
    private Integer status;

    /** 排序 */
    private Integer sort;
}
