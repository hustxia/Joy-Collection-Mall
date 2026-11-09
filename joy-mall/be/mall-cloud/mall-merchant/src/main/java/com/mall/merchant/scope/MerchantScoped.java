package com.mall.merchant.scope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 商家数据隔离标记（V11）。
 *
 * <p>标在 <b>Mapper 接口</b>上：该 Mapper 的所有 SELECT / UPDATE / DELETE 都会被
 * {@link MerchantScopeInterceptor} 自动追加 {@code merchant_id = 当前商家}。
 * 没标注的 Mapper 视为白名单，原样放行（平台后台、字典、会话等不该被商家维度影响）。
 *
 * <p>为什么用注解而不是配置表：谁能被改写跟着代码走，评审时一眼能看见；
 * 配置表容易出现「新加的 Mapper 忘了登记 → 静默全量可见」。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MerchantScoped {

    /** 隔离列名：默认 merchant_id（订单/商品/结算等表统一口径） */
    String column() default "merchant_id";
}
