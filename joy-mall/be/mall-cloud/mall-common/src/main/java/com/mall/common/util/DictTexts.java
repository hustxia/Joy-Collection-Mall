package com.mall.common.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 导出/展示用的字典翻译：dict_code + value -> label。
 * 由 codegen-server 脚手架生成；结果按 code 缓存，字典变更后重启生效。
 * 依赖目标库存在 sys_dict_data(dict_code, dict_value, dict_label, deleted) 列结构，
 * 表不存在或查询异常时 label 原样返回值，且不缓存（下次调用重试，不会失效到重启）。
 */
@Component
public class DictTexts {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    public String label(String dictCode, String value) {
        if (dictCode == null || value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        Map<String, String> mapping = cache.get(dictCode);
        if (mapping == null) {
            mapping = loadDict(dictCode);
            // 查询失败返回 null 不缓存：DB 抖动后下次导出自动重试，不至于失效到重启
            if (mapping != null) {
                cache.put(dictCode, mapping);
            }
        }
        return mapping == null ? value : mapping.getOrDefault(value, value);
    }

    private Map<String, String> loadDict(String dictCode) {
        Map<String, String> m = new HashMap<>();
        try {
            // 用 queryForList 而不是 query(String, lambda, args)：后者同时匹配
            // ResultSetExtractor 与 RowCallbackHandler 两个重载，表达式 lambda 会编译歧义
            for (Map<String, Object> row : jdbcTemplate.queryForList(
                    "SELECT dict_value, dict_label FROM sys_dict_data WHERE dict_code = ? AND deleted = 0",
                    dictCode)) {
                m.put(String.valueOf(row.get("dict_value")), String.valueOf(row.get("dict_label")));
            }
        } catch (Exception e) {
            return null;
        }
        return m;
    }
}
