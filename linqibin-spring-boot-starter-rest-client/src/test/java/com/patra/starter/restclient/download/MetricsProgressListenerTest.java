package com.patra.starter.restclient.download;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// MetricsProgressListener 单元测试。
///
/// 验证 Micrometer 指标记录的正确性。
///
/// @author linqibin
/// @since 0.1.0
@Timeout(2)
class MetricsProgressListenerTest {

  private SimpleMeterRegistry registry;
  private MetricsProgressListener listener;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    listener = new MetricsProgressListener(registry);
  }

  @Nested
  @DisplayName("onProgress 方法")
  class OnProgressTests {

    @Test
    @DisplayName("不应记录任何额外指标（设计决策）")
    void shouldNotRecordAnyMetrics() {
      // Given
      var progress =
          new DownloadProgress(50 * 1024 * 1024L, 100 * 1024 * 1024L, 50, 1024 * 1024, 50, 5000);

      // 记录调用前的 Meter 状态
      Timer durationTimer = registry.find("download.duration.seconds").timer();
      Counter bytesCounter = registry.find("download.bytes.total").counter();
      Counter successCounter =
          registry.find("download.count.total").tag("status", "success").counter();

      long initialTimerCount = durationTimer != null ? durationTimer.count() : 0;
      double initialBytesCount = bytesCounter != null ? bytesCounter.count() : 0;
      double initialSuccessCount = successCounter != null ? successCounter.count() : 0;

      // When
      listener.onProgress(progress);

      // Then - 指标值不应变化（onProgress 不记录任何指标）
      assertThat(durationTimer.count()).isEqualTo(initialTimerCount);
      assertThat(bytesCounter.count()).isEqualTo(initialBytesCount);
      assertThat(successCounter.count()).isEqualTo(initialSuccessCount);
    }
  }

  @Nested
  @DisplayName("onComplete 方法")
  class OnCompleteTests {

    @Test
    @DisplayName("应记录下载耗时 Timer")
    void shouldRecordDurationTimer() {
      // Given
      var progress = new DownloadProgress(100 * 1024 * 1024L, 100 * 1024 * 1024L, 100, 0, 0, 5000);

      // When
      listener.onComplete(progress);

      // Then
      Timer timer = registry.find("download.duration.seconds").timer();
      assertThat(timer).isNotNull();
      assertThat(timer.count()).isEqualTo(1);
      assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(5000);
    }

    @Test
    @DisplayName("应记录累计下载字节数 Counter")
    void shouldRecordBytesCounter() {
      // Given
      long downloadedBytes = 100 * 1024 * 1024L; // 100 MB
      var progress = new DownloadProgress(downloadedBytes, downloadedBytes, 100, 0, 0, 5000);

      // When
      listener.onComplete(progress);

      // Then
      Counter counter = registry.find("download.bytes.total").counter();
      assertThat(counter).isNotNull();
      assertThat(counter.count()).isEqualTo(downloadedBytes);
    }

    @Test
    @DisplayName("应记录成功次数 Counter")
    void shouldRecordSuccessCount() {
      // Given
      var progress = new DownloadProgress(1024, 1024, 100, 0, 0, 1000);

      // When
      listener.onComplete(progress);

      // Then
      Counter counter = registry.find("download.count.total").tag("status", "success").counter();
      assertThat(counter).isNotNull();
      assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("多次完成应累加计数")
    void shouldAccumulateOnMultipleCompletes() {
      // Given
      var progress1 = new DownloadProgress(1024, 1024, 100, 0, 0, 1000);
      var progress2 = new DownloadProgress(2048, 2048, 100, 0, 0, 2000);

      // When
      listener.onComplete(progress1);
      listener.onComplete(progress2);

      // Then
      Counter counter = registry.find("download.count.total").tag("status", "success").counter();
      assertThat(counter).isNotNull();
      assertThat(counter.count()).isEqualTo(2);

      Counter bytesCounter = registry.find("download.bytes.total").counter();
      assertThat(bytesCounter.count()).isEqualTo(1024 + 2048);
    }
  }

  @Nested
  @DisplayName("onError 方法")
  class OnErrorTests {

    @Test
    @DisplayName("SocketTimeoutException 应标记为 timeout")
    void shouldClassifySocketTimeoutAsTimeout() {
      // Given
      var exception = new SocketTimeoutException("Read timed out");
      var progress = new DownloadProgress(50 * 1024L, 100 * 1024L, 50, 0, 0, 3000);

      // When
      listener.onError(exception, progress);

      // Then
      Counter counter =
          registry.find("download.failure.total").tag("error_type", "timeout").counter();
      assertThat(counter).isNotNull();
      assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("IOException 应标记为 io_error")
    void shouldClassifyIOExceptionAsIOError() {
      // Given
      var exception = new IOException("Connection reset");
      var progress = new DownloadProgress(50 * 1024L, 100 * 1024L, 50, 0, 0, 3000);

      // When
      listener.onError(exception, progress);

      // Then
      Counter counter =
          registry.find("download.failure.total").tag("error_type", "io_error").counter();
      assertThat(counter).isNotNull();
      assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("DownloadException 应标记为 download_error")
    void shouldClassifyDownloadExceptionAsDownloadError() {
      // Given
      var exception = DownloadException.httpError(404);
      var progress = new DownloadProgress(0, -1, 0, 0, -1, 1000);

      // When
      listener.onError(exception, progress);

      // Then
      Counter counter =
          registry.find("download.failure.total").tag("error_type", "download_error").counter();
      assertThat(counter).isNotNull();
      assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("其他异常应标记为 unknown")
    void shouldClassifyOtherExceptionAsUnknown() {
      // Given
      var exception = new IllegalStateException("Unexpected error");
      var progress = new DownloadProgress(0, -1, 0, 0, -1, 500);

      // When
      listener.onError(exception, progress);

      // Then
      Counter counter =
          registry.find("download.failure.total").tag("error_type", "unknown").counter();
      assertThat(counter).isNotNull();
      assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("应记录失败次数 Counter")
    void shouldRecordFailureCount() {
      // Given
      var exception = new RuntimeException("Test error");
      var progress = new DownloadProgress(0, 100, 0, 0, -1, 100);

      // When
      listener.onError(exception, progress);

      // Then
      Counter counter = registry.find("download.count.total").tag("status", "failure").counter();
      assertThat(counter).isNotNull();
      assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("多次失败应累加计数")
    void shouldAccumulateOnMultipleErrors() {
      // Given
      var exception1 = new SocketTimeoutException("Timeout 1");
      var exception2 = new IOException("IO Error");

      // When
      listener.onError(exception1, null);
      listener.onError(exception2, null);

      // Then
      Counter failureCounter =
          registry.find("download.count.total").tag("status", "failure").counter();
      assertThat(failureCounter).isNotNull();
      assertThat(failureCounter.count()).isEqualTo(2);

      // 按类型分组
      Counter timeoutCounter =
          registry.find("download.failure.total").tag("error_type", "timeout").counter();
      Counter ioCounter =
          registry.find("download.failure.total").tag("error_type", "io_error").counter();
      assertThat(timeoutCounter.count()).isEqualTo(1);
      assertThat(ioCounter.count()).isEqualTo(1);
    }
  }
}
