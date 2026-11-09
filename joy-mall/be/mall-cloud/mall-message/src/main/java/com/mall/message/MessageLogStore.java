package com.mall.message;

import com.mall.entity.MallMessageLog;

/**
 * 消息日志存储端口（V14）：调度器只依赖这个接口，纯单测可用内存实现。
 */
public interface MessageLogStore {

    /** 落库（生产实现回填主键与审计字段） */
    void save(MallMessageLog log);
}
