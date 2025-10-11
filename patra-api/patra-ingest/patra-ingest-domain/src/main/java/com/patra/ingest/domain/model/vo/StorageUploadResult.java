package com.patra.ingest.domain.model.vo;

/**
 * Result of uploading data to object storage.
 *
 * @param success       indicates whether the upload succeeded
 * @param objectPath    object storage path
 * @param fileSizeBytes size in bytes
 * @param errorMessage  error message when {@code success} is false
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
     * Convenience factory for a successful upload.
     */
    public static StorageUploadResult success(String objectPath, long fileSizeBytes) {
        return new StorageUploadResult(true, objectPath, fileSizeBytes, null);
    }

    /**
     * Convenience factory for a failed upload.
     */
    public static StorageUploadResult failure(String objectPath, String errorMessage) {
        return new StorageUploadResult(false, objectPath, 0, errorMessage);
    }
}
