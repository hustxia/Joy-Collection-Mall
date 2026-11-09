package com.mall.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 运费模板保存入参（V12）：一次提交模板主体 + 全量地区规则（先删后插，规则以本次提交为准）。
 */
@Data
public class FreightTemplateSaveDTO {

    /** 主键：空=新增，非空=修改 */
    private Long id;

    private String name;

    /** 1按件2按重量3满额包邮 */
    private Integer chargeType;

    /** 1全局默认 */
    private Integer isDefault;

    private Integer status;

    private String remark;

    private List<Rule> rules = new ArrayList<>();

    @Data
    public static class Rule {
        private Long id;
        /** 逗号分隔省份名；空=模板默认规则 */
        private String regionCodes;
        private Integer firstUnit;
        private Long firstFee;
        private Integer extraUnit;
        private Long extraFee;
        private Long freeThreshold;
        private Integer sort;
    }
}
