package com.mall.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.mall.common.datascope.DataScopeInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：注册数据权限与分页拦截器
 *
 * <p>分页拦截器必须注册：缺了它 {@code service.page(page, wrapper)} 只做 limit 不做 count，
 * 返回的 {@code Page.total} 恒为 0——前端分页器算不出总页数。
 *
 * <p>数据权限拦截器放在分页之前：先缩小可见范围再 count，
 * 否则不该看到的行也会被算进总数。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(ObjectProvider<InnerInterceptor> extraInterceptors) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new DataScopeInterceptor());
        // 业务域自带的内部拦截器（V11 商家数据隔离等）必须在分页之前：
        // 先缩范围再 count，否则统计里会混进不该看到的行。
        extraInterceptors.orderedStream().forEach(interceptor::addInnerInterceptor);
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限，防止前端传 pageSize=999999 拖垮库
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
