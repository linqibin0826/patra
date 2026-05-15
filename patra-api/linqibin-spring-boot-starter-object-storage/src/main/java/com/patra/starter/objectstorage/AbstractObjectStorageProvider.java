package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.InvalidDownloadRequestException;
import com.patra.starter.objectstorage.domain.InvalidUploadRequestException;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import java.io.InputStream;
import java.util.regex.Pattern;

/// 对象存储提供者的抽象基类。
///
/// 封装了 MinIO 和 S3 共享的验证逻辑和常量定义，
/// 子类只需实现具体的存储操作。
///
/// **验证规则**（遵循 S3 兼容性标准）：
///
/// - 存储桶名称：3-63 字符，仅小写字母/数字/点/连字符
/// - 对象键：最大 1024 字符，不能以斜杠开头
/// - 文件大小：不超过配置的最大值
///
/// @author linqibin
/// @since 0.1.0
public abstract class AbstractObjectStorageProvider implements ObjectStorageProvider {

  /// 存储桶名称验证模式，遵循 S3 命名规则。
  ///
  /// **规则:** 3-63 个字符，仅小写字母/数字/点/连字符，必须以字母或数字开头和结尾。
  protected static final Pattern BUCKET_NAME_PATTERN =
      Pattern.compile("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$");

  /// 对象键的最大长度（S3 兼容性限制）。
  protected static final int MAX_KEY_LENGTH = 1024;

  /// 允许的最大文件大小（字节）。
  protected final long maxFileSize;

  /// 构造抽象提供者。
  ///
  /// @param maxFileSize 允许的最大文件大小（字节）
  protected AbstractObjectStorageProvider(long maxFileSize) {
    this.maxFileSize = maxFileSize;
  }

  /// 验证上传参数的 null/空检查、格式合规性和大小限制。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @param inputStream 内容流
  /// @param metadata 文件元数据
  /// @throws InvalidUploadRequestException 如果任何验证失败
  protected void validateUploadArguments(
      String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
    // Null/空检查
    if (bucket == null || bucket.isBlank()) {
      throw new InvalidUploadRequestException("存储桶名称不能为空");
    }
    if (key == null || key.isBlank()) {
      throw new InvalidUploadRequestException("对象键不能为空");
    }
    if (inputStream == null) {
      throw new InvalidUploadRequestException("输入流不能为 null");
    }
    if (metadata == null) {
      throw new InvalidUploadRequestException("对象元数据是必需的");
    }
    if (metadata.getContentLength() <= 0) {
      throw new InvalidUploadRequestException(
          String.format("内容长度必须大于 0,实际为 %d", metadata.getContentLength()));
    }

    // 存储桶格式验证
    validateBucketNameForUpload(bucket);

    // 对象键格式验证
    validateObjectKeyForUpload(key);

    // 文件大小验证
    if (metadata.getContentLength() > maxFileSize) {
      throw new InvalidUploadRequestException(
          String.format(
              "文件大小 %d 字节超过了最大允许大小 %d 字节 (%.2f MB)",
              metadata.getContentLength(), maxFileSize, maxFileSize / 1024.0 / 1024.0));
    }
  }

  /// 验证下载参数。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @throws InvalidDownloadRequestException 如果参数无效
  protected void validateDownloadArguments(String bucket, String key) {
    if (bucket == null || bucket.isBlank()) {
      throw new InvalidDownloadRequestException("存储桶名称不能为空");
    }
    if (key == null || key.isBlank()) {
      throw new InvalidDownloadRequestException("对象键不能为空");
    }

    // 存储桶格式验证
    validateBucketNameForDownload(bucket);

    // 对象键格式验证（下载时不检查连续斜杠，因为可能需要下载历史遗留数据）
    if (key.length() > MAX_KEY_LENGTH) {
      throw new InvalidDownloadRequestException(
          String.format("对象键长度超过最大值 %d 个字符,实际为 %d", MAX_KEY_LENGTH, key.length()));
    }
    if (key.startsWith("/")) {
      throw new InvalidDownloadRequestException(String.format("对象键 '%s' 不能以斜杠开头", key));
    }
  }

  /// 验证上传时的存储桶名称格式。
  ///
  /// @param bucket 存储桶名称
  /// @throws InvalidUploadRequestException 如果格式无效
  private void validateBucketNameForUpload(String bucket) {
    if (bucket.length() < 3 || bucket.length() > 63) {
      throw new InvalidUploadRequestException(
          String.format("存储桶名称长度必须在 3 到 63 个字符之间,实际为 %d", bucket.length()));
    }
    if (!BUCKET_NAME_PATTERN.matcher(bucket).matches()) {
      throw new InvalidUploadRequestException(
          String.format("存储桶名称 '%s' 无效。必须仅包含小写字母、数字、点和连字符,且必须以字母或数字开头和结尾", bucket));
    }
    if (bucket.contains("..")) {
      throw new InvalidUploadRequestException(String.format("存储桶名称 '%s' 包含连续的点,这是不允许的", bucket));
    }
  }

  /// 验证下载时的存储桶名称格式。
  ///
  /// @param bucket 存储桶名称
  /// @throws InvalidDownloadRequestException 如果格式无效
  private void validateBucketNameForDownload(String bucket) {
    if (bucket.length() < 3 || bucket.length() > 63) {
      throw new InvalidDownloadRequestException(
          String.format("存储桶名称长度必须在 3 到 63 个字符之间,实际为 %d", bucket.length()));
    }
    if (!BUCKET_NAME_PATTERN.matcher(bucket).matches()) {
      throw new InvalidDownloadRequestException(
          String.format("存储桶名称 '%s' 无效。必须仅包含小写字母、数字、点和连字符,且必须以字母或数字开头和结尾", bucket));
    }
  }

  /// 验证上传时的对象键格式。
  ///
  /// @param key 对象键
  /// @throws InvalidUploadRequestException 如果格式无效
  private void validateObjectKeyForUpload(String key) {
    if (key.length() > MAX_KEY_LENGTH) {
      throw new InvalidUploadRequestException(
          String.format("对象键长度超过最大值 %d 个字符,实际为 %d", MAX_KEY_LENGTH, key.length()));
    }
    if (key.startsWith("/")) {
      throw new InvalidUploadRequestException(String.format("对象键 '%s' 不能以斜杠开头", key));
    }
    if (key.contains("//")) {
      throw new InvalidUploadRequestException(String.format("对象键 '%s' 包含连续的斜杠,这是不允许的", key));
    }
  }
}
