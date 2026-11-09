package com.mall.common.flow;

import java.util.List;
import java.util.Map;

/**
 * V6.1 审批流统一契约：内置简易流（BuiltinFlowService）与 Activiti 引擎（ActivitiFlowService）
 * 二选一装配，controller.ftl 的四端点与本类的 todo/done/taskDetail 契约保持不变。
 */
public interface FlowService {

    void submit(String flowCode, Long bizId, String comment);

    void approve(String flowCode, Long bizId, String comment);

    void reject(String flowCode, Long bizId, String comment);

    List<FlowRecord> records(String flowCode, Long bizId);

    Map<String, Object> state(String flowCode, Long bizId);

    /** 我的待办：引擎各自实现（builtin=当前状态+角色匹配；activiti=TaskService 候选查询） */
    List<Map<String, Object>> todo();

    /** 我的已办 */
    List<Map<String, Object>> done();

    /** 任务详情：任务 + 业务单据 + 审批记录聚合 */
    Map<String, Object> taskDetail(String taskId);
}
