package com.patra.ingest.domain.model.vo;

/**
 * Request payload for uploading content to object storage.
 *
 * @param bucket      storage bucket name
 * @param objectPath  object key (without bucket)
 * @param content     file content
 * @param contentType MIME type (for example {@code application/gzip})
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
     * Convenience factory that defaults the content type to {@code application/gzip}.
     */
    public static StorageUploadRequest gzip(String bucket, String objectPath, byte[] content) {
        return new StorageUploadRequest(bucket, objectPath, content, "application/gzip");
    }
}
