package dev.linqibin.starter.restclient.download;

/// 下载进度监听器。
///
/// 用于接收下载过程中的进度更新事件。实现类可以选择性地处理
/// 进度更新、完成和错误事件。
///
/// **典型实现**：
/// - {@link LoggingProgressListener}：将进度输出到日志
/// - {@link MetricsProgressListener}：记录到 Micrometer 指标
/// - {@link CompositeProgressListener}：组合多个监听器
///
/// **使用示例**：
/// ```java
/// downloadClient.download(url, path, progress -> {
///     System.out.println("进度: " + progress.percentage() + "%");
/// });
/// ```
///
/// @author linqibin
/// @since 0.1.0
@FunctionalInterface
public interface ProgressListener {

  /// 进度更新回调。
  ///
  /// 在下载过程中定期调用（默认每 500ms 或进度变化时）。
  ///
  /// @param progress 当前进度信息，包含已下载字节数、速度、剩余时间等
  void onProgress(DownloadProgress progress);

  /// 下载完成回调。
  ///
  /// 在下载成功完成后调用。默认为空实现，子类可覆盖以处理完成事件。
  ///
  /// @param finalProgress 最终进度信息，包含总耗时和最终大小
  default void onComplete(DownloadProgress finalProgress) {
    // 默认空实现
  }

  /// 下载失败回调。
  ///
  /// 在下载过程中发生错误时调用。默认为空实现，子类可覆盖以处理错误事件。
  ///
  /// @param exception 发生的异常
  /// @param lastProgress 失败时的最后进度信息（可能为 null，如果在下载开始前失败）
  default void onError(Exception exception, DownloadProgress lastProgress) {
    // 默认空实现
  }
}
