package com.mall.controller;

import com.mall.common.auth.TokenStore;
import com.mall.common.context.MerchantContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.entity.MallMerchant;
import com.mall.entity.MallMerchantUser;
import com.mall.mapper.MallMerchantMapper;
import com.mall.mapper.MallMerchantUserMapper;
import com.mall.service.MallMerchantUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商家登录（V11）。
 *
 * <p>令牌走独立的 {@code Merchant-Token} 请求头，与平台 {@code Authorization} 互不通用：
 * 平台令牌打不开商家后台，商家令牌也打不开平台接口（见 AuthContextFilter 的三方分流）。
 */
@Tag(name = "商家后台-登录", description = "商家账号登录/登出/当前店铺")
@RestController
@RequestMapping("/merchant-admin/auth")
public class MerchantAuthController {

    private final MallMerchantUserService merchantUserService;
    private final MallMerchantUserMapper merchantUserMapper;
    private final MallMerchantMapper merchantMapper;
    private final TokenStore tokenStore;

    public MerchantAuthController(MallMerchantUserService merchantUserService,
                                  MallMerchantUserMapper merchantUserMapper,
                                  MallMerchantMapper merchantMapper,
                                  TokenStore tokenStore) {
        this.merchantUserService = merchantUserService;
        this.merchantUserMapper = merchantUserMapper;
        this.merchantMapper = merchantMapper;
        this.tokenStore = tokenStore;
    }

    @Operation(summary = "商家登录：账号密码 → 独立商家令牌")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO dto, HttpServletRequest request) {
        if (dto == null || !StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException(400, "账号和密码不能为空");
        }
        // 验证码为可选：商家后台是内部账号体系，默认不强制；接入图形验证码时在此校验 captchaId/captchaAnswer
        return Result.ok(merchantUserService.login(dto.getUsername(), dto.getPassword(), clientIp(request)));
    }

    @Operation(summary = "商家登出：删除当前商家会话")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Merchant-Token", required = false) String token) {
        if (StringUtils.hasText(token)) {
            tokenStore.remove(token);
        }
        return Result.ok();
    }

    @Operation(summary = "当前商家与账号信息（含余额）")
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        Long merchantId = MerchantContext.get();
        if (merchantId == null) {
            throw new BusinessException(403, "缺少商家身份，请重新登录商家后台");
        }
        MallMerchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(400, "店铺不存在");
        }
        MallMerchantUser user = merchantUserMapper.selectById(MerchantContext.getMerchantUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(merchant.getId()));
        m.put("name", merchant.getName());
        m.put("merchantNo", merchant.getMerchantNo());
        m.put("logo", merchant.getLogo());
        m.put("contactName", merchant.getContactName());
        m.put("contactPhone", merchant.getContactPhone());
        m.put("settleRate", merchant.getSettleRate());
        m.put("accountBalance", merchant.getAccountBalance());
        m.put("settledTotal", merchant.getSettledTotal());
        m.put("status", merchant.getStatus());
        data.put("merchant", m);
        if (user != null) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("id", String.valueOf(user.getId()));
            u.put("username", user.getUsername());
            u.put("realName", user.getRealName());
            u.put("role", user.getRole());
            u.put("lastLoginTime", user.getLastLoginTime());
            data.put("user", u);
        }
        return Result.ok(data);
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip)) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 登录入参：验证码字段可选，预留后不破坏前端契约 */
    public static class LoginDTO {
        private String username;
        private String password;
        private String captchaId;
        private String captchaAnswer;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getCaptchaId() {
            return captchaId;
        }

        public void setCaptchaId(String captchaId) {
            this.captchaId = captchaId;
        }

        public String getCaptchaAnswer() {
            return captchaAnswer;
        }

        public void setCaptchaAnswer(String captchaAnswer) {
            this.captchaAnswer = captchaAnswer;
        }
    }
}
