package com.mall.controller;

import com.mall.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统字典端点：GET /system/dict/data/type/{dictType}。
 *
 * <p>生成页面的状态列会请求该端点做枚举翻译（如 sys_status / mall_mall_order_pay_type），
 * 数据存 sys_dict_data；查无数据时返回空数组（前端降级为显示原值，不报错）。
 */
@RestController
public class SysDictController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/system/dict/data/type/{dictType}")
    public Result<List<Map<String, Object>>> byType(@PathVariable("dictType") String dictType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT label, value FROM sys_dict_data "
                        + "WHERE dict_type = ? AND deleted = 0 ORDER BY sort, id",
                dictType);
        // 只回 label/value 两个键，避免把内部列名漏给前端
        List<Map<String, Object>> data = new java.util.ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", row.get("label"));
            item.put("value", row.get("value"));
            data.add(item);
        }
        return Result.ok(data);
    }
}
