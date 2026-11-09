package com.mall.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.auth.TokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 服务级集成测试基类（V6 T-01/T-02）：连独立测试库 {@code yueji_mall_test}，走 MockMvc 全栈
 * （含 AuthContextFilter 与权限点校验），夹具自建自清。
 *
 * <p>跨域数据一律<i>直连测试库造夹具</i>（例如订单测试自己插 mall_goods/mall_sku 行），
 * 不启动别的服务——所以用例只依赖「本服务 + 库」，可离线跑 {@code mvn test}；
 * 真正的跨服务协作由 Feign 契约测试（T-03）与端到端冒烟（T-04）覆盖。
 *
 * <p>每个用例开始前先做库名校验：连错库立刻失败，绝不写业务库。
 */
public abstract class MallIntegrationTestBase {

    /** 夹具标记：便于人工排查遗留数据 */
    protected static final String TEST_MARK = "IT-V6-";

    private static final ObjectMapper OM = new ObjectMapper();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TokenStore tokenStore;

    /** 本用例创建的会员/商品/SKU/地址，供 tearDownFixtures 清理 */
    private final Set<Long> memberIds = new LinkedHashSet<>();
    private final Set<Long> goodsIds = new LinkedHashSet<>();
    private final Set<Long> skuIds = new LinkedHashSet<>();
    private final Set<Long> addressIds = new LinkedHashSet<>();
    private final Set<Long> categoryIds = new LinkedHashSet<>();
    private final Set<String> tokens = new LinkedHashSet<>();

    @BeforeEach
    void requireTestDatabase() {
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertTrue(database != null && database.endsWith("_test"),
                "测试必须在 * _test 库上运行，当前库：" + database);
    }

    // ===== 夹具 =====

    /** 造会员（手机号按时间戳生成，避免二次运行撞 uk_phone） */
    protected long createMember(String nicknamePrefix) {
        String phone = newPhone();
        jdbc.update("INSERT INTO mall_member (phone, nickname, source, status, points) VALUES (?,?,2,1,0)",
                phone, TEST_MARK + nicknamePrefix + "-" + phone);
        Long id = jdbc.queryForObject("SELECT id FROM mall_member WHERE phone = ?", Long.class, phone);
        memberIds.add(id);
        return id;
    }

    protected long createAddress(long memberId) {
        jdbc.update("INSERT INTO mall_address (member_id, receiver_name, receiver_phone, province, city, district,"
                        + " detail, is_default) VALUES (?,?,?,?,?,?,?,1)",
                memberId, "测试收件人", "19900000000", "浙江省", "杭州市", "西湖区", "测试路 1 号");
        Long id = jdbc.queryForObject(
                "SELECT id FROM mall_address WHERE member_id = ? ORDER BY id DESC LIMIT 1", Long.class, memberId);
        addressIds.add(id);
        return id;
    }

    protected long createCategory() {
        String name = TEST_MARK + "类目-" + newPhone();
        jdbc.update("INSERT INTO mall_category (parent_id, name, level, sort, status) VALUES (0,?,1,0,1)", name);
        Long id = jdbc.queryForObject("SELECT id FROM mall_category WHERE name = ?", Long.class, name);
        categoryIds.add(id);
        return id;
    }

    protected long createGoods(long categoryId, String name, long price) {
        String goodsNo = TEST_MARK + "G-" + newPhone();
        jdbc.update("INSERT INTO mall_goods (goods_no, category_id, name, main_image, price_min, price_max, sales, status)"
                        + " VALUES (?,?,?,?,?,?,0,1)",
                goodsNo, categoryId, name, "/img/test.png", price, price);
        Long id = jdbc.queryForObject("SELECT id FROM mall_goods WHERE goods_no = ?", Long.class, goodsNo);
        goodsIds.add(id);
        return id;
    }

    protected long createSku(long goodsId, long price, int stock) {
        String skuCode = TEST_MARK + "S-" + newPhone();
        jdbc.update("INSERT INTO mall_sku (goods_id, sku_code, spec_json, price, stock, sales, status)"
                        + " VALUES (?,?,?,?,?,0,1)",
                goodsId, skuCode, "[]", price, stock);
        Long id = jdbc.queryForObject("SELECT id FROM mall_sku WHERE sku_code = ?", Long.class, skuCode);
        skuIds.add(id);
        return id;
    }

    /** 一键造「类目 + 商品 + SKU」，返回 SKU ID */
    protected long createGoodsWithSku(String name, long price, int stock) {
        long categoryId = createCategory();
        long goodsId = createGoods(categoryId, name, price);
        return createSku(goodsId, price, stock);
    }

    /** 会员令牌（走 sys_session 落库，等价真实登录） */
    protected String memberToken(long memberId) {
        String token = tokenStore.create("m:" + memberId, "127.0.0.1");
        tokens.add(token);
        return token;
    }

    /** 后台管理员令牌（种子数据里 role_key=admin，权限点直通） */
    protected String adminToken() {
        String token = tokenStore.create("admin", "127.0.0.1");
        tokens.add(token);
        return token;
    }

    // ===== 断言小工具 =====

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected JsonNode body(MvcResult result) throws Exception {
        // 响应体固定 UTF-8 解析：Spring 未回写 charset 时默认 ISO-8859-1，中文断言会变乱码
        return OM.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 断言 HTTP 200 且业务 code=200，返回响应体 */
    protected JsonNode ok(ResultActions actions) throws Exception {
        MvcResult result = actions.andExpect(status().isOk()).andReturn();
        JsonNode root = body(result);
        assertEquals(200, root.get("code").asInt(),
                "期望业务 code=200，实际响应："
                        + result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        return root;
    }

    /** 便捷重载：直接传请求构造器 */
    protected JsonNode ok(MockHttpServletRequestBuilder builder) throws Exception {
        return ok(mvc.perform(builder));
    }

    /** 断言 HTTP 200 但业务 code 为期望的失败码（业务异常统一走 Result 包装，HTTP 仍 200） */
    protected JsonNode bizError(ResultActions actions, int expectedCode) throws Exception {
        MvcResult result = actions.andExpect(status().isOk()).andReturn();
        JsonNode root = body(result);
        assertEquals(expectedCode, root.get("code").asInt(),
                "期望业务 code=" + expectedCode + "，实际响应："
                        + result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        return root;
    }

    /** 便捷重载：直接传请求构造器 */
    protected JsonNode bizError(MockHttpServletRequestBuilder builder, int expectedCode) throws Exception {
        return bizError(mvc.perform(builder), expectedCode);
    }

    protected String json(Object value) throws Exception {
        return OM.writeValueAsString(value);
    }

    protected MvcResult postJson(String url, String token, String jsonBody) throws Exception {
        var builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (jsonBody != null) {
            builder.content(jsonBody);
        }
        if (token != null) {
            builder.header("Authorization", bearer(token));
        }
        return mvc.perform(builder).andReturn();
    }

    // ===== 读库小工具 =====

    protected int stockOf(long skuId) {
        Integer v = jdbc.queryForObject("SELECT stock FROM mall_sku WHERE id = ?", Integer.class, skuId);
        return v == null ? -1 : v;
    }

    protected int salesOf(long skuId) {
        Integer v = jdbc.queryForObject("SELECT sales FROM mall_sku WHERE id = ?", Integer.class, skuId);
        return v == null ? -1 : v;
    }

    protected int orderStatusOf(long orderId) {
        Integer v = jdbc.queryForObject("SELECT status FROM mall_order WHERE id = ?", Integer.class, orderId);
        return v == null ? -1 : v;
    }

    protected int refundStatusOf(long refundId) {
        Integer v = jdbc.queryForObject("SELECT status FROM mall_refund WHERE id = ?", Integer.class, refundId);
        return v == null ? -1 : v;
    }

    protected String newPhone() {
        // 199 + 当前毫秒低位 + 随机两位，保证同批次与跨批次都不撞 uk_phone
        long seed = System.currentTimeMillis() % 100_000_000L;
        int random = ThreadLocalRandom.current().nextInt(100);
        return String.format("199%08d%02d", seed, random);
    }

    /** 清理本类夹具（@AfterAll 调用）：子表先删，父表后删 */
    protected void tearDownFixtures() {
        String memberIn = inClause(memberIds);
        if (memberIn != null) {
            // V12：发货单/支付单/发票先于订单清理，否则残留会撞唯一键
            // （uk_delivery_express、uk_payment_pay_no、uk_invoice_order），表现为二次运行才失败
            jdbc.update("DELETE FROM mall_delivery_item WHERE order_id IN (SELECT id FROM mall_order WHERE member_id IN ("
                    + memberIn + "))");
            jdbc.update("DELETE FROM mall_delivery WHERE order_id IN (SELECT id FROM mall_order WHERE member_id IN ("
                    + memberIn + "))");
            jdbc.update("DELETE FROM mall_payment WHERE member_id IN (" + memberIn + ")");
            jdbc.update("DELETE FROM mall_invoice WHERE member_id IN (" + memberIn + ")");
            jdbc.update("DELETE FROM mall_order_log WHERE order_id IN (SELECT id FROM mall_order WHERE member_id IN ("
                    + memberIn + "))");
            jdbc.update("DELETE FROM mall_order_item WHERE order_id IN (SELECT id FROM mall_order WHERE member_id IN ("
                    + memberIn + "))");
            jdbc.update("DELETE FROM mall_refund WHERE member_id IN (" + memberIn + ")");
            jdbc.update("DELETE FROM mall_order WHERE member_id IN (" + memberIn + ")");
            jdbc.update("DELETE FROM mall_cart WHERE member_id IN (" + memberIn + ")");
            jdbc.update("DELETE FROM mall_member_footprint WHERE member_id IN (" + memberIn + ")");
            jdbc.update("DELETE FROM mall_address WHERE member_id IN (" + memberIn + ")");
            jdbc.update("DELETE FROM mall_member WHERE id IN (" + memberIn + ")");
        }
        String addressIn = inClause(addressIds);
        if (addressIn != null) {
            jdbc.update("DELETE FROM mall_address WHERE id IN (" + addressIn + ")");
        }
        String skuIn = inClause(skuIds);
        if (skuIn != null) {
            jdbc.update("DELETE FROM mall_sku WHERE id IN (" + skuIn + ")");
        }
        String goodsIn = inClause(goodsIds);
        if (goodsIn != null) {
            jdbc.update("DELETE FROM mall_goods WHERE id IN (" + goodsIn + ")");
        }
        String categoryIn = inClause(categoryIds);
        if (categoryIn != null) {
            jdbc.update("DELETE FROM mall_category WHERE id IN (" + categoryIn + ")");
        }
        for (String token : tokens) {
            jdbc.update("DELETE FROM sys_session WHERE token = ?", token);
        }
        memberIds.clear();
        goodsIds.clear();
        skuIds.clear();
        addressIds.clear();
        categoryIds.clear();
        tokens.clear();
    }

    private static String inClause(Set<Long> ids) {
        if (ids.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>(ids.size());
        for (Long id : ids) {
            parts.add(String.valueOf(id));
        }
        return String.join(",", parts);
    }
}
