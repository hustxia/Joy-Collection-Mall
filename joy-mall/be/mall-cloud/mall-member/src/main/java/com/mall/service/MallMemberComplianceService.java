package com.mall.service;

import java.util.Map;

/**
 * 会员合规服务（V11）：注销冷静期与个人信息导出。
 *
 * <p>注销用「状态位 + 到期执行」而不是物理删除：订单、售后、资金流水都要留着可查
 * （{@code member_id} 不变），只在到期时把 PII 匿名化。
 */
public interface MallMemberComplianceService {

    /** 冷静期天数 */
    int COOLING_DAYS = 7;

    /** 注销状态：正常 */
    int STATUS_NORMAL = 0;
    /** 注销状态：冷静期 */
    int STATUS_COOLING = 1;
    /** 注销状态：已注销 */
    int STATUS_CANCELLED = 2;

    /** 申请注销：进入 7 天冷静期 */
    Map<String, Object> applyCancel(Long memberId);

    /** 撤销注销：冷静期内有效，恢复为正常账号 */
    Map<String, Object> revokeCancel(Long memberId);

    /** 当前注销状态（含剩余天数） */
    Map<String, Object> cancelStatus(Long memberId);

    /**
     * 惰性执行：若该会员处于冷静期且已到期，立即脱敏。
     *
     * @return true=本次执行了注销
     */
    boolean executeIfDue(Long memberId);

    /** 扫描所有到期会员并执行注销（定时任务/运维脚本入口），返回处理条数 */
    int executeDue();

    /** 个人信息导出（JSON 结构）：只含本人资料 / 地址 / 订单 / 优惠券 */
    Map<String, Object> export(Long memberId);

    /** 个人信息导出（CSV 文本，带 UTF-8 BOM） */
    String exportCsv(Long memberId);
}
