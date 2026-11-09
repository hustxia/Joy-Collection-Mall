package com.mall.system;

import com.mall.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 操作日志。只记写操作，查询不入库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oper_log")
public class OpeLog extends BaseEntity {

    private String title;

    private String operType;

    private String method;

    private String requestUri;

    private String requestMethod;

    private String operParam;

    private String jsonResult;

    /**
     * 1 成功 0 失败
     */
    private Integer status;

    private String errorMsg;

    private Long operUserId;

    private String operName;

    private String operIp;

    /**
     * 省份（由 IP 解析，内网/本机时记「内网」「本机」）
     */
    private String province;

    /**
     * 城市（由 IP 解析）
     */
    private String city;

    private Long costMs;

    private LocalDateTime operTime;
}
