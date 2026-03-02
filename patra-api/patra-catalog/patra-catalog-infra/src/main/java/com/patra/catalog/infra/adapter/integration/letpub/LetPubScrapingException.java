package com.patra.catalog.infra.adapter.integration.letpub;

import java.io.Serial;

/// LetPub 爬取异常。
///
/// 当 LetPub 网站爬取过程中发生不可恢复的错误时抛出，
/// 如超过最大限流重试次数、网络异常等。
///
/// 由 Spring Batch 的 skip 策略捕获，单条失败不中断整体作业。
///
/// @author linqibin
/// @since 0.1.0
public class LetPubScrapingException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /// 创建爬取异常。
  ///
  /// @param message 错误信息
  public LetPubScrapingException(String message) {
    super(message);
  }

  /// 创建带原因的爬取异常。
  ///
  /// @param message 错误信息
  /// @param cause 原始异常
  public LetPubScrapingException(String message, Throwable cause) {
    super(message, cause);
  }
}
