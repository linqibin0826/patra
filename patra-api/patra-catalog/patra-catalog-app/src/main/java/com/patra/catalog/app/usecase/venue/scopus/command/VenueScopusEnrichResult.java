package com.patra.catalog.app.usecase.venue.scopus.command;

/// Scopus 期刊指标富化命令执行结果。
///
/// @param executionId Spring Batch Job Execution ID，用于追踪任务状态
/// @author linqibin
/// @since 0.1.0
public record VenueScopusEnrichResult(Long executionId) {

  /// 创建成功结果。
  public static VenueScopusEnrichResult of(Long executionId) {
    return new VenueScopusEnrichResult(executionId);
  }
}
