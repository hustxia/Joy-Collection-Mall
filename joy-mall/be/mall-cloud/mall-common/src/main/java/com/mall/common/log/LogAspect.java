package com.mall.common.log;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.mall.common.context.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 操作日志切面（公共件，跨服务复用）。
 *
 * <p>与基座一致：日志写失败绝不影响主流程；参数落库前按字段名脱敏；超长内容截断。
 * 落库走共享库 sys_oper_log 的原始 INSERT（不依赖系统域的 OpeLog 实体，
 * 保证 common 自包含、可被任意业务服务引入）。
 */
@Aspect
@Component
public class LogAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    /**
     * 单字段上限：够定位问题，又不至于把表塞满
     */
    private static final int MAX_TEXT = 2000;

    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)\"(password|passwd|pwd|token|secret|authorization|credential)\"\\s*:\\s*"
                    + "(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}\\]\\s]+)");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IpRegionResolver ipRegionResolver;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint point, Log operLog) throws Throwable {
        long start = System.currentTimeMillis();
        String errorMsg = null;
        Object result = null;
        try {
            result = point.proceed();
            return result;
        } catch (Throwable e) {
            errorMsg = e.getMessage();
            throw e;
        } finally {
            try {
                save(point, operLog, result, errorMsg, System.currentTimeMillis() - start);
            } catch (Exception e) {
                // 记录日志失败不能反向影响业务
                log.warn("操作日志写入失败：{}", e.getMessage());
            }
        }
    }

    private void save(ProceedingJoinPoint point, Log operLog, Object result,
                      String errorMsg, long costMs) {
        long id = IdWorker.getId();
        String title = operLog.title();
        int status = errorMsg == null ? 1 : 0;
        String truncatedError = truncate(errorMsg);

        MethodSignature signature = (MethodSignature) point.getSignature();
        String method = signature.getDeclaringType().getSimpleName() + "#" + signature.getName();

        HttpServletRequest request = currentRequest();
        String uri = null;
        String reqMethod = null;
        String ip = null;
        if (request != null) {
            uri = request.getRequestURI();
            reqMethod = request.getMethod();
            ip = clientIp(request);
        }
        String operType = inferType(operLog.type(), request, signature.getName());
        String operParam = operLog.saveParam() ? truncate(toJson(sanitize(point.getArgs()))) : null;
        String jsonResult = (operLog.saveResult() && result != null)
                ? truncate(toJson(result)) : null;

        jdbcTemplate.update(
                "INSERT INTO sys_oper_log (id, title, oper_type, method, request_uri, request_method, "
                        + "oper_param, json_result, status, error_msg, oper_user_id, oper_name, oper_ip, "
                        + "cost_ms, oper_time, create_time, update_time, deleted) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, NOW(), NOW(), 0)",
                id, title, operType, method, uri, reqMethod, operParam, jsonResult,
                status, truncatedError, UserContext.getUserId(), UserContext.getUsername(), ip,
                costMs, LocalDateTime.now());

        // 省市由 IP 反查，异步回填，避免阻塞业务接口
        if (ip != null) {
            long logId = id;
            ipRegionResolver.resolveAsync(ip, region -> {
                try {
                    jdbcTemplate.update("UPDATE sys_oper_log SET province=?, city=? WHERE id=?",
                            region.province(), region.city(), logId);
                } catch (Exception e) {
                    log.warn("操作日志地域回填失败：{}", e.getMessage());
                }
            });
        }
    }

    /**
     * 文件流、Servlet 对象无法序列化，替换成类型占位
     */
    private List<Object> sanitize(Object[] args) {
        List<Object> list = new ArrayList<>();
        if (args == null) {
            return list;
        }
        for (Object arg : args) {
            if (arg instanceof MultipartFile file) {
                list.add("<file:" + file.getOriginalFilename() + ", " + file.getSize() + "B>");
            } else if (arg instanceof MultipartFile[] files) {
                list.add("<files:" + files.length + ">");
            } else if (arg instanceof jakarta.servlet.ServletRequest
                    || arg instanceof jakarta.servlet.ServletResponse) {
                list.add("<servlet>");
            } else {
                list.add(arg);
            }
        }
        return list;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return maskSensitive(objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 按字段名脱敏：只换值不换键。
     */
    static String maskSensitive(String json) {
        if (json == null) {
            return null;
        }
        return SENSITIVE.matcher(json).replaceAll(m -> "\"" + m.group(1) + "\":\"****\"");
    }

    private String inferType(String declared, HttpServletRequest request, String methodName) {
        if (declared != null && !declared.isBlank()) {
            return declared.toUpperCase(Locale.ROOT);
        }
        String m = methodName.toLowerCase(Locale.ROOT);
        if (m.contains("import")) {
            return "IMPORT";
        }
        if (m.contains("export")) {
            return "EXPORT";
        }
        if (request == null) {
            return "OTHER";
        }
        return switch (request.getMethod().toUpperCase(Locale.ROOT)) {
            case "POST" -> "INSERT";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> "OTHER";
        };
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        for (String header : Arrays.asList("X-Forwarded-For", "X-Real-IP")) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) + "...(truncated)" : text;
    }
}
