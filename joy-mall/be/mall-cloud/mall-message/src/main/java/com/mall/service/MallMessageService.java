package com.mall.service;

import com.mall.dto.MessageSendDTO;
import com.mall.dto.MessageTemplateSaveDTO;

import java.util.Map;

/**
 * 消息服务（V14，REQ-P1-09）：模板渲染 + 通道调度 + 日志查询。
 */
public interface MallMessageService {

    /** 按场景码发送（内部接口与后台测试发送共用） */
    Map<String, Object> send(MessageSendDTO dto);

    /** 发送日志分页 */
    Map<String, Object> pageLog(Integer status, Integer degraded, String sceneCode, long pageNum, long pageSize);

    /** 模板分页 */
    Map<String, Object> pageTemplate(String sceneCode, long pageNum, long pageSize);

    /** 模板新增/修改（场景码唯一） */
    void saveTemplate(MessageTemplateSaveDTO dto);
}
