package com.mall.goods;

import com.mall.freight.FreightCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 运费算术口径（V12）：需求文档 REQ-P0-02 的两条验收样例逐字落成断言。
 *
 * <p>这里刻意不启动 Spring——纯函数算错就是算错，跟容器、数据库无关，
 * 用最便宜的测试锁住最容易算错的「续段向上取整」。
 */
class FreightCalculatorTest {

    @Test
    @DisplayName("按件计费：首件 2 件 8 元、续件 1 件 3 元，买 5 件 = 17 元")
    void byPiece() {
        long fee = FreightCalculator.calc(1, 2, 800L, 1, 300L, 0L, 5, 10000L);
        assertEquals(1700L, fee);
    }

    @Test
    @DisplayName("按重量计费：首重 1kg 10 元、续重 0.5kg 4 元，2.8kg = 26 元（续重向上取整 4 段）")
    void byWeight() {
        // 2.8kg = 2800g；首重 1000g，余 1800g，每 500g 一段 → ceil(1800/500)=4 段
        long fee = FreightCalculator.calc(2, 1000, 1000L, 500, 400L, 0L, 2800, 10000L);
        assertEquals(2600L, fee);
    }

    @Test
    @DisplayName("刚好等于首件量：只收首费，不产生续费")
    void exactlyFirstUnit() {
        assertEquals(800L, FreightCalculator.calc(1, 2, 800L, 1, 300L, 0L, 2, 10000L));
    }

    @Test
    @DisplayName("满额包邮：商品金额达阈值时运费 0，优先级高于续费计算")
    void freeOverThreshold() {
        assertEquals(0L, FreightCalculator.calc(1, 2, 800L, 1, 300L, 9900L, 50, 9900L));
        assertEquals(0L, FreightCalculator.calc(1, 2, 800L, 1, 300L, 9900L, 50, 20000L));
    }

    @Test
    @DisplayName("满额包邮型模板：未达门槛按固定首费，达门槛为 0")
    void freeOverType() {
        assertEquals(800L, FreightCalculator.calc(3, 2, 800L, 1, 300L, 9900L, 5, 5000L));
        assertEquals(0L, FreightCalculator.calc(3, 2, 800L, 1, 300L, 9900L, 5, 9900L));
    }

    @Test
    @DisplayName("0 件不收费：避免出现「0 件商品 8 元运费」")
    void zeroUnits() {
        assertEquals(0L, FreightCalculator.calc(1, 2, 800L, 1, 300L, 0L, 0, 0L));
    }

    @Test
    @DisplayName("包邮差额：未达门槛返回差值，已达门槛为 0")
    void gap() {
        assertEquals(4900L, FreightCalculator.freeGap(9900L, 5000L));
        assertEquals(0L, FreightCalculator.freeGap(9900L, 9900L));
        assertEquals(0L, FreightCalculator.freeGap(0L, 100L));
    }

    @Test
    @DisplayName("金额恒等式：pay_amount = 商品额 − 优惠 + 运费")
    void payAmountIdentity() {
        long goods = 10000L;
        long freight = FreightCalculator.calc(1, 2, 800L, 1, 300L, 0L, 3, goods);
        long discount = 500L;
        assertEquals(goods - discount + freight, 10000L - 500L + 1100L);
    }
}
