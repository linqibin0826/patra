package com.patra.ingest.domain.model.vo;

/**
 * 对象存储上传结果。
 *
 * @param success 是否成功
 * @param objectPath 对象路径
 * @param fileSizeBytes 文件大小（字节）
 * @param errorMessage 错误信息（失败时）
 * @author linqibin
 * @since 0.1.0
 */
public record StorageUploadResult(
        boolean success,
        String objectPath,
        long fileSizeBytes,
        String errorMessage
) {
    /**
     * 成功结果。
     */
    public static StorageUploadResult success(String objectPath, long fileSizeBytes) {
        return new StorageUploadResult(true, objectPath, fileSizeBytes, null);
    }

    /**
     * 失败结果。
     */
    public static StorageUploadResult failure(String objectPath, String errorMessage) {
        return new StorageUploadResult(false, objectPath, 0, errorMessage);
    }
}
