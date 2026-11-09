package com.mall.ai.dto;

import lombok.Data;

/** 建会话入参：来源用于把「正在看的商品/订单」带进上下文 */
@Data
public class ChatSessionCreateDTO {
    /** pc / h5，缺省按 pc */
    private String channel;
    /** goods_detail / order_detail / home / float */
    private String source;
    /** 进入时正在浏览的商品 ID（可空，用于售前首句个性化） */
    private Long goodsId;
}
