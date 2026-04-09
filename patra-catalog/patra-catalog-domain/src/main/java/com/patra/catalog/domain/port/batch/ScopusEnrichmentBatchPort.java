package com.patra.catalog.domain.port.batch;

/// Scopus 期刊指标富化批处理端口（领域层定义，基础设施层实现）。
///
/// **设计要点**：
///
/// - 无参数方法：通过 NOT EXISTS 子查询自动识别未处理的期刊
/// - 幂等性：重复调用安全——已有 Scopus 数据的期刊会被 Reader 自动过滤
/// - 返回执行标识符供上层追踪任务状态
///
/// @author linqibin
/// @since 0.1.0
public interface ScopusEnrichmentBatchPort {

  /// 启动 Scopus 期刊指标富化批处理任务。
  ///
  /// @return 批处理执行标识符（Spring Batch Job Execution ID）
  Long launchEnrichment();
}
