package com.mall.controller.shop;

import com.mall.common.context.UserContext;
import com.mall.common.file.FileRecord;
import com.mall.common.file.FileStorageService;
import com.mall.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端会员文件上传（V4 售后凭证）。
 *
 * <p>不复用 /file/upload：AuthContextFilter 对会员 token 有前台接口隔离，
 * 会员调 /file/** 一律 403（2026-10-29 实测），故开 /shop 前缀入口。
 * 落盘统一走 {@link FileStorageService}；本接口额外收紧两点：
 * <ul>
 *   <li>只放图片扩展名（售后凭证无文档场景）</li>
 *   <li>响应不暴露 sys_file 主键 id（会员不应拿到后台文件管理主键）</li>
 * </ul>
 */
@Tag(name = "商城前台-文件上传", description = "会员凭证图上传")
@RestController
@RequestMapping("/shop/upload")
public class ShopUploadController {

    /** 售后凭证仅放图片；webp/gif 同放（H5 压缩产物常见 webp） */
    private static final List<String> IMAGE_EXTS = List.of("jpg", "jpeg", "png", "webp", "gif");

    @Autowired
    private FileStorageService fileStorageService;

    @Operation(summary = "上传图片（会员）")
    @PostMapping
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        // 不在 SHOP_ANON_LIST：必须持会员 token（匿名上传是被鉴权层直接拒绝的）
        FileRecord record = fileStorageService.store(file, UserContext.getUsername(), IMAGE_EXTS);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", record.getUrl());
        data.put("name", record.getOriginalName());
        data.put("size", record.getFileSize());
        return Result.ok(data);
    }
}
