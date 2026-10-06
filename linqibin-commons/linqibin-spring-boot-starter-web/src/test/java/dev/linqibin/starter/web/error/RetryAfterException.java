package dev.linqibin.starter.web.error;

import dev.linqibin.commons.error.retry.HasRetryAfter;
import java.time.Duration;

/// 带剩余等待时间的测试异常，`ProblemDetailBuilderTest` 和 `GlobalRestExceptionHandlerTest` 共用。
public final class RetryAfterException extends RuntimeException implements HasRetryAfter {

  private final Duration retryAfter;

  /// 创建测试异常。
  ///
  /// @param retryAfter 剩余等待时间
  public RetryAfterException(Duration retryAfter) {
    super("请稍后再试");
    this.retryAfter = retryAfter;
  }

  /// 返回剩余等待时间。
  ///
  /// @return 剩余等待时间
  @Override
  public Duration getRetryAfter() {
    return retryAfter;
  }
}
