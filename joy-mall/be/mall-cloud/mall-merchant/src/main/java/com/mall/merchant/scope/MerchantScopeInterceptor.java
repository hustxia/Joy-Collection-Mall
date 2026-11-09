package com.mall.merchant.scope;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.mall.common.context.MerchantContext;
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
 * 商家数据隔离拦截器（V11）。
 *
 * <p>只对标注了 {@link MerchantScoped} 的 Mapper 生效：解析 SQL 后给主表追加
 * {@code merchant_id = 当前商家}，覆盖查询 / 更新 / 删除三条路径。
 *
 * <p>三条「不改写」的边界，都是有意的：
 * <ul>
 *   <li>无商家上下文（定时任务、Feign 内部调用、平台后台）→ 放行，否则内部任务会静默查不到数据；</li>
 *   <li>SQL 解析失败 → 放行原 SQL 并告警，避免一条怪 SQL 让整个商家后台 500；</li>
 *   <li>INSERT → 不改写，由业务显式写入 merchant_id（来源必须是上下文，不能信前端）。</li>
 * </ul>
 *
 * <p>更新/删除为什么走 {@code beforePrepare}：MyBatis-Plus 的 {@code beforeUpdate} 只给
 * MappedStatement，把它重新 getBoundSql 出来的副本改写掉，执行时用的却是另一个实例；
 * 真正生效的那份 SQL 只能从 StatementHandler 上拿到，因此用 ThreadLocal 把「本次语句
 * 是否商家隔离」带过去。
 *
 * <p>这是纵深防御的第二道：第一道是 Service 层的归属校验（越权直接 403 并留痕），
 * 拦截器保证「就算漏了校验也查不到别人家的行」。
 */
public class MerchantScopeInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MerchantScopeInterceptor.class);

    /** Mapper 类 → 注解（启动后不变，缓存避免每条 SQL 反射） */
    private final Map<String, MerchantScoped> annotationCache = new ConcurrentHashMap<>();

    private final Map<String, Boolean> negativeCache = new ConcurrentHashMap<>();

    /** 仅用于把「写语句的隔离参数」从 beforeUpdate 传到 beforePrepare */
    private final ThreadLocal<Pending> pendingWrite = new ThreadLocal<>();

    private record Pending(String column, long merchantId) {
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        pendingWrite.remove();
        MerchantScoped scoped = scopedOf(ms);
        if (scoped == null) {
            return;
        }
        Long merchantId = MerchantContext.get();
        if (merchantId == null) {
            return;
        }
        rewrite(boundSql, scoped.column(), merchantId);
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) {
        MerchantScoped scoped = scopedOf(ms);
        Long merchantId = MerchantContext.get();
        if (scoped == null || merchantId == null) {
            pendingWrite.remove();
            return;
        }
        pendingWrite.set(new Pending(scoped.column(), merchantId));
        // 兜底再改一次副本：不同执行器路径下（批处理等）可能不经 StatementHandler
        rewrite(ms.getBoundSql(parameter), scoped.column(), merchantId);
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer timeout) {
        Pending pending = pendingWrite.get();
        if (pending == null) {
            return;
        }
        rewrite(sh.getBoundSql(), pending.column(), pending.merchantId());
    }

    private void rewrite(BoundSql boundSql, String column, long merchantId) {
        if (boundSql == null) {
            return;
        }
        String sql = boundSql.getSql();
        if (sql == null || sql.isBlank()) {
            return;
        }
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            String rewritten = rewriteStatement(stmt, column, merchantId);
            if (rewritten != null) {
                PluginUtils.mpBoundSql(boundSql).sql(rewritten);
            }
        } catch (Exception e) {
            log.warn("商家数据隔离改写失败，已放行原 SQL：{}", e.getMessage());
        }
    }

    /** @return 改写后的 SQL；null 表示不需要改写（INSERT 或结构不认识） */
    String rewriteStatement(Statement stmt, String column, long merchantId) {
        if (stmt instanceof Select select && select.getSelectBody() instanceof PlainSelect ps
                && ps.getFromItem() instanceof Table table) {
            Expression cond = condition(table, column, merchantId);
            if (cond == null) {
                return null;
            }
            ps.setWhere(and(ps.getWhere(), cond));
            return stmt.toString();
        }
        if (stmt instanceof Update update && update.getTable() != null) {
            Expression cond = condition(update.getTable(), column, merchantId);
            if (cond == null) {
                return null;
            }
            update.setWhere(and(update.getWhere(), cond));
            return stmt.toString();
        }
        if (stmt instanceof Delete delete && delete.getTable() != null) {
            Expression cond = condition(delete.getTable(), column, merchantId);
            if (cond == null) {
                return null;
            }
            delete.setWhere(and(delete.getWhere(), cond));
            return stmt.toString();
        }
        return null;
    }

    private MerchantScoped scopedOf(MappedStatement ms) {
        String id = ms.getId();
        int idx = id.lastIndexOf('.');
        if (idx <= 0) {
            return null;
        }
        String className = id.substring(0, idx);
        MerchantScoped cached = annotationCache.get(className);
        if (cached != null) {
            return cached;
        }
        if (Boolean.TRUE.equals(negativeCache.get(className))) {
            return null;
        }
        try {
            Class<?> mapperClass = Class.forName(className, false,
                    Thread.currentThread().getContextClassLoader());
            MerchantScoped annotation = mapperClass.getAnnotation(MerchantScoped.class);
            if (annotation == null) {
                negativeCache.put(className, Boolean.TRUE);
                return null;
            }
            annotationCache.put(className, annotation);
            return annotation;
        } catch (ClassNotFoundException e) {
            negativeCache.put(className, Boolean.TRUE);
            return null;
        }
    }

    private Expression condition(Table table, String column, long merchantId) {
        if (table == null || table.getName() == null) {
            return null;
        }
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(table, column));
        eq.setRightExpression(new LongValue(merchantId));
        return eq;
    }

    private Expression and(Expression where, Expression cond) {
        return where == null ? cond : new AndExpression(where, cond);
    }
}
