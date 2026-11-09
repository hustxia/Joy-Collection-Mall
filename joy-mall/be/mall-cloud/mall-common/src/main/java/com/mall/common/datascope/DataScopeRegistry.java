package com.mall.common.datascope;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 数据权限登记表。
 *
 * <p>新增需要做数据隔离的表时，在下面 {@code register} 里加一行即可，
 * 生成器产出的 Service/Mapper 无需改动。
 */
@Component
public class DataScopeRegistry {

    private static final Logger log = LoggerFactory.getLogger(DataScopeRegistry.class);

    @PostConstruct
    public void init() {
        // 示例：DataScopeInterceptor.register("biz_order", "owner_id", "self");
        // 示例：DataScopeInterceptor.register("biz_order", "dept_id", "dept");
        log.info("数据权限登记完成");
    }
}
