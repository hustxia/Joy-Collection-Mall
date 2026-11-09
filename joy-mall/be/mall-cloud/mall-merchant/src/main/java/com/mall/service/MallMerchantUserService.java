package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallMerchantUser;

import java.util.Map;

/**
 * 商家账号服务（V11）：登录、当前账号、密码维护。
 *
 * <p>令牌与平台 {@code sys_user} 完全隔离：会话 username 为 {@code mt:{账号ID}:{商家ID}}，
 * 由 {@code AuthContextFilter} 分流，平台令牌打不开商家后台，商家令牌也打不开平台接口。
 */
public interface MallMerchantUserService extends IService<MallMerchantUser> {

    /**
     * 商家登录：账号密码 → 独立商家令牌。
     *
     * @param username 登录账号
     * @param password 明文密码
     * @param loginIp  登录 IP（写会话用）
     * @return token / merchant / user / perms
     */
    Map<String, Object> login(String username, String password, String loginIp);
}
