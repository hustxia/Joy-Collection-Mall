package com.mall.common.file;

import com.mall.common.context.UserContext;
import com.mall.common.result.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传下载基座：
 * <ul>
 *   <li>POST /file/upload —— 校验白名单+大小 → yyyyMMdd/uuid.ext 落盘 → 插 sys_file</li>
 *   <li>GET /file/download/{id} —— 按 id 查库取相对路径流式下载（双重防穿越）</li>
 *   <li>POST /file/page、DELETE /file/{id} —— 文件管理（P1 页面接入）</li>
 *   <li>GET /uploads/** —— 图片静态直链（FileWebConfig 映射，不走鉴权）</li>
 * </ul>
 */
@RestController
@RequestMapping("/file")
public class FileController {

    /** 分页上限：文件管理页不需要更大，防止 pageSize 传超大值打成全表扫描 */
    private static final long MAX_PAGE_SIZE = 500;

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private FileProps props;

    @Autowired
    private FileStorageService fileStorageService;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        // 落盘/校验逻辑统一走 FileStorageService（V4 抽取，/shop/upload 与本接口同源）
        FileRecord record = fileStorageService.store(file, UserContext.getUsername(), null);
        Map<String, Object> data = new HashMap<>();
        // id 必须显式转字符串：LongIdJacksonConfig 的 Long→String 只作用于 bean 属性，
        // Map 值会原样输出 number，19 位雪花号在 JS 里静默截断（S8 实测：回传 id 查不到记录）
        data.put("id", String.valueOf(record.getId()));
        data.put("url", record.getUrl());
        data.put("name", record.getOriginalName());
        data.put("size", record.getFileSize());
        return Result.ok(data);
    }

    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        FileRecord record = fileMapper.selectById(id);
        if (record == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }
        // 双保险防穿越：normalize 后必须仍在上传目录内（relative_path 被篡改含 ../ 时拒绝）
        Path base = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
        Path target = base.resolve(record.getRelativePath()).normalize();
        if (!target.startsWith(base) || !Files.exists(target)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在或已被清理");
            return;
        }
        response.setContentType(record.getContentType() == null
                ? "application/octet-stream" : record.getContentType());
        response.setContentLengthLong(Files.size(target));
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(record.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20"));
        Files.copy(target, response.getOutputStream());
        response.getOutputStream().flush();
    }

    @PostMapping("/page")
    public Result<Page<FileRecord>> page(@RequestBody Map<String, Object> req) {
        // 分页参数容错 + 钳位：直接 Long.parseLong 遇到非法值抛 NumberFormatException（变成 500）；
        // pageSize 不设上限则可能被一次刷成全表扫描
        long pageNum = Math.max(1L, parseLong(req.get("pageNum"), 1L));
        long pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1L, parseLong(req.get("pageSize"), 10L)));
        String name = req.get("originalName") == null ? "" : String.valueOf(req.get("originalName")).trim();
        LambdaQueryWrapper<FileRecord> qw = new LambdaQueryWrapper<>();
        if (!name.isEmpty()) {
            qw.like(FileRecord::getOriginalName, name);
        }
        qw.orderByDesc(FileRecord::getId);
        return Result.ok(fileMapper.selectPage(new Page<>(pageNum, pageSize), qw));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        // 逻辑删：物理文件保留（防误删不可恢复；清理策略留给运维脚本）
        fileMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 宽松解析分页参数：null / 空串 / 非数字一律回落默认值，不抛异常。
     */
    private long parseLong(Object value, long defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
