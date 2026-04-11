package com.patra.catalog.domain.port.storage;

import com.patra.catalog.domain.exception.FileDownloadException;
import java.net.URI;

/// 期刊封面图下载与对象存储端口。
///
/// **职责**：从远端 URL 下载封面图后上传至对象存储，返回写入的对象键。
///
/// **实现约束**：
///
/// - Infra 层实现通过组合 `FileDownloadPort` + `ObjectStorageOperations` 完成
/// - 必须在临时文件清理上保证幂等性（try-finally）
/// - 下载失败、大小越界、上传失败统一抛出 `FileDownloadException` 并携带
///   `StandardErrorTrait` 语义
///
/// @author linqibin
/// @since 0.1.0
public interface VenueCoverImageDownloadPort {

  /// 下载远端封面图并上传到对象存储。
  ///
  /// @param sourceUrl LetPub 提供的封面图原始 URL（通常是 Aliyun OSS CDN URL）
  /// @param targetObjectKey 目标对象键（调用方决定，保证稳定性与唯一性）
  /// @return 实际写入的对象键（通常等于 `targetObjectKey`）
  /// @throws FileDownloadException 下载、大小校验或上传失败时抛出
  String downloadAndStore(URI sourceUrl, String targetObjectKey);
}
