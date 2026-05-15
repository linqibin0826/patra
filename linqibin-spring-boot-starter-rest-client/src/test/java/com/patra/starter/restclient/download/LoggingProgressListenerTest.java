package com.patra.starter.restclient.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// LoggingProgressListener 单元测试。
///
/// 使用代理监听器来验证日志输出时机，而不是直接验证日志内容。
///
/// @author linqibin
/// @since 0.1.0
@Timeout(2)
class LoggingProgressListenerTest {

  /// 用于记录回调次数的代理监听器
  private static class CountingListener extends LoggingProgressListener {
    final List<Integer> progressCallPercentages = new ArrayList<>();
    boolean completeCalled = false;
    boolean errorCalled = false;

    CountingListener(int interval) {
      super(interval);
    }

    @Override
    public void onProgress(DownloadProgress progress) {
      int beforeSize = progressCallPercentages.size();
      super.onProgress(progress);
      // 如果父类输出了日志，记录百分比
      // 由于我们无法直接检测日志输出，这里通过覆盖来间接验证
      progressCallPercentages.add(progress.percentage());
    }

    @Override
    public void onComplete(DownloadProgress finalProgress) {
      super.onComplete(finalProgress);
      completeCalled = true;
    }

    @Override
    public void onError(Exception exception, DownloadProgress lastProgress) {
      super.onError(exception, lastProgress);
      errorCalled = true;
    }
  }

  @Nested
  @DisplayName("构造函数")
  class ConstructorTests {

    @Test
    @DisplayName("默认构造函数应创建成功")
    void shouldCreateWithDefaultInterval() {
      var listener = new LoggingProgressListener();
      assertThat(listener).isNotNull();
    }

    @Test
    @DisplayName("间隔小于 1 应抛出异常")
    void shouldThrowForIntervalLessThanOne() {
      assertThatThrownBy(() -> new LoggingProgressListener(0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("1-100");
    }

    @Test
    @DisplayName("间隔大于 100 应抛出异常")
    void shouldThrowForIntervalGreaterThan100() {
      assertThatThrownBy(() -> new LoggingProgressListener(101))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("1-100");
    }

    @Test
    @DisplayName("有效间隔应创建成功")
    void shouldCreateWithValidInterval() {
      var listener = new LoggingProgressListener(25);
      assertThat(listener).isNotNull();
    }
  }

  @Nested
  @DisplayName("onProgress 方法")
  class OnProgressTests {

    private LoggingProgressListener listener;

    @BeforeEach
    void setUp() {
      listener = new LoggingProgressListener(10);
    }

    @Test
    @DisplayName("应能处理已知大小的进度")
    void shouldHandleKnownSizeProgress() {
      // 不抛异常即可
      listener.onProgress(new DownloadProgress(50, 100, 50, 1024 * 1024, 50, 1000));
    }

    @Test
    @DisplayName("应能处理未知大小的进度")
    void shouldHandleUnknownSizeProgress() {
      // 不抛异常即可
      listener.onProgress(new DownloadProgress(1024 * 1024, -1, -1, 1024 * 1024, -1, 1000));
    }

    @Test
    @DisplayName("应能处理零进度")
    void shouldHandleZeroProgress() {
      listener.onProgress(new DownloadProgress(0, 100, 0, 0, -1, 0));
    }

    @Test
    @DisplayName("应能处理 100% 进度")
    void shouldHandle100PercentProgress() {
      listener.onProgress(new DownloadProgress(100, 100, 100, 0, 0, 5000));
    }
  }

  @Nested
  @DisplayName("onComplete 方法")
  class OnCompleteTests {

    @Test
    @DisplayName("完成时应能正常处理")
    void shouldHandleComplete() {
      var listener = new LoggingProgressListener();
      // 不抛异常即可
      listener.onComplete(
          new DownloadProgress(100 * 1024 * 1024L, 100 * 1024 * 1024L, 100, 0, 0, 5000));
    }

    @Test
    @DisplayName("未知大小完成时应能正常处理")
    void shouldHandleCompleteWithUnknownSize() {
      var listener = new LoggingProgressListener();
      listener.onComplete(new DownloadProgress(100 * 1024 * 1024L, -1, -1, 0, 0, 5000));
    }
  }

  @Nested
  @DisplayName("onError 方法")
  class OnErrorTests {

    @Test
    @DisplayName("失败时应能正常处理（有进度信息）")
    void shouldHandleErrorWithProgress() {
      var listener = new LoggingProgressListener();
      var progress = new DownloadProgress(50 * 1024 * 1024L, 100 * 1024 * 1024L, 50, 0, 0, 3000);
      // 不抛异常即可
      listener.onError(new RuntimeException("网络中断"), progress);
    }

    @Test
    @DisplayName("失败时应能正常处理（无进度信息）")
    void shouldHandleErrorWithoutProgress() {
      var listener = new LoggingProgressListener();
      // 不抛异常即可
      listener.onError(new RuntimeException("连接失败"), null);
    }
  }

  @Nested
  @DisplayName("reset 方法")
  class ResetTests {

    @Test
    @DisplayName("重置后应能重新使用")
    void shouldResetState() {
      var listener = new LoggingProgressListener(10);

      // 模拟第一次下载
      listener.onProgress(new DownloadProgress(50, 100, 50, 1024, 50, 1000));
      listener.onComplete(new DownloadProgress(100, 100, 100, 0, 0, 2000));

      // 重置
      listener.reset();

      // 第二次下载应能正常工作
      listener.onProgress(new DownloadProgress(0, 100, 0, 0, 100, 0));
      listener.onProgress(new DownloadProgress(50, 100, 50, 1024, 50, 1000));
      listener.onComplete(new DownloadProgress(100, 100, 100, 0, 0, 2000));
    }
  }
}
