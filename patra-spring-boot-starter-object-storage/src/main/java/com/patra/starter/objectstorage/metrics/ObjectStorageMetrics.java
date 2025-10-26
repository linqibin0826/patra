package com.patra.starter.objectstorage.metrics;

import com.patra.starter.objectstorage.ProviderType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/** Records the metrics defined in docs/object-storage-design.md §9.1.1. */
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

  public void recordUploadFailure(ProviderType providerType, String bucket) {
    if (meterRegistry == null) {
      return;
    }
    counter(providerType, bucket, "failure").increment();
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
