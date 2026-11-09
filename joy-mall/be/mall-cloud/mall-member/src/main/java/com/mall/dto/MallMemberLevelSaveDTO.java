package com.mall.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 会员等级保存入参（V13）
 */
@Data
public class MallMemberLevelSaveDTO {

    private Long id;

    private String name;

    private Integer levelNo;

    /** 成长值阈值 */
    private Long growthThreshold;

    /** 折扣率：100=不打折，95=9.5折 */
    private Integer discountRate;

    /** 每月包邮次数 */
    private Integer freeShippingCount;

    /** 权益文案（一条一项，落库拼成 JSON 数组） */
    private List<String> benefits = new ArrayList<>();

    private String icon;

    private Integer status;

    private Integer sort;
}
