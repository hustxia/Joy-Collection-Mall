package com.mall.service;

import com.mall.vo.MallDashboardVO;

/**
 * 经营看板 Service（V4 P0-2）
 */
public interface MallDashboardService {

    /**
     * 聚合经营看板核心指标。
     *
     * @param range 统计范围：today（今日）/ 7d（近7天）/ 30d（近30天，默认）
     * @return 看板指标聚合结果
     */
    MallDashboardVO overview(String range);
}
