package com.mall.common.auth;

import java.util.Map;

/**
 * 接口权限点映射贡献者：业务模块声明自己的端点-权限映射，PermissionChecker 汇集后集中校验。
 * <p>key 形如 {@code POST /fee/page}（路径段可用 {@code {id}} 通配）。
 * value 为所需权限串（如 {@code demo_fee:add}），空串表示仅登录即可（基座能力豁免）。
 * 未注册的端点走安全缺省：GET 放行、写方法拒绝（fail-closed）。
 */
public interface ApiPermContributor {

    Map<String, String> endpointPerms();
}
