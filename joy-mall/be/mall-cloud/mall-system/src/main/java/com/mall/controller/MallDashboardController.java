package com.mall.controller;

import com.mall.common.result.Result;
import com.mall.service.MallDashboardService;
import com.mall.vo.MallDashboardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经营看板（V4 P0-2）
 * 后台运营端端点，GET 放行（PermissionChecker：未注册端点 GET 放行）。
 */
@RestController
@RequestMapping("/mall_dashboard")
public class MallDashboardController {

    @Autowired
    private MallDashboardService mallDashboardService;

    @GetMapping("/overview")
    public Result<MallDashboardVO> overview(@RequestParam(value = "range", required = false) String range) {
        return Result.ok(mallDashboardService.overview(range));
    }
}
