package dev.patra.analytics.simulator.support;

/// 等待一段时间。实时压测与重试退避经它睡眠，测试换成不睡的实现。
@FunctionalInterface
public interface Sleeper {

  /// 真正睡眠的实现。
  Sleeper REAL =
      millis -> {
        try {
          Thread.sleep(millis);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("等待时被中断", e);
        }
      };

  /// 睡眠。
  ///
  /// @param millis 毫秒，≤ 0 时立即返回
  void sleep(long millis);
}
