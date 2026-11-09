package com.mall.common.file;

import com.mall.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 缩略图服务（P1-2 图片性能）：统一 {@code /thumb?src=&w=} 端点背后的缩放与缓存。
 *
 * <p>设计要点：
 * <ul>
 *   <li>源白名单：仅允许 {@code /uploads/} 与 {@code /img/} 前缀，且拒绝 {@code ..} 路径穿越；其余一律 400。</li>
 *   <li>缩放：等比缩放到目标宽度（JPEG 质量 0.8），PNG 原图转 JPEG 大幅降体积（JDK 自带 ImageIO，无新依赖）。</li>
 *   <li>缓存：首次生成落盘 {@code <upload-dir>/.thumbs/<w>/<源相对路径>}，之后命中缓存；classpath 的 /img 原图也缓存到同一目录。</li>
 *   <li>降级：原图损坏/解码失败/IO 异常时返回<b>原图字节</b>（原 Content-Type），不抛 500，避免列表破图。</li>
 * </ul>
 * 宽度限制在 [16, 800]，默认 300，防止被滥用放大或生成超大图。
 */
@Service
public class ThumbService {

    private final String uploadDir;

    public ThumbService(@Value("${file.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    /** 生成/命中缩略图；源不存在返回 notFound，生成失败返回原图降级 */
    public ThumbResult generate(String src, int width) {
        validate(src);
        int w = clampWidth(width);

        Path cachePath = cachePath(src, w);
        if (Files.isRegularFile(cachePath)) {
            try {
                return ThumbResult.ok(Files.readAllBytes(cachePath), "image/jpeg");
            } catch (IOException ignored) {
                // 缓存读失败则重新生成
            }
        }

        byte[] original = readOriginalBytes(src);
        if (original == null) {
            return ThumbResult.notFound();
        }

        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
            if (img == null) {
                return ThumbResult.ok(original, detectContentType(src));
            }
            int ow = img.getWidth();
            int oh = img.getHeight();
            if (ow <= 0 || oh <= 0) {
                return ThumbResult.ok(original, detectContentType(src));
            }
            int targetH = (int) Math.max(1L, Math.round(oh * (w / (double) ow)));
            BufferedImage scaled = new BufferedImage(w, targetH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, 0, 0, w, targetH, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "jpeg", baos);
            byte[] out = baos.toByteArray();

            // 落盘缓存（失败不影响本次返回）
            try {
                Files.createDirectories(cachePath.getParent());
                try (FileOutputStream fos = new FileOutputStream(cachePath.toFile())) {
                    fos.write(out);
                }
            } catch (IOException ignored) {
                // 缓存写失败仅本次不缓存，下次重新生成
            }
            return ThumbResult.ok(out, "image/jpeg");
        } catch (Exception e) {
            // 解码/缩放异常：降级返回原图，不 500
            return ThumbResult.ok(original, detectContentType(src));
        }
    }

    private void validate(String src) {
        if (src == null || src.isEmpty()) {
            throw new BusinessException(400, "缩略图源不能为空");
        }
        if (!src.startsWith("/uploads/") && !src.startsWith("/img/")) {
            throw new BusinessException(400, "缩略图仅支持 /uploads/ 与 /img/ 源");
        }
        if (src.contains("..")) {
            throw new BusinessException(400, "非法的缩略图路径");
        }
    }

    private int clampWidth(int width) {
        if (width < 16) {
            return 16;
        }
        if (width > 800) {
            return 800;
        }
        return width;
    }

    /** 解析原图字节：/uploads 读文件，/img 读 classpath:static */
    private byte[] readOriginalBytes(String src) {
        try {
            if (src.startsWith("/uploads/")) {
                File f = new File(uploadDir, src.substring("/uploads/".length()));
                if (!f.isFile()) {
                    return null;
                }
                return Files.readAllBytes(f.toPath());
            } else {
                try (InputStream is = getClass().getResourceAsStream("/static" + src)) {
                    if (is == null) {
                        return null;
                    }
                    return is.readAllBytes();
                }
            }
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    private Path cachePath(String src, int width) {
        // 源相对路径（去掉前导 /uploads 或 /img）——两者都映射到 .thumbs/<w>/<剩余路径>
        String rel = src.startsWith("/uploads/")
                ? src.substring("/uploads".length())
                : src.substring("/img".length());
        return new File(uploadDir, ".thumbs" + File.separator + width + rel).toPath();
    }

    private String detectContentType(String src) {
        if (src.endsWith(".png")) {
            return "image/png";
        }
        if (src.endsWith(".gif")) {
            return "image/gif";
        }
        if (src.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
