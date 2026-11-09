package com.mall.testsupport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Locale;

/**
 * 测试库名守卫：数据源 URL 指向的库名必须以 {@code _test} 结尾，否则直接让应用上下文启动失败。
 *
 * <p>为什么要守卫：测试会 {@code spring.sql.init} 建表并写入自建数据，一旦把
 * {@code TEST_DB_URL} 指到业务库 {@code yueji_mall}，测试数据就会污染业务数据、断言也会失真。
 * 这个守卫在数据源创建<b>之前</b>执行，宁可起不来也不能连错库。
 *
 * <p>仅 test scope 引入本模块，生产 jar 不含此类。
 */
public class TestDbNameGuard implements EnvironmentPostProcessor, Ordered {

    /** 允许的测试库后缀 */
    private static final String REQUIRED_SUFFIX = "_test";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = firstNonNull(
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("DB_URL"));
        if (url == null || url.isBlank()) {
            // 网关等服务无数据源，跳过
            return;
        }
        String database = databaseNameOf(url);
        if (database == null) {
            return;
        }
        if (!database.toLowerCase(Locale.ROOT).endsWith(REQUIRED_SUFFIX)) {
            throw new IllegalStateException("拒绝启动：测试只能连 *" + REQUIRED_SUFFIX + " 库，当前库名为「"
                    + database + "」。请检查 TEST_DB_URL / spring.datasource.url。");
        }
    }

    /** 从 jdbc:mysql://host:port/db?params 里取出库名 */
    static String databaseNameOf(String url) {
        int slash = url.indexOf('/', url.indexOf("//") + 2);
        if (slash < 0 || slash == url.length() - 1) {
            return null;
        }
        String rest = url.substring(slash + 1);
        int query = rest.indexOf('?');
        if (query >= 0) {
            rest = rest.substring(0, query);
        }
        int semicolon = rest.indexOf(';');
        if (semicolon >= 0) {
            rest = rest.substring(0, semicolon);
        }
        return rest.isBlank() ? null : rest;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    /** 必须晚于 ConfigData 处理，才能读到 application-test.yml 里覆盖后的 url */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
