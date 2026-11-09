package com.mall.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 登录日志只读查询（V6.4 P0-4）：谁在什么时候从哪里登录、成败与原因。
 * 写入端在 AuthController；本控制器只读，端点纳入权限校验 fail-closed 缺省（GET/POST/page 读放行）。
 */
@RestController
@RequestMapping("/loginLog")
public class LoginLogController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/page")
    public Map<String, Object> page(@RequestBody(required = false) Map<String, Object> body) {
        int pageNum = body == null || body.get("pageNum") == null ? 1 : ((Number) body.get("pageNum")).intValue();
        int pageSize = body == null || body.get("pageSize") == null ? 10 : ((Number) body.get("pageSize")).intValue();
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        var args = new java.util.ArrayList<Object>();
        if (body != null && body.get("username") != null && !String.valueOf(body.get("username")).isBlank()) {
            where.append(" AND username LIKE ?");
            args.add("%" + body.get("username") + "%");
        }
        if (body != null && body.get("success") != null) {
            where.append(" AND success = ?");
            args.add(((Number) body.get("success")).intValue());
        }
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_login_log" + where, Integer.class, args.toArray());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT id, username, ip, region, success, fail_reason AS failReason, create_time AS createTime "
                        + "FROM sys_login_log" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                args.toArray(), pageSize, (pageNum - 1) * pageSize);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("records", records);
        data.put("total", total == null ? 0 : total);
        data.put("current", pageNum);
        data.put("size", pageSize);
        return Map.of("code", 200, "msg", "success", "data", data);
    }
}
