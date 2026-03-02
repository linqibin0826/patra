package com.patra.catalog.app.usecase.venue.letpub.command;

/// LetPub 期刊富化命令执行结果。
///
/// @param executionId Spring Batch Job Execution ID，用于追踪任务状态
/// @author linqibin
/// @since 0.1.0
public record VenueLetPubEnrichResult(Long executionId) {

  /// 创建成功结果。
  public static VenueLetPubEnrichResult of(Long executionId) {
    return new VenueLetPubEnrichResult(executionId);
  }
}
