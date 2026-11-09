package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.auth.TokenStore;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.entity.MallMember;
import com.mall.service.MallAgreementService;
import com.mall.service.MallMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * C 端会员认证（V3）：手机号+密码，BCrypt 存储。
 * member token 复用 TokenStore，username 存 "m:{memberId}" 与后台会话区分。
 */
@Tag(name = "商城前台-认证", description = "会员注册/登录/资料")
@RestController
@RequestMapping("/shop/auth")
public class ShopAuthController {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    @Autowired
    private MallMemberService mallMemberService;
    @Autowired
    private TokenStore tokenStore;
    @Autowired
    private MallAgreementService mallAgreementService;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Operation(summary = "注册（匿名）：成功直接发 member token")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterDTO dto, HttpServletRequest request) {
        if (dto == null || !StringUtils.hasText(dto.getPhone()) || !PHONE_PATTERN.matcher(dto.getPhone()).matches()) {
            throw new BusinessException(400, "手机号格式不正确");
        }
        // V11 合规：未勾选协议不得注册（前端 checkbox 只是提示，服务端才是边界）
        if (!Boolean.TRUE.equals(dto.getAgreed())) {
            throw new BusinessException(400, "请先阅读并同意用户协议");
        }
        if (!StringUtils.hasText(dto.getPassword()) || dto.getPassword().length() < 6 || dto.getPassword().length() > 20) {
            throw new BusinessException(400, "密码长度须为 6~20 位");
        }
        LambdaQueryWrapper<MallMember> exist = new LambdaQueryWrapper<>();
        exist.eq(MallMember::getPhone, dto.getPhone());
        if (mallMemberService.count(exist) > 0) {
            throw new BusinessException(400, "该手机号已注册");
        }
        MallMember member = new MallMember();
        member.setPhone(dto.getPhone());
        member.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname()
                : "会员" + dto.getPhone().substring(7));
        member.setPassword(bcrypt.encode(dto.getPassword()));
        member.setPasswordAlgo("bcrypt");
        member.setSource(2);
        member.setStatus(1);
        member.setPoints(0);
        member.setLastLoginTime(LocalDateTime.now());
        mallMemberService.save(member);
        // 同意留痕：注册即视为同意当前生效的用户协议与隐私政策（版本号一并落库，便于日后举证）
        mallAgreementService.record(member.getId(), 1, clientIp(request));
        mallAgreementService.record(member.getId(), 2, clientIp(request));
        String token = tokenStore.create("m:" + member.getId(), clientIp(request));
        return Result.ok(loginData(token, member));
    }

    @Operation(summary = "登录（匿名）：手机号+密码")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO dto, HttpServletRequest request) {
        if (dto == null || !StringUtils.hasText(dto.getPhone()) || !StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException(400, "手机号和密码不能为空");
        }
        LambdaQueryWrapper<MallMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallMember::getPhone, dto.getPhone());
        MallMember member = mallMemberService.getOne(wrapper);
        if (member == null) {
            throw new BusinessException(400, "手机号或密码错误");
        }
        // password 为空是「历史存量账号」的典型特征（V3 才加的 password 列，早期演示会员一直是 NULL）。
        // 若与密码错误合并成同一句提示，用户会一直怀疑自己密码记错、反复重试——分开给可操作的指引。
        // 代价：会暴露「该手机号已注册」，演示环境取可用性，生产若在意枚举风险可合并回统一文案。
        if (!StringUtils.hasText(member.getPassword())) {
            throw new BusinessException(400, "该账号尚未设置登录密码，请使用手机号重新注册或联系客服");
        }
        if (!bcrypt.matches(dto.getPassword(), member.getPassword())) {
            throw new BusinessException(400, "手机号或密码错误");
        }
        if (member.getStatus() != null && member.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系客服");
        }
        member.setLastLoginTime(LocalDateTime.now());
        mallMemberService.updateById(member);
        String token = tokenStore.create("m:" + member.getId(), clientIp(request));
        return Result.ok(loginData(token, member));
    }

    @Operation(summary = "登出：删除当前会话")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            tokenStore.remove(authorization.substring(7));
        }
        return Result.ok();
    }

    @Operation(summary = "当前会员资料")
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        MallMember member = currentMember();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", String.valueOf(member.getId()));
        data.put("nickname", member.getNickname());
        data.put("phone", member.getPhone());
        data.put("avatar", member.getAvatar());
        data.put("gender", member.getGender());
        data.put("points", member.getPoints());
        return Result.ok(data);
    }

    @Operation(summary = "修改资料（昵称/头像/性别）")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody ProfileDTO dto) {
        MallMember member = currentMember();
        if (dto != null) {
            if (StringUtils.hasText(dto.getNickname())) {
                member.setNickname(dto.getNickname());
            }
            if (dto.getAvatar() != null) {
                member.setAvatar(dto.getAvatar());
            }
            if (dto.getGender() != null) {
                member.setGender(dto.getGender());
            }
        }
        mallMemberService.updateById(member);
        return Result.ok();
    }

    private MallMember currentMember() {
        MallMember member = mallMemberService.getById(UserContext.getUserId());
        if (member == null) {
            throw new BusinessException(401, "会员不存在，请重新登录");
        }
        return member;
    }

    private Map<String, Object> loginData(String token, MallMember member) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(member.getId()));
        m.put("nickname", member.getNickname());
        m.put("phone", member.getPhone());
        m.put("avatar", member.getAvatar());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("member", m);
        return data;
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip)) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Data
    public static class RegisterDTO {
        private String phone;
        private String password;
        private String nickname;
        /** V11 合规：是否勾选「已阅读并同意用户协议与隐私政策」，未勾选不放行 */
        private Boolean agreed;
    }

    @Data
    public static class LoginDTO {
        private String phone;
        private String password;
    }

    @Data
    public static class ProfileDTO {
        private String nickname;
        private String avatar;
        private Integer gender;
    }
}
