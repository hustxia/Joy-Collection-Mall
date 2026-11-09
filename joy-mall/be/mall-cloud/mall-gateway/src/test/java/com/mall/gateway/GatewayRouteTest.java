package com.mall.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6 T-02 网关上下文与路由表回归。
 *
 * <p>网关是唯一「不可用就全站不可用」的组件，但它的配置是纯 yml，改错了编译也不报错。
 * 这里把路由表当契约断言：路由条数、目标端口、以及最重要的
 * <b>「/inner/** 绝不出现在网关路由里」</b>——内部端点一旦被网关暴露，
 * 任何拿到会员 token 的人都能直接调 /inner/sku/deduct 扣库存。
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // 路由表断言不需要真实库：Hikari 不在启动时校验连接（网关鉴权的库不可用路径本身 fail-open）
        properties = {"spring.main.web-application-type=reactive",
                "spring.datasource.hikari.initialization-fail-timeout=-1"})
class GatewayRouteTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    private List<RouteDefinition> routes() {
        return routeDefinitionLocator.getRouteDefinitions().collectList().block();
    }

    @Test
    void routesCoverEveryDomain() {
        List<RouteDefinition> routes = routes();
        // V14 客服/消息 3 条 → 17；V16 AI 客服（shop/admin）2 条 → 19
        assertEquals(19, routes.size(), "路由条数（C 端 9 + 后台 9 + 系统 1 + /inner 兜底拦截 1）不应随意变动");

        Set<String> uris = routes.stream().map(r -> r.getUri().toString()).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(
                "http://localhost:8201", "http://localhost:8202", "http://localhost:8203",
                "http://localhost:8204", "http://localhost:8205", "http://localhost:8206",
                "http://localhost:8207", "http://localhost:8208", "http://localhost:8209",
                "http://localhost:8210"), uris,
                "每个业务域都要有路由，且只指向 8201~8210");

        Set<String> ids = routes.stream().map(RouteDefinition::getId).collect(Collectors.toSet());
        assertTrue(ids.containsAll(Set.of("route-order-shop", "route-refund-shop", "route-marketing-shop",
                "route-goods-shop", "route-member-shop", "route-order-admin", "route-refund-admin",
                "route-marketing-admin", "route-member-admin", "route-goods-admin", "route-system",
                "route-merchant-shop", "route-merchant-admin",
                // V14：客服工单 / 消息台账
                "route-support-shop", "route-support-admin", "route-message-admin",
                // V16：在线 AI 客服
                "route-ai-shop", "route-ai-admin")), "路由 id 缺失：" + ids);
    }

    @Test
    void innerEndpointsAreNotExposed() {
        // route-inner-block 是有意的兜底路由：让 /inner 请求进入过滤器链，由 GatewayAuthFilter 统一 403，
        // uri 不会被真正转发。除它之外，任何业务路由都不得匹配 /inner。
        List<RouteDefinition> routes = routes();
        assertTrue(routes.stream().anyMatch(r -> "route-inner-block".equals(r.getId())),
                "缺少 /inner 兜底拦截路由 route-inner-block");
        for (RouteDefinition route : routes()) {
            if ("route-inner-block".equals(route.getId())) {
                continue;
            }
            String predicates = route.getPredicates().toString();
            assertFalse(predicates.contains("/inner"), "/inner 不得出现在网关路由：" + route.getId() + " -> " + predicates);
        }
    }

    @Test
    void shopAndAdminPathsRoutedToExpectedService() {
        assertRoute("route-goods-shop", "/shop/goods/**");
        assertRoute("route-order-shop", "/shop/order/**");
        assertRoute("route-member-shop", "/shop/cart/**");
        assertRoute("route-refund-shop", "/shop/refund/**");
        assertRoute("route-marketing-shop", "/shop/coupon/**");
        assertRoute("route-order-admin", "/mall_order/**");
        assertRoute("route-merchant-shop", "/shop/merchant/**");
        assertRoute("route-merchant-admin", "/mall_merchant/**");
        // V11：商家后台走独立前缀 /merchant-admin/**（令牌与平台不通用，靠网关路由 + AuthContextFilter 三方分流）
        assertRoute("route-merchant-admin", "/merchant-admin/**");
        // V13/V14 新增 C 端入口：促销试算、搜索、工单
        assertRoute("route-marketing-shop", "/shop/promotion/**");
        assertRoute("route-goods-shop", "/shop/search/**");
        assertRoute("route-support-shop", "/shop/ticket/**");
        assertRoute("route-member-shop", "/shop/member/**");
        assertRoute("route-system", "/system/**");
    }

    private void assertRoute(String routeId, String expectedPath) {
        RouteDefinition route = routes().stream().filter(r -> r.getId().equals(routeId)).findFirst()
                .orElseThrow(() -> new AssertionError("缺少路由：" + routeId));
        String predicates = route.getPredicates().toString();
        assertTrue(predicates.contains(expectedPath),
                routeId + " 应匹配 " + expectedPath + "，实际 " + predicates);
    }
}
