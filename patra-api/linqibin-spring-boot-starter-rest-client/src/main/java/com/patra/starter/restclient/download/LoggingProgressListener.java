package com.patra.starter.restclient.download;

import lombok.extern.slf4j.Slf4j;

/// 日志输出进度监听器。
///
/// 按配置的间隔输出进度日志，避免日志刷屏。默认每 10% 进度输出一条日志。
///
/// **日志输出示例**：
/// ```
/// 下载进度：30.0 MB / 300.0 MB (10%) | 速度：15.2 MB/s | 剩余：17s
/// 下载进度：60.0 MB / 300.0 MB (20%) | 速度：14.8 MB/s | 剩余：16s
/// 下载完成：300.0 MB / 300.0 MB (100%) | 总耗时：98.5s
/// ```
///
/// **对于未知大小的文件**：
/// ```
/// 下载进度：30.0 MB / 未知 | 速度：15.2 MB/s
/// 下载完成：300.0 MB / 未知 | 总耗时：98.5s
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class LoggingProgressListener implements ProgressListener {

  /// 默认日志输出间隔（每 10% 输出一次）
  public static final int DEFAULT_LOG_INTERVAL_PERCENT = 10;

  private final int logIntervalPercent;

  /// 使用 ThreadLocal 存储每个线程的上次日志百分比，确保线程安全。
  ///
  /// 当此监听器作为单例 Bean 注入时，多个并发下载会在不同线程执行，
  /// ThreadLocal 确保每个线程拥有独立的状态。
  private final ThreadLocal<Integer> lastLoggedPercent = ThreadLocal.withInitial(() -> -1);

  /// 使用默认间隔（10%）创建监听器。
  public LoggingProgressListener() {
    this(DEFAULT_LOG_INTERVAL_PERCENT);
  }

  /// 使用指定间隔创建监听器。
  ///
  /// @param logIntervalPercent 日志输出间隔百分比（1-100）
  /// @throws IllegalArgumentException 如果间隔不在有效范围内
  public LoggingProgressListener(int logIntervalPercent) {
    if (logIntervalPercent < 1 || logIntervalPercent > 100) {
      throw new IllegalArgumentException("日志间隔必须在 1-100 之间，当前值: " + logIntervalPercent);
    }
    this.logIntervalPercent = logIntervalPercent;
  }

  @Override
  public void onProgress(DownloadProgress progress) {
    // 如果总大小未知，每次都输出日志（因为无法计算百分比）
    if (!progress.isTotalBytesKnown()) {
      log.info("下载进度：{} | 速度：{}", progress.formattedSize(), progress.formattedSpeed());
      return;
    }

    // 根据间隔判断是否输出日志
    int currentPercent = progress.percentage();
    int lastLogged = lastLoggedPercent.get();
    int targetPercent = lastLogged + logIntervalPercent;

    // 首次输出（0%）或达到间隔阈值
    if (lastLogged < 0 || currentPercent >= targetPercent) {
      log.info(
          "下载进度：{} | 速度：{} | 剩余：{}",
          progress.formattedSize(),
          progress.formattedSpeed(),
          progress.formattedRemainingTime());
      lastLoggedPercent.set((currentPercent / logIntervalPercent) * logIntervalPercent);
    }
  }

  @Override
  public void onComplete(DownloadProgress finalProgress) {
    try {
      log.info(
          "下载完成：{} | 总耗时：{}", finalProgress.formattedSize(), finalProgress.formattedElapsedTime());
    } finally {
      lastLoggedPercent.remove();
    }
  }

  @Override
  public void onError(Exception exception, DownloadProgress lastProgress) {
    try {
      if (lastProgress != null) {
        log.error(
            "下载失败：已下载 {} | 耗时：{} | 错误：{}",
            lastProgress.formattedSize(),
            lastProgress.formattedElapsedTime(),
            exception.getMessage());
      } else {
        log.error("下载失败：{}", exception.getMessage());
      }
    } finally {
      lastLoggedPercent.remove();
    }
  }

  /// 重置状态，允许监听器被重用。
  ///
  /// 在开始新的下载任务前调用此方法。清理 ThreadLocal 防止内存泄漏。
  public void reset() {
    lastLoggedPercent.remove();
  }
}
