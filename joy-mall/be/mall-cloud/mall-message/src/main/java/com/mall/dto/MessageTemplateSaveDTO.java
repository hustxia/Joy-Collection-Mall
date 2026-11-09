package com.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台消息模板保存入参（V14）
 */
@Data
@Schema(description = "消息模板保存入参")
public class MessageTemplateSaveDTO {

    private Long id;

    @Schema(description = "场景码")
    private String sceneCode;

    @Schema(description = "场景名称")
    private String sceneName;

    @Schema(description = "标题模板，变量用 ${var}")
    private String titleTemplate;

    @Schema(description = "内容模板，变量用 ${var}")
    private String contentTemplate;

    @Schema(description = "主通道：sms/site")
    private String channel;

    @Schema(description = "状态：1启用0停用")
    private Integer status;

    private String remark;
}
