package dev.linqibin.commons.error.retry;

import java.time.Duration;

/// 需要告诉调用方「多久之后再试」的异常实现此接口。
///
/// 统一错误格式据此输出响应头 `Retry-After` 和响应体字段 `retryAfterSeconds`，两者数值相同。
public interface HasRetryAfter {

  /// 返回还要等多久。
  ///
  /// @return 剩余等待时间
  Duration getRetryAfter();

  /// 返回剩余等待的秒数：毫秒向上取整到秒，最小为 1。
  ///
  /// @return 秒数
  default long getRetryAfterSeconds() {
    long millis = Math.max(0, getRetryAfter().toMillis());
    return Math.max(1, (millis + 999) / 1000);
  }
}
