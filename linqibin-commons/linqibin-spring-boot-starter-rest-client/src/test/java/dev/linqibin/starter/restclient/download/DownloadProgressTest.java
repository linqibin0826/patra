package dev.linqibin.starter.restclient.download;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// DownloadProgress 单元测试。
///
/// @author linqibin
/// @since 0.1.0
class DownloadProgressTest {

  @Nested
  @DisplayName("formattedSpeed 方法")
  class FormattedSpeedTests {

    @Test
    @DisplayName("应正确格式化字节级速度")
    void shouldFormatBytesPerSecond() {
      var progress = new DownloadProgress(0, 0, 0, 500, 0, 0);
      assertThat(progress.formattedSpeed()).isEqualTo("500 B/s");
    }

    @Test
    @DisplayName("应正确格式化 KB 级速度")
    void shouldFormatKiloBytesPerSecond() {
      var progress = new DownloadProgress(0, 0, 0, 1024 * 10, 0, 0);
      assertThat(progress.formattedSpeed()).isEqualTo("10 KB/s");
    }

    @Test
    @DisplayName("应正确格式化 MB 级速度")
    void shouldFormatMegaBytesPerSecond() {
      var progress = new DownloadProgress(0, 0, 0, 1024 * 1024 * 15, 0, 0);
      assertThat(progress.formattedSpeed()).isEqualTo("15 MB/s");
    }

    @Test
    @DisplayName("应正确格式化 GB 级速度")
    void shouldFormatGigaBytesPerSecond() {
      var progress = new DownloadProgress(0, 0, 0, 1024L * 1024 * 1024 * 2, 0, 0);
      assertThat(progress.formattedSpeed()).isEqualTo("2 GB/s");
    }

    @Test
    @DisplayName("应正确格式化带小数的速度")
    void shouldFormatDecimalSpeed() {
      var progress = new DownloadProgress(0, 0, 0, (long) (1024 * 1024 * 12.5), 0, 0);
      assertThat(progress.formattedSpeed()).isEqualTo("12.5 MB/s");
    }
  }

  @Nested
  @DisplayName("formattedRemainingTime 方法")
  class FormattedRemainingTimeTests {

    @Test
    @DisplayName("未知剩余时间应显示'未知'")
    void shouldShowUnknownForNegativeTime() {
      var progress = new DownloadProgress(0, 0, 0, 0, -1, 0);
      assertThat(progress.formattedRemainingTime()).isEqualTo("未知");
    }

    @Test
    @DisplayName("应正确格式化秒级剩余时间")
    void shouldFormatSeconds() {
      var progress = new DownloadProgress(0, 0, 0, 0, 45, 0);
      assertThat(progress.formattedRemainingTime()).isEqualTo("45s");
    }

    @Test
    @DisplayName("应正确格式化分钟级剩余时间")
    void shouldFormatMinutes() {
      var progress = new DownloadProgress(0, 0, 0, 0, 150, 0);
      assertThat(progress.formattedRemainingTime()).isEqualTo("2m 30s");
    }

    @Test
    @DisplayName("应正确格式化整分钟")
    void shouldFormatExactMinutes() {
      var progress = new DownloadProgress(0, 0, 0, 0, 120, 0);
      assertThat(progress.formattedRemainingTime()).isEqualTo("2m");
    }

    @Test
    @DisplayName("应正确格式化小时级剩余时间")
    void shouldFormatHours() {
      var progress = new DownloadProgress(0, 0, 0, 0, 3600 + 900, 0);
      assertThat(progress.formattedRemainingTime()).isEqualTo("1h 15m");
    }

    @Test
    @DisplayName("应正确格式化整小时")
    void shouldFormatExactHours() {
      var progress = new DownloadProgress(0, 0, 0, 0, 7200, 0);
      assertThat(progress.formattedRemainingTime()).isEqualTo("2h");
    }
  }

  @Nested
  @DisplayName("formattedSize 方法")
  class FormattedSizeTests {

    @Test
    @DisplayName("应正确格式化已知大小的进度")
    void shouldFormatKnownSize() {
      long downloaded = 150 * 1024 * 1024L;
      long total = 300 * 1024 * 1024L;
      var progress = new DownloadProgress(downloaded, total, 50, 0, 0, 0);
      assertThat(progress.formattedSize()).isEqualTo("150 MB / 300 MB (50%)");
    }

    @Test
    @DisplayName("未知总大小应显示'未知'")
    void shouldShowUnknownForUnknownTotal() {
      var progress = new DownloadProgress(50 * 1024 * 1024L, -1, -1, 0, 0, 0);
      assertThat(progress.formattedSize()).isEqualTo("50 MB / 未知");
    }

    @Test
    @DisplayName("零总大小应显示'未知'")
    void shouldShowUnknownForZeroTotal() {
      var progress = new DownloadProgress(50 * 1024 * 1024L, 0, -1, 0, 0, 0);
      assertThat(progress.formattedSize()).isEqualTo("50 MB / 未知");
    }
  }

  @Nested
  @DisplayName("formattedElapsedTime 方法")
  class FormattedElapsedTimeTests {

    @Test
    @DisplayName("应正确格式化秒级已用时间")
    void shouldFormatSecondsWithDecimal() {
      var progress = new DownloadProgress(0, 0, 0, 0, 0, 12500);
      assertThat(progress.formattedElapsedTime()).isEqualTo("12.5s");
    }

    @Test
    @DisplayName("应正确格式化分钟级已用时间")
    void shouldFormatMinutesElapsed() {
      var progress = new DownloadProgress(0, 0, 0, 0, 0, 150000);
      assertThat(progress.formattedElapsedTime()).isEqualTo("2m 30s");
    }
  }

  @Nested
  @DisplayName("状态判断方法")
  class StateTests {

    @Test
    @DisplayName("总大小已知时 isTotalBytesKnown 应返回 true")
    void shouldReturnTrueWhenTotalKnown() {
      var progress = new DownloadProgress(0, 100, 0, 0, 0, 0);
      assertThat(progress.isTotalBytesKnown()).isTrue();
    }

    @Test
    @DisplayName("总大小未知时 isTotalBytesKnown 应返回 false")
    void shouldReturnFalseWhenTotalUnknown() {
      var progress = new DownloadProgress(0, -1, 0, 0, 0, 0);
      assertThat(progress.isTotalBytesKnown()).isFalse();
    }

    @Test
    @DisplayName("下载完成时 isComplete 应返回 true")
    void shouldReturnTrueWhenComplete() {
      var progress = new DownloadProgress(100, 100, 100, 0, 0, 0);
      assertThat(progress.isComplete()).isTrue();
    }

    @Test
    @DisplayName("下载未完成时 isComplete 应返回 false")
    void shouldReturnFalseWhenNotComplete() {
      var progress = new DownloadProgress(50, 100, 50, 0, 0, 0);
      assertThat(progress.isComplete()).isFalse();
    }

    @Test
    @DisplayName("总大小未知时 isComplete 应返回 false")
    void shouldReturnFalseWhenTotalUnknownForComplete() {
      var progress = new DownloadProgress(100, -1, -1, 0, 0, 0);
      assertThat(progress.isComplete()).isFalse();
    }
  }
}
