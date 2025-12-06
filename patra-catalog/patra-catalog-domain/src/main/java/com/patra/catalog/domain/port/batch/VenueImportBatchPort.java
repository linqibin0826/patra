package com.patra.catalog.domain.port.batch;

import com.patra.catalog.domain.model.vo.venue.VenueImportParams;

/// OpenAlex Venue 批量导入端口接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 六边形架构：领域层定义接口，隐藏批处理框架细节
/// - Application 层通过此端口调用批量导入，无需知道 Spring Batch 等技术实现
/// - 返回执行标识符供上层追踪任务状态
///
/// **使用场景**：
///
/// - 大数据量期刊导入（约 255,000 条）
/// - 多文件顺序读取（42+ 个分区文件）
/// - 支持断点续传（fileIndex + lineIndex）
/// - 支持 Upsert 幂等写入
///
/// **与 MeshDescriptorBatchPort 的差异**：
///
/// - MeSH 使用单文件路径，Venue 使用多文件路径列表
/// - MeSH 使用 currentIndex 断点，Venue 使用 fileIndex + lineIndex
/// - Venue 写入策略为 Upsert（更新或插入），MeSH 为纯新增
///
/// @author linqibin
/// @since 0.1.0
public interface VenueImportBatchPort {

  /// 启动 Venue 批量导入任务。
  ///
  /// @param params 导入参数（包含文件路径列表、是否强制新实例、是否临时文件）
  /// @return 批处理执行标识符（Spring Batch Job Execution ID）
  /// @see VenueImportParams
  Long launchImport(VenueImportParams params);
}
