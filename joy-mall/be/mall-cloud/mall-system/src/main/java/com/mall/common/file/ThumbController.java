package com.mall.common.file;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 统一缩略图端点（P1-2）：{@code GET /thumb?src=<相对路径>&w=300} → image/jpeg。
 *
 * <p>前端列表/卡片商品图统一引用本端点缩小体积（首屏目标 &lt;800KB），详情页保持原图清晰。
 * 列入 {@code AuthContextFilter} 白名单（img 标签直链不带 token）。
 */
@RestController
@RequestMapping("/thumb")
public class ThumbController {

    @Autowired
    private ThumbService thumbService;

    @GetMapping
    public ResponseEntity<byte[]> thumb(@RequestParam String src,
                                        @RequestParam(defaultValue = "300") int w,
                                        HttpServletResponse response) throws IOException {
        ThumbResult result = thumbService.generate(src, w);
        if (result.isNotFound()) {
            return ResponseEntity.notFound().build();
        }
        response.setHeader("Cache-Control", "public, max-age=86400");
        return ResponseEntity.ok()
                .header("Content-Type", result.getContentType())
                .body(result.getBytes());
    }
}
