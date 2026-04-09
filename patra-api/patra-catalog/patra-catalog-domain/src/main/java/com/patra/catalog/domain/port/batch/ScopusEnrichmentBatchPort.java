package com.patra.catalog.domain.port.batch;

/// Scopus 期刊指标富化批处理端口（领域层定义，基础设施层实现）。
///
/// **设计要点**：
///
/// - 按目标年份筛选：通过 `NOT EXISTS` 子查询筛选缺少指定年份 Scopus 评级数据的期刊
/// - 幂等性：重复调用安全——已有目标年份数据的期刊会被 Reader 自动过滤
/// - 返回执行标识符供上层追踪任务状态
///
/// @author linqibin
/// @since 0.1.0
public interface ScopusEnrichmentBatchPort {

  /// 启动 Scopus 期刊指标富化批处理任务。
  ///
  /// @param targetYear 目标评级年份（如 2025），用于筛选缺少该年份数据的期刊
  /// @param minCitedByCount 最低被引次数阈值，0 表示不过滤
  /// @return 批处理执行标识符（Spring Batch Job Execution ID）
  Long launchEnrichment(short targetYear, int minCitedByCount);
}
