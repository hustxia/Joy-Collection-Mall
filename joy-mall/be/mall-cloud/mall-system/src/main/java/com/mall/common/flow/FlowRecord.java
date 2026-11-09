package com.mall.common.flow;

import java.time.LocalDateTime;

/** 审批流水（双引擎投影目标：builtin 直读 sys_flow_task，activiti 由 ACT_HI_* 投影） */
public class FlowRecord {
    private Long id;
    private String flowCode;
    private String bizTable;
    private Long bizId;
    private String fromStatus;
    private String toStatus;
    private String action;
    private String comment;
    private Long operUserId;
    private String operName;
    private LocalDateTime operTime;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getFlowCode() { return flowCode; }
    public void setFlowCode(String v) { this.flowCode = v; }
    public String getBizTable() { return bizTable; }
    public void setBizTable(String v) { this.bizTable = v; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long v) { this.bizId = v; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String v) { this.fromStatus = v; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String v) { this.toStatus = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public String getComment() { return comment; }
    public void setComment(String v) { this.comment = v; }
    public Long getOperUserId() { return operUserId; }
    public void setOperUserId(Long v) { this.operUserId = v; }
    public String getOperName() { return operName; }
    public void setOperName(String v) { this.operName = v; }
    public LocalDateTime getOperTime() { return operTime; }
    public void setOperTime(LocalDateTime v) { this.operTime = v; }
}
