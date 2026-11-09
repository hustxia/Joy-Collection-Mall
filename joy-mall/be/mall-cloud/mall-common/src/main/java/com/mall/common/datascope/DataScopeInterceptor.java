package com.mall.common.datascope;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import com.mall.common.context.UserContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据权限拦截器：对已登记的表自动追加过滤条件。
 *
 * <p>规则由 {@link DataScopeRegistry} 在启动时登记，不在这里查库——
 * 拦截器里查库会再次触发拦截器，形成死循环。
 */
public class DataScopeInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataScopeInterceptor.class);

    private static final Map<String, Rule> RULES = new ConcurrentHashMap<>();

    public record Rule(String column, String scope) {
    }

    /**
     * 登记一张表的数据权限列。scope 取 self（按本人）或 dept（按本部门）。
     */
    public static void register(String tableName, String column, String scope) {
        if (tableName == null || column == null || scope == null) {
            return;
        }
        RULES.put(tableName.replace("`", "").toLowerCase(), new Rule(column, scope.toLowerCase()));
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        rewrite(boundSql);
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer timeout) {
        rewrite(sh.getBoundSql());
    }

    private void rewrite(BoundSql boundSql) {
        if (RULES.isEmpty()) {
            return;
        }
        if ("all".equals(UserContext.getDataScope())) {
            return;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 无登录态（定时任务、内部调用）不过滤，否则会静默查不到任何数据
            return;
        }
        String sql = boundSql.getSql();
        if (sql == null || sql.isBlank()) {
            return;
        }
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            boolean changed = false;
            if (stmt instanceof Select select && select.getSelectBody() instanceof PlainSelect ps
                    && ps.getFromItem() instanceof Table table) {
                Expression cond = condition(table);
                if (cond != null) {
                    ps.setWhere(and(ps.getWhere(), cond));
                    changed = true;
                }
            } else if (stmt instanceof Update update) {
                Expression cond = condition(update.getTable());
                if (cond != null) {
                    update.setWhere(and(update.getWhere(), cond));
                    changed = true;
                }
            } else if (stmt instanceof Delete delete) {
                Expression cond = condition(delete.getTable());
                if (cond != null) {
                    delete.setWhere(and(delete.getWhere(), cond));
                    changed = true;
                }
            }
            if (changed) {
                PluginUtils.mpBoundSql(boundSql).sql(stmt.toString());
            }
        } catch (Exception e) {
            // 解析失败不能阻断业务：放行原 SQL 并告警，避免一条怪 SQL 让整个接口 500
            log.warn("数据权限改写失败，已放行原 SQL：{}", e.getMessage());
        }
    }

    private Expression condition(Table table) {
        if (table == null || table.getName() == null) {
            return null;
        }
        Rule rule = RULES.get(table.getName().replace("`", "").toLowerCase());
        if (rule == null) {
            return null;
        }
        Long value = "dept".equals(rule.scope()) ? UserContext.getDeptId() : UserContext.getUserId();
        if (value == null) {
            // 部门信息缺失时降级按本人过滤，宁严不松
            value = UserContext.getUserId();
        }
        if (value == null) {
            return null;
        }
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(table, rule.column()));
        eq.setRightExpression(new LongValue(value));
        return eq;
    }

    private Expression and(Expression where, Expression cond) {
        return where == null ? cond : new AndExpression(where, cond);
    }
}
