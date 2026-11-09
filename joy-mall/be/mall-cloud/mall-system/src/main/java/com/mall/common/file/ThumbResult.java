package com.mall.common.file;

/**
 * 缩略图生成结果。
 *
 * <p>三种状态：命中缓存/新生成（bytes 为 JPEG）、降级返回原图（bytes 为原图字节、contentType 为原图类型）、源不存在（notFound）。
 */
public class ThumbResult {

    private final boolean notFound;
    private final byte[] bytes;
    private final String contentType;

    private ThumbResult(boolean notFound, byte[] bytes, String contentType) {
        this.notFound = notFound;
        this.bytes = bytes;
        this.contentType = contentType;
    }

    public static ThumbResult notFound() {
        return new ThumbResult(true, null, null);
    }

    public static ThumbResult ok(byte[] bytes, String contentType) {
        return new ThumbResult(false, bytes, contentType);
    }

    public boolean isNotFound() {
        return notFound;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getContentType() {
        return contentType;
    }
}
