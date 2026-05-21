package dev.linqibin.starter.restclient.download;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

/// 组合进度监听器。
///
/// 将进度事件广播给多个监听器，支持同时使用日志输出和指标记录。
///
/// **使用示例**：
/// ```java
/// var listener = CompositeProgressListener.of(
///     new LoggingProgressListener(10),
///     new MetricsProgressListener(registry)
/// );
/// downloadClient.download(url, path, listener);
/// ```
///
/// **错误处理**：单个监听器的异常不会影响其他监听器的执行。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class CompositeProgressListener implements ProgressListener {

  private final List<ProgressListener> listeners;

  /// 创建组合监听器。
  ///
  /// @param listeners 监听器列表
  private CompositeProgressListener(List<ProgressListener> listeners) {
    this.listeners = List.copyOf(listeners);
  }

  /// 工厂方法：从多个监听器创建组合监听器。
  ///
  /// @param listeners 监听器数组
  /// @return 组合监听器
  public static CompositeProgressListener of(ProgressListener... listeners) {
    return new CompositeProgressListener(List.of(listeners));
  }

  /// 工厂方法：从监听器列表创建组合监听器。
  ///
  /// @param listeners 监听器列表
  /// @return 组合监听器
  public static CompositeProgressListener of(List<ProgressListener> listeners) {
    return new CompositeProgressListener(listeners);
  }

  @Override
  public void onProgress(DownloadProgress progress) {
    for (ProgressListener listener : listeners) {
      try {
        listener.onProgress(progress);
      } catch (Exception e) {
        log.warn("监听器 {} 处理进度事件时发生异常: {}", listener.getClass().getSimpleName(), e.getMessage());
      }
    }
  }

  @Override
  public void onComplete(DownloadProgress finalProgress) {
    for (ProgressListener listener : listeners) {
      try {
        listener.onComplete(finalProgress);
      } catch (Exception e) {
        log.warn("监听器 {} 处理完成事件时发生异常: {}", listener.getClass().getSimpleName(), e.getMessage());
      }
    }
  }

  @Override
  public void onError(Exception exception, DownloadProgress lastProgress) {
    for (ProgressListener listener : listeners) {
      try {
        listener.onError(exception, lastProgress);
      } catch (Exception e) {
        log.warn("监听器 {} 处理错误事件时发生异常: {}", listener.getClass().getSimpleName(), e.getMessage());
      }
    }
  }

  /// 获取包含的监听器数量。
  ///
  /// @return 监听器数量
  public int size() {
    return listeners.size();
  }
}
