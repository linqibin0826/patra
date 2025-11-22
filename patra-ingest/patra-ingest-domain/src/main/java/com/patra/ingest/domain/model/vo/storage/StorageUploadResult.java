package com.patra.ingest.domain.model.vo.storage;

/// 对象存储上传结果 Value Object。
///
/// 描述文件上传到对象存储的执行结果。
///
/// **业务语义:**
///
/// - 成功上传包含对象路径和文件大小
///   - 失败上传包含错误信息,文件大小为 0
///
/// **不变性约束:** 当 `success` 为 `false` 时,`errorMessage` 应提供。
///
/// @param success 上传是否成功
/// @param objectPath 对象存储路径
/// @param fileSizeBytes 文件大小(字节)
/// @param errorMessage 错误信息(仅失败时提供)
/// @author linqibin
/// @since 0.1.0
public record StorageUploadResult(
    boolean success, String objectPath, long fileSizeBytes, String errorMessage) {
  /// 工厂方法: 创建成功的上传结果。
  public static StorageUploadResult success(String objectPath, long fileSizeBytes) {
    return new StorageUploadResult(true, objectPath, fileSizeBytes, null);
  }

  /// 工厂方法: 创建失败的上传结果。
  public static StorageUploadResult failure(String objectPath, String errorMessage) {
    return new StorageUploadResult(false, objectPath, 0, errorMessage);
  }
}
