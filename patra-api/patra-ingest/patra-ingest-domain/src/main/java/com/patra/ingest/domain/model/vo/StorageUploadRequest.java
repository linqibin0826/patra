package com.patra.ingest.domain.model.vo;

/**
 * 对象存储上传请求。
 *
 * @param bucket 存储桶名称
 * @param objectPath 对象路径（不含bucket）
 * @param content 文件内容（字节数组）
 * @param contentType 内容类型（如 application/gzip）
 * @author linqibin
 * @since 0.1.0
 */
public record StorageUploadRequest(
        String bucket,
        String objectPath,
        byte[] content,
        String contentType
) {
    /**
     * 快捷构造（默认gzip类型）。
     */
    public static StorageUploadRequest gzip(String bucket, String objectPath, byte[] content) {
        return new StorageUploadRequest(bucket, objectPath, content, "application/gzip");
    }
}
