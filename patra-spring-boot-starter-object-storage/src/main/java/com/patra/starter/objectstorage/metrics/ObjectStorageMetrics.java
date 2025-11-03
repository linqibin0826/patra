package com.patra.starter.objectstorage.metrics;

import com.patra.starter.objectstorage.ProviderType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/**
 * 对象存储指标收集器,记录上传/下载操作的性能和成功率。
 *
 * <p><b>收集的指标:</b>
 *
 * <ul>
 *   <li>{@code patra.object_storage.upload.total} - 上传总数(成功/失败)
 *   <li>{@code patra.object_storage.upload.duration} - 上传时长分布
 *   <li>{@code patra.object_storage.upload.size} - 上传文件大小分布
 *   <li>{@code patra.object_storage.download.total} - 下载总数
 *   <li>{@code patra.object_storage.retry.count} - 重试次数
 * </ul>
 *
 * <p><b>指标标签:</b>
 * provider(minio/s3)、bucket、status(success/failure)、error_type(validation/network/auth/unknown)
 */
public class ObjectStorageMetrics {

  private static final String UPLOAD_TOTAL = "patra.object_storage.upload.total";
  private static final String UPLOAD_DURATION = "patra.object_storage.upload.duration";
  private static final String UPLOAD_SIZE = "patra.object_storage.upload.size";
  private static final String DOWNLOAD_TOTAL = "patra.object_storage.download.total";
  private static final String RETRY_COUNT = "patra.object_storage.retry.count";

  private final MeterRegistry meterRegistry;

  public ObjectStorageMetrics() {
    this(null);
  }

  public ObjectStorageMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordUploadSuccess(
      ProviderType providerType, String bucket, long durationNanos, long fileSize) {
    if (meterRegistry == null) {
      return;
    }
    counter(providerType, bucket, "success").increment();
    timer(providerType, bucket).record(durationNanos, TimeUnit.NANOSECONDS);
    summary(providerType, bucket).record(Math.max(0, fileSize));
  }

  /**
   * 记录上传失败并分类错误类型。
   *
   * @param providerType 存储提供者类型
   * @param bucket 存储桶名称
   * @param errorType 错误分类(例如 "validation"、"network"、"auth"、"unknown")
   */
  public void recordUploadFailure(ProviderType providerType, String bucket, String errorType) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder(UPLOAD_TOTAL)
        .tags(
            "provider",
            providerType.name().toLowerCase(),
            "bucket",
            bucket,
            "status",
            "failure",
            "error_type",
            errorType)
        .register(meterRegistry)
        .increment();
  }

  public void recordRetry(ProviderType providerType, String bucket, int retryCount) {
    if (meterRegistry == null || retryCount <= 0) {
      return;
    }
    Counter.builder(RETRY_COUNT)
        .tags("provider", providerType.name().toLowerCase(), "bucket", bucket)
        .register(meterRegistry)
        .increment(retryCount);
  }

  public void recordDownload(ProviderType providerType, String bucket) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder(DOWNLOAD_TOTAL)
        .tags("provider", providerType.name().toLowerCase(), "bucket", bucket)
        .register(meterRegistry)
        .increment();
  }

  private Counter counter(ProviderType providerType, String bucket, String status) {
    return Counter.builder(UPLOAD_TOTAL)
        .tags("provider", providerType.name().toLowerCase(), "bucket", bucket, "status", status)
        .register(meterRegistry);
  }

  private Timer timer(ProviderType providerType, String bucket) {
    return Timer.builder(UPLOAD_DURATION)
        .tags("provider", providerType.name().toLowerCase(), "bucket", bucket)
        .publishPercentileHistogram()
        .publishPercentiles(0.5, 0.9, 0.99)
        .register(meterRegistry);
  }

  private DistributionSummary summary(ProviderType providerType, String bucket) {
    return DistributionSummary.builder(UPLOAD_SIZE)
        .tags("provider", providerType.name().toLowerCase(), "bucket", bucket)
        .publishPercentileHistogram()
        .register(meterRegistry);
  }
}
