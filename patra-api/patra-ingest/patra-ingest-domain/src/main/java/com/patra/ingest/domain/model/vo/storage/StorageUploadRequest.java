package com.patra.ingest.domain.model.vo.storage;

/// 对象存储上传请求 Value Object。
/// 
/// 封装上传文件到对象存储(如 OSS/S3)所需的完整信息。
/// 
/// **业务语义:**
/// 
/// - bucket - 存储桶名称
///   - objectPath - 对象键(不含桶名)
///   - content - 文件内容(字节数组)
///   - contentType - MIME 类型(如 `application/gzip`)
/// 
/// **常见用途:** 上传采集的出版物数据到对象存储,通常使用 gzip 压缩。
/// 
/// @param bucket 存储桶名称
/// @param objectPath 对象键(不含桶名)
/// @param content 文件内容
/// @param contentType MIME 类型(例如 `application/gzip`)
/// @author linqibin
/// @since 0.1.0
public record StorageUploadRequest(
    String bucket, String objectPath, byte[] content, String contentType) {
  /// 工厂方法: 创建 gzip 压缩格式的上传请求(默认 contentType 为 `application/gzip`)。
  public static StorageUploadRequest gzip(String bucket, String objectPath, byte[] content) {
    return new StorageUploadRequest(bucket, objectPath, content, "application/gzip");
  }
}
