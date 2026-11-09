package com.mall.common.flow;

import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 内置简易流实现（V2.2 语义 + V6.1 待办扩展）。
 * 安全：table_name/status_col 走白名单正则——流程定义被改出注入串也进不了 SQL。
 */
@Service
@Slf4j
public class BuiltinFlowService implements FlowService {

    private static final Pattern IDENT = Pattern.compile("^[A-Za-z0-9_]{1,64}$");
    private static final String DRAFT = "0";
    private static final String PENDING = "1";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(String flowCode, Long bizId, String comment) {
        Def def = loadDef(flowCode);
        String current = currentStatus(def, bizId);
        if (!DRAFT.equals(current)) {
            throw new BusinessException(400, "只有草稿状态可以提交，当前状态：" + current);
        }
        String to = resolveSubmitTarget(def);
        move(def, bizId, current, to, "SUBMIT", comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String flowCode, Long bizId, String comment) {
        Def def = loadDef(flowCode);
        String current = currentStatus(def, bizId);
        Map<String, Object> node = findNode(def, current);
        if (node == null) {
            throw new BusinessException(400, "当前状态没有配置审批节点：" + current);
        }
        checkRole(node);
        String to = str(node.get("to"));
        if (!StringUtils.hasText(to)) {
            throw new BusinessException(400, "流程节点缺少目标状态，请检查流程定义");
        }
        move(def, bizId, current, to, "APPROVE", comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String flowCode, Long bizId, String comment) {
        Def def = loadDef(flowCode);
        String current = currentStatus(def, bizId);
        Map<String, Object> node = findNode(def, current);
        if (node == null) {
            throw new BusinessException(400, "当前状态没有配置审批节点：" + current);
        }
        checkRole(node);
        String to = str(node.get("rejectTo"));
        if (!StringUtils.hasText(to)) {
            to = DRAFT;
        }
        move(def, bizId, current, to, "REJECT", comment);
    }

    @Override
    public List<FlowRecord> records(String flowCode, Long bizId) {
        if (!StringUtils.hasText(flowCode) || bizId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT id, flow_code, biz_table, biz_id, from_status, to_status, action, comment, "
                        + "oper_user_id, oper_name, oper_time FROM sys_flow_task "
                        + "WHERE flow_code = ? AND biz_id = ? ORDER BY oper_time, id",
                (rs, i) -> {
                    FlowRecord vo = new FlowRecord();
                    vo.setId(rs.getLong("id"));
                    vo.setFlowCode(rs.getString("flow_code"));
                    vo.setBizTable(rs.getString("biz_table"));
                    vo.setBizId(rs.getLong("biz_id"));
                    vo.setFromStatus(rs.getString("from_status"));
                    vo.setToStatus(rs.getString("to_status"));
                    vo.setAction(rs.getString("action"));
                    vo.setComment(rs.getString("comment"));
                    long uid = rs.getLong("oper_user_id");
                    vo.setOperUserId(rs.wasNull() ? null : uid);
                    vo.setOperName(rs.getString("oper_name"));
                    if (rs.getTimestamp("oper_time") != null) {
                        vo.setOperTime(rs.getTimestamp("oper_time").toLocalDateTime());
                    }
                    return vo;
                }, flowCode, bizId);
    }

    @Override
    public Map<String, Object> state(String flowCode, Long bizId) {
        Def def = loadDef(flowCode);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flowCode", def.code);
        data.put("flowName", def.name);
        String current = bizId == null ? DRAFT : currentStatus(def, bizId);
        data.put("status", current);
        data.put("nodes", def.nodes);
        List<String> actions = new ArrayList<>();
        if (DRAFT.equals(current)) {
            actions.add("SUBMIT");
        } else {
            Map<String, Object> node = findNode(def, current);
            if (node != null && StringUtils.hasText(str(node.get("to"))) && roleMatched(node)) {
                actions.add("APPROVE");
                actions.add("REJECT");
            }
        }
        data.put("actions", actions);
        return data;
    }

    /** 我的待办：当前状态停在审批节点的单据（产物基座暂无角色体系，登录用户可见全部） */
    @Override
    public List<Map<String, Object>> todo() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Def def : allDefs()) {
            for (Map<String, Object> node : def.nodes) {
                String from = str(node.get("from"));
                if (!StringUtils.hasText(str(node.get("roleCode"))) || !StringUtils.hasText(from)) {
                    continue;
                }
                result.addAll(jdbcTemplate.queryForList(
                        "SELECT id AS bizId, '" + def.code + "' AS flowCode, '" + def.name
                                + "' AS flowName, '" + from + "' AS nodeName FROM " + def.tableName
                                + " WHERE " + def.statusCol + " = ? ORDER BY id DESC LIMIT 200", from));
            }
        }
        return result;
    }

    /** 我的已办：sys_flow_task 里我操作过的流水 */
    @Override
    public List<Map<String, Object>> done() {
        return jdbcTemplate.queryForList(
                "SELECT flow_code AS flowCode, biz_table AS bizTable, biz_id AS bizId, "
                        + "action, comment, oper_time AS operTime FROM sys_flow_task "
                        + "WHERE oper_user_id = ? ORDER BY oper_time DESC LIMIT 200",
                UserContext.getUserId());
    }

    @Override
    public Map<String, Object> taskDetail(String taskId) {
        // 内置流没有独立任务域：taskId 约定为 flowCode:bizId（前端从待办行拼出来）
        int idx = taskId == null ? -1 : taskId.indexOf(':');
        if (idx <= 0) {
            throw new BusinessException(400, "任务标识非法，期待 flowCode:bizId");
        }
        String flowCode = taskId.substring(0, idx);
        Long bizId = Long.valueOf(taskId.substring(idx + 1));
        Def def = loadDef(flowCode);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flowCode", flowCode);
        data.put("bizId", bizId);
        data.put("bizTable", def.tableName);
        data.put("state", state(flowCode, bizId));
        data.put("records", records(flowCode, bizId));
        return data;
    }

    private void move(Def def, Long bizId, String from, String to, String action, String comment) {
        int n = jdbcTemplate.update("UPDATE " + def.tableName + " SET " + def.statusCol
                + " = ? WHERE id = ?", to, bizId);
        if (n == 0) {
            throw new BusinessException(404, "单据不存在：" + bizId);
        }
        String operName = UserContext.getUsername();
        jdbcTemplate.update(
                "INSERT INTO sys_flow_task(flow_code, biz_table, biz_id, from_status, to_status, action, "
                        + "comment, oper_user_id, oper_name) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)",
                def.code, def.tableName, bizId, from, to, action, comment,
                UserContext.getUserId(), StringUtils.hasText(operName) ? operName : "system");
    }

    private String resolveSubmitTarget(Def def) {
        Map<String, Object> node = findNode(def, DRAFT);
        if (node != null && StringUtils.hasText(str(node.get("to")))) {
            return str(node.get("to"));
        }
        return PENDING;
    }

    private Map<String, Object> findNode(Def def, String from) {
        for (Map<String, Object> node : def.nodes) {
            if (from.equals(str(node.get("from")))) {
                return node;
            }
        }
        return null;
    }

    private void checkRole(Map<String, Object> node) {
        // TODO 产物角色体系引入后按 roleCode 校验（当前基座无角色，恒放行）
    }

    private boolean roleMatched(Map<String, Object> node) {
        return true;
    }

    private String currentStatus(Def def, Long bizId) {
        List<String> list = jdbcTemplate.queryForList(
                "SELECT " + def.statusCol + " FROM " + def.tableName + " WHERE id = ?",
                String.class, bizId);
        if (list.isEmpty()) {
            throw new BusinessException(404, "单据不存在：" + bizId);
        }
        return list.get(0) == null ? DRAFT : list.get(0);
    }

    private List<Def> allDefs() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT flow_code, flow_name, table_name, status_col, nodes FROM sys_flow_def "
                        + "WHERE deleted = 0 AND enabled = 1");
        List<Def> defs = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            try {
                defs.add(toDef(r));
            } catch (Exception e) {
                // 单条定义坏了不拖垮整个待办列表；带 flow_code 留痕便于修复脏数据
                log.warn("流程定义解析失败已跳过，flow_code={}：{}", r.get("flow_code"), e.getMessage());
            }
        }
        return defs;
    }

    private Def loadDef(String flowCode) {
        if (!StringUtils.hasText(flowCode)) {
            throw new BusinessException(400, "流程编码不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT flow_code, flow_name, table_name, status_col, nodes, enabled "
                        + "FROM sys_flow_def WHERE flow_code = ? AND deleted = 0", flowCode.trim());
        if (rows.isEmpty()) {
            throw new BusinessException(404, "流程不存在：" + flowCode);
        }
        Def def = toDef(rows.get(0));
        Object enabled = rows.get(0).get("enabled");
        if (enabled != null && ((Number) enabled).intValue() != 1) {
            throw new BusinessException(400, "流程已停用：" + flowCode);
        }
        return def;
    }

    private Def toDef(Map<String, Object> r) {
        Def def = new Def();
        def.code = str(r.get("flow_code"));
        def.name = str(r.get("flow_name"));
        def.tableName = str(r.get("table_name"));
        def.statusCol = str(r.get("status_col"));
        if (!IDENT.matcher(def.tableName).matches()) {
            throw new BusinessException(400, "流程绑定的表名非法：" + def.tableName);
        }
        if (!IDENT.matcher(def.statusCol).matches()) {
            throw new BusinessException(400, "流程绑定的状态列非法：" + def.statusCol);
        }
        def.nodes = parseNodes(str(r.get("nodes")));
        return def;
    }

    private List<Map<String, Object>> parseNodes(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception e) {
            throw new BusinessException(400, "流程节点 JSON 解析失败：" + e.getMessage());
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static final class Def {
        String code;
        String name;
        String tableName;
        String statusCol;
        List<Map<String, Object>> nodes = List.of();
    }
}
