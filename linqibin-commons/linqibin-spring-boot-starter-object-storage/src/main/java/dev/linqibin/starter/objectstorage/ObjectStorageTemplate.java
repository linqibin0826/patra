package dev.linqibin.starter.objectstorage;

import dev.linqibin.starter.objectstorage.domain.DownloadFailedException;
import dev.linqibin.starter.objectstorage.domain.DownloadResult;
import dev.linqibin.starter.objectstorage.domain.InvalidDownloadRequestException;
import dev.linqibin.starter.objectstorage.domain.InvalidUploadRequestException;
import dev.linqibin.starter.objectstorage.domain.ObjectInfo;
import dev.linqibin.starter.objectstorage.domain.ObjectMetadata;
import dev.linqibin.starter.objectstorage.domain.ObjectNotFoundException;
import dev.linqibin.starter.objectstorage.domain.UploadFailedException;
import dev.linqibin.starter.objectstorage.domain.UploadResult;
import dev.linqibin.starter.objectstorage.metrics.ObjectStorageMetrics;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import org.springframework.retry.support.RetryTemplate;

/// 对象存储模板类,包装提供者操作并增强重试和可观测性能力。
///
/// 此类是 {@link ObjectStorageOperations} 的默认实现,负责:
///
/// - 集成 {@link RetryTemplate},自动重试瞬时故障
///   - 集成 {@link ObjectStorageMetrics},记录上传成功/失败/重试指标
///   - 分类异常类型(validation/network/auth/unknown)用于指标标签
///   - 统一异常处理,确保所有上传失败都包装为 {@link UploadFailedException}
///
/// **使用示例:**
///
/// ```java
/// @Autowired
/// private ObjectStorageOperations objectStorageOps;
///
/// ObjectMetadata metadata = ObjectMetadata.builder()
///     .contentLength(file.getSize())
///     .contentType("application/pdf")
///     .build();
///
/// UploadResult result = objectStorageOps.upload("documents", "file.pdf", inputStream, metadata);
/// ```
public class ObjectStorageTemplate implements ObjectStorageOperations {

  private final ObjectStorageProvider provider;
  private final RetryTemplate retryTemplate;
  private final ObjectStorageMetrics metrics;

  /// 构造对象存储模板。
  ///
  /// @param provider 对象存储提供者(MinIO 或 S3)
  /// @param retryTemplate 重试模板
  /// @param metrics 指标收集器
  public ObjectStorageTemplate(
      ObjectStorageProvider provider, RetryTemplate retryTemplate, ObjectStorageMetrics metrics) {
    this.provider = provider;
    this.retryTemplate = retryTemplate;
    this.metrics = metrics;
  }

  /// 上传文件到对象存储,自动重试瞬时故障并记录指标。
  ///
  /// **重试逻辑:** 仅重试网络错误(IOException、SocketTimeoutException 等), 不重试验证错误({@link
  /// InvalidUploadRequestException})。
  ///
  /// **指标收集:** 记录上传时长、文件大小、成功/失败率、重试次数等。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @param inputStream 内容流
  /// @param metadata 元数据
  /// @return 上传结果
  /// @throws InvalidUploadRequestException 如果参数验证失败(不会重试)
  /// @throws UploadFailedException 如果上传失败(网络错误会自动重试)
  @Override
  public UploadResult upload(
      String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
    return retryTemplate.execute(
        context -> {
          long start = System.nanoTime();
          try {
            UploadResult result = provider.upload(bucket, key, inputStream, metadata);
            metrics.recordUploadSuccess(
                provider.getProviderType(),
                bucket,
                System.nanoTime() - start,
                result.getFileSize());
            return result;
          } catch (Exception ex) {
            String errorType = classifyError(ex);
            metrics.recordUploadFailure(provider.getProviderType(), bucket, errorType);
            if (ex instanceof UploadFailedException uploadFailedException) {
              throw uploadFailedException;
            }
            throw new UploadFailedException("上传失败", ex);
          } finally {
            if (context.getRetryCount() > 0) {
              metrics.recordRetry(provider.getProviderType(), bucket, context.getRetryCount());
            }
          }
        });
  }

  /// 下载对象内容（流式）。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @return 下载结果
  /// @throws ObjectNotFoundException 如果对象不存在
  /// @throws DownloadFailedException 如果下载失败
  @Override
  public DownloadResult download(String bucket, String key) {
    return retryTemplate.execute(
        context -> {
          long start = System.nanoTime();
          try {
            DownloadResult result = provider.download(bucket, key);
            metrics.recordDownloadSuccess(
                provider.getProviderType(),
                bucket,
                System.nanoTime() - start,
                result.getContentLength());
            return result;
          } catch (Exception ex) {
            String errorType = classifyDownloadError(ex);
            metrics.recordDownloadFailure(provider.getProviderType(), bucket, errorType);
            if (ex instanceof DownloadFailedException downloadFailedException) {
              throw downloadFailedException;
            }
            throw new DownloadFailedException("下载失败", ex);
          } finally {
            if (context.getRetryCount() > 0) {
              metrics.recordRetry(provider.getProviderType(), bucket, context.getRetryCount());
            }
          }
        });
  }

  /// 下载对象到指定文件路径。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @param targetPath 目标文件路径
  /// @return 目标文件路径
  /// @throws ObjectNotFoundException 如果对象不存在
  /// @throws DownloadFailedException 如果下载失败
  @Override
  public Path downloadToFile(String bucket, String key, Path targetPath) {
    return retryTemplate.execute(
        context -> {
          long start = System.nanoTime();
          try {
            Path result = provider.downloadToFile(bucket, key, targetPath);
            // 获取文件大小用于指标
            long fileSize = java.nio.file.Files.size(result);
            metrics.recordDownloadSuccess(
                provider.getProviderType(), bucket, System.nanoTime() - start, fileSize);
            return result;
          } catch (Exception ex) {
            String errorType = classifyDownloadError(ex);
            metrics.recordDownloadFailure(provider.getProviderType(), bucket, errorType);
            if (ex instanceof DownloadFailedException downloadFailedException) {
              throw downloadFailedException;
            }
            throw new DownloadFailedException("下载到文件失败", ex);
          } finally {
            if (context.getRetryCount() > 0) {
              metrics.recordRetry(provider.getProviderType(), bucket, context.getRetryCount());
            }
          }
        });
  }

  /// 获取对象元数据（HEAD 请求）。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @return 对象元数据,如果对象不存在则返回空 Optional
  @Override
  public Optional<ObjectInfo> statObject(String bucket, String key) {
    return retryTemplate.execute(
        context -> {
          try {
            return provider.statObject(bucket, key);
          } catch (Exception ex) {
            String errorType = classifyDownloadError(ex);
            metrics.recordDownloadFailure(provider.getProviderType(), bucket, errorType);
            if (ex instanceof DownloadFailedException downloadFailedException) {
              throw downloadFailedException;
            }
            throw new DownloadFailedException("获取对象信息失败", ex);
          } finally {
            if (context.getRetryCount() > 0) {
              metrics.recordRetry(provider.getProviderType(), bucket, context.getRetryCount());
            }
          }
        });
  }

  /// 将异常分类为错误类型,用于指标标签。
  ///
  /// **分类规则:**
  ///
  /// - "validation" - {@link InvalidUploadRequestException},参数验证失败
  ///   - "auth" - 异常消息包含 "auth" 或 "credential",认证/授权失败
  ///   - "network" - IOException、SocketException、SocketTimeoutException,网络错误
  ///   - "unknown" - 其他未知错误
  ///
  /// @param ex 待分类的异常
  /// @return 错误类型字符串: "validation"、"network"、"auth" 或 "unknown"
  private String classifyError(Exception ex) {
    if (ex instanceof InvalidUploadRequestException) {
      return "validation";
    }

    // 检查异常消息中的常见模式
    String message = ex.getMessage();
    if (message != null) {
      String lowerMessage = message.toLowerCase(Locale.ROOT);
      if (lowerMessage.contains("auth") || lowerMessage.contains("credential")) {
        return "auth";
      }
      if (lowerMessage.contains("timeout")
          || lowerMessage.contains("connection")
          || lowerMessage.contains("network")) {
        return "network";
      }
    }

    // 检查异常类型
    if (ex instanceof IOException
        || ex instanceof SocketException
        || ex instanceof SocketTimeoutException) {
      return "network";
    }

    return "unknown";
  }

  /// 将下载相关异常分类为错误类型,用于指标标签。
  ///
  /// @param ex 待分类的异常
  /// @return 错误类型字符串
  private String classifyDownloadError(Exception ex) {
    if (ex instanceof InvalidDownloadRequestException) {
      return "validation";
    }
    if (ex instanceof ObjectNotFoundException) {
      return "not_found";
    }

    // 复用上传的分类逻辑
    return classifyError(ex);
  }
}
