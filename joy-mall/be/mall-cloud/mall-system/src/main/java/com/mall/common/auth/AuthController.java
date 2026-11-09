package com.mall.common.auth;

import com.mall.common.log.IpRegionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录基座：POST /auth/login、POST /auth/logout、GET /auth/me、GET /auth/captcha。
 * V6.4 安全基线：
 * <ul>
 *   <li>密码 BCrypt（password_algo 分派，旧 SHA-256 存量用户登录成功即无感迁移）</li>
 *   <li>算术验证码（60s 一次性，内存存答案）</li>
 *   <li>失败锁定：连续 5 次锁 10 分钟（计数落库，重启不清零）</li>
 *   <li>登录日志 sys_login_log（含 IP 归属地异步回填）</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final int MAX_FAIL = 5;
    private static final int LOCK_MINUTES = 10;
    private static final long CAPTCHA_TTL_MS = 60_000L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenStore tokenStore;

    @Autowired
    private IpRegionResolver ipRegionResolver;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, CaptchaEntry> captchas = new ConcurrentHashMap<>();

    /**
     * 是否把验证码答案下发给客户端：<b>默认 false，生产禁止开启</b>。
     * 只有自动化验收（CI 冒烟脚本无人看图）在联调环境显式打开，
     * 否则脚本无法完成后台登录，只能人工贴 token。
     */
    @Value("${auth.captcha.expose-answer:false}")
    private boolean exposeCaptchaAnswer;

    /** 算术验证码：题目画进图里，答案只存服务端内存 */
    @GetMapping("/captcha")
    public Map<String, Object> captcha() {
        captchas.entrySet().removeIf(e -> e.getValue().expiredAt() < System.currentTimeMillis());
        int a = random.nextInt(9) + 1;
        int b = random.nextInt(9) + 1;
        boolean plus = random.nextBoolean();
        int x = Math.max(a, b);
        int y = Math.min(a, b);
        int answer = plus ? x + y : x - y;
        String text = x + (plus ? "+" : "-") + y + "=?";
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        captchas.put(captchaId, new CaptchaEntry(String.valueOf(answer),
                System.currentTimeMillis() + CAPTCHA_TTL_MS));
        Map<String, Object> data = new HashMap<>();
        data.put("captchaId", captchaId);
        data.put("image", toBase64Png(text));
        if (exposeCaptchaAnswer) {
            // 仅供自动化验收取答案；生产环境该开关必须保持关闭
            data.put("question", text);
            data.put("answer", String.valueOf(answer));
        }
        return Map.of("code", 200, "msg", "success", "data", data);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Map.of("code", 400, "msg", "用户名或密码不能为空");
        }
        String ip = clientIp(request);
        // 1) 验证码：一次性 + 60s，防脚本撞库（答案校验放在密码比对前）
        String captchaId = body.getOrDefault("captchaId", "");
        String captchaAnswer = body.getOrDefault("captchaAnswer", "");
        CaptchaEntry cap = captchas.remove(captchaId);
        if (cap == null || cap.expiredAt() < System.currentTimeMillis()
                || !cap.answer().equals(captchaAnswer.trim())) {
            return loginLogAndRespond(username, ip, false, "验证码错误或已过期", null);
        }
        // 2) 失败锁定：计数落库，重启不清零
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, username, password, password_algo, fail_count, lock_until FROM sys_user WHERE username = ? AND deleted = 0",
                username);
        if (users.isEmpty()) {
            return loginLogAndRespond(username, ip, false, "用户名或密码错误", null);
        }
        Map<String, Object> user = users.get(0);
        long userId = ((Number) user.get("id")).longValue();
        int failCount = ((Number) user.getOrDefault("fail_count", 0)).intValue();
        Object lockUntilObj = user.get("lock_until");
        if (lockUntilObj != null) {
            LocalDateTime lockUntil = lockUntilObj instanceof java.time.LocalDateTime ldt2
                    ? ldt2 : ((Timestamp) lockUntilObj).toLocalDateTime();
            if (LocalDateTime.now().isBefore(lockUntil)) {
                long minutes = java.time.Duration.between(LocalDateTime.now(), lockUntil).toMinutes() + 1;
                return loginLogAndRespond(username, ip, false, "失败次数过多，账号已锁定，请 " + minutes + " 分钟后再试", null);
            }
        }
        // 3) 密码校验：按 password_algo 分派；旧 SHA-256 命中后立即 BCrypt 重哈希写回
        String stored = String.valueOf(user.get("password"));
        String algo = String.valueOf(user.getOrDefault("password_algo", "sha256"));
        boolean matched;
        if ("bcrypt".equalsIgnoreCase(algo)) {
            matched = bcrypt.matches(password, stored);
        } else {
            matched = sha256(password).equalsIgnoreCase(stored);
            if (matched) {
                // 无感迁移：旧哈希命中即升级 BCrypt，全量用户在自然登录中完成
                jdbcTemplate.update("UPDATE sys_user SET password = ?, password_algo = 'bcrypt' WHERE id = ?",
                        bcrypt.encode(password), userId);
            }
        }
        if (!matched) {
            int nextFail = failCount + 1;
            String msg = "用户名或密码错误";
            if (nextFail >= MAX_FAIL) {
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
                jdbcTemplate.update("UPDATE sys_user SET fail_count = ?, lock_until = ? WHERE id = ?",
                        nextFail, Timestamp.valueOf(lockUntil), userId);
                msg = "连续失败 " + nextFail + " 次，账号锁定 " + LOCK_MINUTES + " 分钟";
            } else {
                jdbcTemplate.update("UPDATE sys_user SET fail_count = ? WHERE id = ?", nextFail, userId);
            }
            return loginLogAndRespond(username, ip, false, msg, null);
        }
        // 4) 成功：清零失败计数 + 签发 token + 下发 perms
        jdbcTemplate.update("UPDATE sys_user SET fail_count = 0, lock_until = NULL WHERE id = ?", userId);
        String token = tokenStore.create(username, ip);
        List<String> perms = loadPerms(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", username);
        data.put("perms", perms);
        loginLogAndRespond(username, ip, true, null, userId);
        return Map.of("code", 200, "msg", "success", "data", data);
    }

    /** 用户权限点集：sys_user_role → sys_role_menu.perms；admin 角色直通下发 *:*:* */
    private List<String> loadPerms(long userId) {
        List<String> perms = new ArrayList<>();
        Integer admin = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ? AND r.deleted = 0 AND (r.role_key = 'admin' OR r.id = 1)",
                Integer.class, userId);
        if (admin != null && admin > 0) {
            perms.add("*:*:*");
            return perms;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DISTINCT m.perms FROM sys_user_role ur "
                        + "JOIN sys_role_menu rm ON rm.role_id = ur.role_id "
                        + "JOIN sys_menu m ON m.id = rm.menu_id "
                        + "WHERE ur.user_id = ? AND m.deleted = 0 AND m.perms IS NOT NULL AND m.perms <> ''",
                userId);
        for (Map<String, Object> row : rows) {
            perms.add(String.valueOf(row.get("perms")));
        }
        return perms;
    }

    /** 登录日志：结果先落库，归属地异步回填（查询走网络，不能阻塞登录链路） */
    private Map<String, Object> loginLogAndRespond(String username, String ip, boolean success,
                                                   String failReason, Long userId) {
        // A-04：用 KeyHolder 取生成主键；原来 SELECT MAX(id) 在并发同名登录下会把归属地回填到别人的行
        org.springframework.jdbc.support.KeyHolder keyHolder =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO sys_login_log (username, ip, success, fail_reason) VALUES (?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, ip);
            ps.setInt(3, success ? 1 : 0);
            ps.setString(4, failReason);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (ip != null && !ip.isBlank() && key != null) {
            Long logId = key.longValue();
            ipRegionResolver.resolveAsync(ip, region ->
                    jdbcTemplate.update("UPDATE sys_login_log SET region = ? WHERE id = ?",
                            region.province() + (region.city() == null ? "" : " " + region.city()), logId));
        }
        if (failReason == null) {
            return null;
        }
        return Map.of("code", 400, "msg", failReason);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            tokenStore.remove(authorization.substring(7));
        }
        return Map.of("code", 200, "msg", "success");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return Map.of("code", 401, "msg", "未登录");
        }
        String username = tokenStore.usernameOf(authorization.substring(7));
        if (username == null) {
            return Map.of("code", 401, "msg", "登录状态已失效");
        }
        return Map.of("code", 200, "msg", "success", "data", Map.of("username", username));
    }

    private String toBase64Png(String text) {
        try {
            int w = 120;
            int h = 40;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(240, 244, 248));
            g.fillRect(0, 0, w, h);
            // 干扰线
            for (int i = 0; i < 4; i++) {
                g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
                g.setStroke(new BasicStroke(1.2f));
                g.drawLine(random.nextInt(w), random.nextInt(h), random.nextInt(w), random.nextInt(h));
            }
            g.setColor(new Color(40, 60, 90));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            g.drawString(text, 18, 28);
            g.dispose();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("验证码图片生成失败", e);
        }
    }

    private String clientIp(HttpServletRequest request) {
        for (String header : new String[]{"X-Forwarded-For", "X-Real-IP"}) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private record CaptchaEntry(String answer, long expiredAt) {
    }
}
