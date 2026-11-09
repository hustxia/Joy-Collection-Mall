package com.mall.goods;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 运费模板（V12 REQ-P0-02）集成测试：模板维护 + 地区匹配 + 三种计费方式 + 无模板回落。
 *
 * <p>断言全部对着 {@code /inner/freight/quote} 的返回值，与订单域看到的口径完全一致
 * （订单域不重算，只求和）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FreightTemplateFlowTest extends MallIntegrationTestBase {

    private static final String PREFIX = "IT-V12-FREIGHT-";

    private String admin;

    @BeforeAll
    void initTokens() {
        admin = adminToken();
        jdbc.update("UPDATE mall_freight_template SET deleted = 1 WHERE name LIKE ?", PREFIX + "%");
    }

    @AfterAll
    void cleanUp() {
        jdbc.update("DELETE FROM mall_freight_rule WHERE template_id IN"
                + " (SELECT id FROM (SELECT id FROM mall_freight_template WHERE name LIKE ?) t)", PREFIX + "%");
        jdbc.update("DELETE FROM mall_freight_template WHERE name LIKE ?", PREFIX + "%");
    }

    @Test
    @DisplayName("按件计费：买 5 件 = 首 2 件 8 元 + 续 3 件 9 元 = 17 元")
    void quoteByPiece() throws Exception {
        long templateId = createTemplate("按件", 1, List.of(rule(null, 2, 800, 1, 300, 9900)));
        long skuId = createGoodsWithSku(PREFIX + "按件商品", 1000, 100);
        long goodsId = jdbc.queryForObject("SELECT goods_id FROM mall_sku WHERE id = ?", Long.class, skuId);
        bindGoodsTemplate(goodsId, templateId);

        JsonNode quote = quote(goodsId, skuId, 5, 5000, "浙江省", "杭州市");
        assertEquals(1700L, quote.path("amount").asLong(), "5 件应为 8 + 3×3 = 17 元");
        assertTrue(quote.path("matched").asBoolean());
        assertTrue(quote.path("tip").asText().contains("免运费"), "应提示满额包邮差额");
    }

    @Test
    @DisplayName("按重量计费：2.8kg = 首重 1kg 10 元 + 续重 4 段 × 4 元 = 26 元")
    void quoteByWeight() throws Exception {
        long templateId = createTemplate("按重量", 2, List.of(rule(null, 1000, 1000, 500, 400, 0)));
        long skuId = createGoodsWithSku(PREFIX + "重量商品", 1000, 100);
        jdbc.update("UPDATE mall_sku SET weight = 1400 WHERE id = ?", skuId);
        long goodsId = jdbc.queryForObject("SELECT goods_id FROM mall_sku WHERE id = ?", Long.class, skuId);
        bindGoodsTemplate(goodsId, templateId);

        JsonNode quote = quote(goodsId, skuId, 2, 2000, "浙江省", "杭州市");
        assertEquals(2600L, quote.path("amount").asLong(), "1400g × 2 = 2.8kg 应为 26 元");
    }

    @Test
    @DisplayName("满额包邮：商品金额达阈值运费为 0，且提示已包邮")
    void freeShipping() throws Exception {
        long templateId = createTemplate("满额包邮", 1, List.of(rule(null, 1, 800, 1, 300, 9900)));
        long skuId = createGoodsWithSku(PREFIX + "包邮商品", 5000, 100);
        long goodsId = jdbc.queryForObject("SELECT goods_id FROM mall_sku WHERE id = ?", Long.class, skuId);
        bindGoodsTemplate(goodsId, templateId);

        JsonNode free = quote(goodsId, skuId, 2, 10000, "浙江省", "杭州市");
        assertEquals(0L, free.path("amount").asLong());
        assertTrue(free.path("tip").asText().contains("已满"), "应提示已包邮");

        JsonNode notFree = quote(goodsId, skuId, 1, 5000, "浙江省", "杭州市");
        assertEquals(800L, notFree.path("amount").asLong(), "未达门槛收首费");
        assertEquals(4900L, notFree.path("freeGap").asLong(), "还差 49 元");
    }

    @Test
    @DisplayName("地区规则优先：新疆专用价命中，其他省份回落默认规则")
    void regionRule() throws Exception {
        long templateId = createTemplate("地区差异", 1, List.of(
                rule("新疆,西藏", 1, 2000, 1, 1000, 0),
                rule(null, 1, 800, 1, 300, 0)));
        long skuId = createGoodsWithSku(PREFIX + "地区商品", 1000, 100);
        long goodsId = jdbc.queryForObject("SELECT goods_id FROM mall_sku WHERE id = ?", Long.class, skuId);
        bindGoodsTemplate(goodsId, templateId);

        assertEquals(2000L, quote(goodsId, skuId, 1, 1000, "新疆", "乌鲁木齐市").path("amount").asLong());
        assertEquals(800L, quote(goodsId, skuId, 1, 1000, "浙江省", "杭州市").path("amount").asLong());
    }

    @Test
    @DisplayName("无任何可用模板：运费 0 且 matched=false（不阻断下单，日志告警）")
    void noTemplateFallsBackToZero() throws Exception {
        long skuId = createGoodsWithSku(PREFIX + "无模板商品", 1000, 100);
        long goodsId = jdbc.queryForObject("SELECT goods_id FROM mall_sku WHERE id = ?", Long.class, skuId);
        List<Long> actives = jdbc.queryForList(
                "SELECT id FROM mall_freight_template WHERE status = 1 AND deleted = 0", Long.class);
        try {
            actives.forEach(id -> jdbc.update("UPDATE mall_freight_template SET status = 0 WHERE id = ?", id));
            JsonNode quote = quote(goodsId, skuId, 3, 3000, "浙江省", "杭州市");
            assertFalse(quote.path("matched").asBoolean());
            assertEquals(0L, quote.path("amount").asLong());
        } finally {
            actives.forEach(id -> jdbc.update("UPDATE mall_freight_template SET status = 1 WHERE id = ?", id));
        }
    }

    @Test
    @DisplayName("默认模板不可删除，被商品引用的模板也不可删")
    void deleteGuards() throws Exception {
        long templateId = createTemplate("默认保护", 1, List.of(rule(null, 1, 800, 1, 300, 0)));
        ok(post("/mall_freight_template/default").header("Authorization", bearer(admin))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json(Map.of("id", templateId))));
        bizError(delete("/mall_freight_template/" + templateId).header("Authorization", bearer(admin)), 400);

        long another = createTemplate("引用保护", 1, List.of(rule(null, 1, 500, 1, 200, 0)));
        long skuId = createGoodsWithSku(PREFIX + "引用商品", 1000, 10);
        long goodsId = jdbc.queryForObject("SELECT goods_id FROM mall_sku WHERE id = ?", Long.class, skuId);
        bindGoodsTemplate(goodsId, another);
        bizError(delete("/mall_freight_template/" + another).header("Authorization", bearer(admin)), 400);
    }

    // ===== 辅助 =====

    private long createTemplate(String suffix, int chargeType, List<Map<String, Object>> rules) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", PREFIX + suffix);
        body.put("chargeType", chargeType);
        body.put("isDefault", 0);
        body.put("status", 1);
        body.put("rules", rules);
        ok(post("/mall_freight_template").header("Authorization", bearer(admin))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json(body)));
        return jdbc.queryForObject("SELECT id FROM mall_freight_template WHERE name = ? AND deleted = 0",
                Long.class, PREFIX + suffix);
    }

    private Map<String, Object> rule(String regions, int firstUnit, long firstFee, int extraUnit, long extraFee,
                                     long freeThreshold) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("regionCodes", regions);
        m.put("firstUnit", firstUnit);
        m.put("firstFee", firstFee);
        m.put("extraUnit", extraUnit);
        m.put("extraFee", extraFee);
        m.put("freeThreshold", freeThreshold);
        return m;
    }

    private void bindGoodsTemplate(long goodsId, long templateId) {
        jdbc.update("UPDATE mall_goods SET freight_template_id = ? WHERE id = ?", templateId, goodsId);
    }

    private JsonNode quote(long goodsId, long skuId, int qty, long goodsAmount,
                           String province, String city) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", 1);
        body.put("province", province);
        body.put("city", city);
        body.put("goodsAmount", goodsAmount);
        body.put("items", List.of(Map.of("skuId", skuId, "goodsId", goodsId, "quantity", qty)));
        // /inner/** 返回裸对象（不套 Result 壳），直接解析响应体
        org.springframework.test.web.servlet.MvcResult result = mvc.perform(post("/inner/freight/quote")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        return body(result);
    }
}
