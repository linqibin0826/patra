package dev.linqibin.starter.restclient.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// CompositeProgressListener 单元测试。
///
/// @author linqibin
/// @since 0.1.0
class CompositeProgressListenerTest {

  @Nested
  @DisplayName("工厂方法")
  class FactoryMethodTests {

    @Test
    @DisplayName("of(varargs) 应创建组合监听器")
    void shouldCreateFromVarargs() {
      var listener1 = mock(ProgressListener.class);
      var listener2 = mock(ProgressListener.class);

      var composite = CompositeProgressListener.of(listener1, listener2);

      assertThat(composite.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("of(List) 应创建组合监听器")
    void shouldCreateFromList() {
      var listener1 = mock(ProgressListener.class);
      var listener2 = mock(ProgressListener.class);

      var composite = CompositeProgressListener.of(List.of(listener1, listener2));

      assertThat(composite.size()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("onProgress 方法")
  class OnProgressTests {

    @Test
    @DisplayName("应广播给所有监听器")
    void shouldBroadcastToAllListeners() {
      var listener1 = mock(ProgressListener.class);
      var listener2 = mock(ProgressListener.class);
      var composite = CompositeProgressListener.of(listener1, listener2);

      var progress = new DownloadProgress(50, 100, 50, 1024, 50, 1000);
      composite.onProgress(progress);

      verify(listener1).onProgress(progress);
      verify(listener2).onProgress(progress);
    }

    @Test
    @DisplayName("单个监听器异常不应影响其他监听器")
    void shouldContinueOnException() {
      var listener1 = mock(ProgressListener.class);
      var listener2 = mock(ProgressListener.class);
      var progress = new DownloadProgress(50, 100, 50, 1024, 50, 1000);

      doThrow(new RuntimeException("测试异常")).when(listener1).onProgress(progress);

      var composite = CompositeProgressListener.of(listener1, listener2);
      composite.onProgress(progress);

      // listener2 仍应被调用
      verify(listener2).onProgress(progress);
    }
  }

  @Nested
  @DisplayName("onComplete 方法")
  class OnCompleteTests {

    @Test
    @DisplayName("应广播给所有监听器")
    void shouldBroadcastComplete() {
      var listener1 = mock(ProgressListener.class);
      var listener2 = mock(ProgressListener.class);
      var composite = CompositeProgressListener.of(listener1, listener2);

      var progress = new DownloadProgress(100, 100, 100, 0, 0, 5000);
      composite.onComplete(progress);

      verify(listener1).onComplete(progress);
      verify(listener2).onComplete(progress);
    }

    @Test
    @DisplayName("单个监听器异常不应影响其他监听器")
    void shouldContinueOnException() {
      var listener1 = mock(ProgressListener.class);
      var listener2 = mock(ProgressListener.class);
      var progress = new DownloadProgress(100, 100, 100, 0, 0, 5000);

      doThrow(new RuntimeException("测试异常")).when(listener1).onComplete(progress);

      var composite = CompositeProgressListener.of(listener1, listener2);
      composite.onComplete(progress);

      verify(listener2).onComplete(progress);
    }
  }

  @Nested
  @DisplayName("onError 方法")
  class OnErrorTests {

    @Test
    @DisplayName("应广播给所有监听器")
    void shouldBroadcastError() {
      var listener1 = mock(ProgressListener.class);
      var listener2 = mock(ProgressListener.class);
      var composite = CompositeProgressListener.of(listener1, listener2);

      var exception = new RuntimeException("下载失败");
      var progress = new DownloadProgress(50, 100, 50, 0, 0, 2000);
      composite.onError(exception, progress);

      verify(listener1).onError(exception, progress);
      verify(listener2).onError(exception, progress);
    }

    @Test
    @DisplayName("单个监听器异常不应影响其他监听器")
    void shouldContinueOnException() {
      var listener1 = mock(ProgressListener.class);
      var listener2 = mock(ProgressListener.class);
      var exception = new RuntimeException("下载失败");
      var progress = new DownloadProgress(50, 100, 50, 0, 0, 2000);

      doThrow(new RuntimeException("测试异常")).when(listener1).onError(exception, progress);

      var composite = CompositeProgressListener.of(listener1, listener2);
      composite.onError(exception, progress);

      verify(listener2).onError(exception, progress);
    }
  }
}
