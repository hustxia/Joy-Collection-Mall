package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.auth.TokenStore;
import com.mall.common.exception.BusinessException;
import com.mall.entity.MallMerchant;
import com.mall.entity.MallMerchantUser;
import com.mall.mapper.MallMerchantMapper;
import com.mall.mapper.MallMerchantUserMapper;
import com.mall.service.MallMerchantUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家账号实现（V11）。
 *
 * <p>与平台登录同口径的三件事：失败锁定（5 次 / 10 分钟）、BCrypt 校验、
 * 登录成功清空失败计数。差异只有一处——会话 username 带 {@code mt:} 前缀，
 * 之后所有商家鉴权都靠它分流，不再共用 sys 权限体系。
 */
@Slf4j
@Service
public class MallMerchantUserServiceImpl extends ServiceImpl<MallMerchantUserMapper, MallMerchantUser>
        implements MallMerchantUserService {

    /** 连续失败上限 */
    private static final int MAX_FAIL = 5;
    /** 锁定时长（分钟） */
    private static final int LOCK_MINUTES = 10;

    private final MallMerchantMapper merchantMapper;
    private final TokenStore tokenStore;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public MallMerchantUserServiceImpl(MallMerchantMapper merchantMapper, TokenStore tokenStore) {
        this.merchantMapper = merchantMapper;
        this.tokenStore = tokenStore;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(String username, String password, String loginIp) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BusinessException(400, "账号和密码不能为空");
        }
        MallMerchantUser user = getOne(new LambdaQueryWrapper<MallMerchantUser>()
                .eq(MallMerchantUser::getUsername, username.trim())
                .last("LIMIT 1"), false);
        if (user == null) {
            throw new BusinessException(400, "账号或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(403, "该商家账号已停用，请联系平台");
        }
        if (user.getLockUntil() != null && LocalDateTime.now().isBefore(user.getLockUntil())) {
            long minutes = Duration.between(LocalDateTime.now(), user.getLockUntil()).toMinutes() + 1;
            throw new BusinessException(403, "失败次数过多，账号已锁定，请 " + minutes + " 分钟后再试");
        }
        if (!matches(password, user)) {
            int failCount = (user.getFailCount() == null ? 0 : user.getFailCount()) + 1;
            LambdaUpdateWrapper<MallMerchantUser> update = new LambdaUpdateWrapper<MallMerchantUser>()
                    .eq(MallMerchantUser::getId, user.getId())
                    .set(MallMerchantUser::getFailCount, failCount);
            String msg = "账号或密码错误";
            if (failCount >= MAX_FAIL) {
                update.set(MallMerchantUser::getLockUntil, LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                msg = "连续失败 " + failCount + " 次，账号锁定 " + LOCK_MINUTES + " 分钟";
            }
            update(update);
            throw new BusinessException(400, msg);
        }
        MallMerchant merchant = merchantMapper.selectById(user.getMerchantId());
        if (merchant == null) {
            throw new BusinessException(400, "店铺不存在，请联系平台");
        }
        if (!Integer.valueOf(1).equals(merchant.getStatus())) {
            throw new BusinessException(403, "店铺未启用，暂不可登录商家后台");
        }
        update(new LambdaUpdateWrapper<MallMerchantUser>()
                .eq(MallMerchantUser::getId, user.getId())
                .set(MallMerchantUser::getFailCount, 0)
                .set(MallMerchantUser::getLockUntil, null)
                .set(MallMerchantUser::getLastLoginTime, LocalDateTime.now()));
        String token = tokenStore.create(tokenUsername(user), loginIp);
        log.info("商家登录成功：merchantId={}, username={}", user.getMerchantId(), user.getUsername());
        return loginData(token, merchant, user);
    }

    /** 会话 username：mt:{账号ID}:{商家ID}（AuthContextFilter 按此解析上下文） */
    public static String tokenUsername(MallMerchantUser user) {
        return "mt:" + user.getId() + ":" + user.getMerchantId();
    }

    /** 商家侧权限点：店主全量、店员只保留发品/发货/售后 */
    private List<String> permsOf(MallMerchantUser user) {
        List<String> perms = new ArrayList<>();
        if (MallMerchantUser.ROLE_STAFF.equalsIgnoreCase(user.getRole())) {
            perms.add("merchant:goods:*");
            perms.add("merchant:order:*");
            perms.add("merchant:refund:*");
            return perms;
        }
        perms.add("*:*:*");
        return perms;
    }

    private Map<String, Object> loginData(String token, MallMerchant merchant, MallMerchantUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(merchant.getId()));
        m.put("name", merchant.getName());
        m.put("merchantNo", merchant.getMerchantNo());
        m.put("logo", merchant.getLogo());
        m.put("accountBalance", merchant.getAccountBalance());
        data.put("merchant", m);
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("id", String.valueOf(user.getId()));
        u.put("username", user.getUsername());
        u.put("realName", user.getRealName());
        u.put("role", user.getRole());
        data.put("user", u);
        data.put("perms", permsOf(user));
        return data;
    }

    private boolean matches(String raw, MallMerchantUser user) {
        String stored = user.getPassword();
        if (!StringUtils.hasText(stored)) {
            return false;
        }
        if ("sha256".equalsIgnoreCase(user.getPasswordAlgo())) {
            return sha256Hex(raw).equalsIgnoreCase(stored);
        }
        return bcrypt.matches(raw, stored);
    }

    /** 兼容 sys_user 早期 SHA-256 存量哈希（与平台登录同算法，便于迁移账号） */
    private String sha256Hex(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
