package com.mall.ai.dto;

import lombok.Data;

@Data
public class FaqSaveDTO {
    /** 编辑时传 */
    private Long id;
    private String code;
    private String question;
    private String answer;
    private String category;
    private String synonyms;
    private Integer status;
}
