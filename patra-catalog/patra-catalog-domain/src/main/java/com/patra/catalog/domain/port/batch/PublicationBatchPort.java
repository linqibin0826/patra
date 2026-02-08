package com.patra.catalog.domain.port.batch;

import com.patra.catalog.domain.model.vo.publication.PublicationImportParams;

/// Publication 批量导入端口接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 六边形架构：领域层定义接口，隐藏批处理框架细节
/// - Application 层通过此端口调用批量导入，无需知道 Spring Batch 等技术实现
/// - 返回执行标识符供上层追踪任务状态
///
/// **单文件模式**：
///
/// 每次调用只处理一个文件，通过 `PublicationImportParams.fileIndex` 指定。
/// 这种设计支持：
///
/// - 测试环境只导入 1 个文件验证功能
/// - 生产环境通过 XXL-Job 循环调度批量导入 1334 个文件
/// - 断点续传（从指定文件索引继续）
///
/// **使用场景**：
///
/// - `launchBaselineImport(params)`：导入指定的 PubMed Baseline 文件
/// - 文件索引范围：1-1334（对应 pubmed26n0001.xml.gz ~ pubmed26n1334.xml.gz）
/// - 每文件约 30,000 条记录
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationBatchPort {

  /// 启动 PubMed Baseline 文献批量导入任务。
  ///
  /// 每次调用只处理一个文件（由 `params.fileIndex` 指定），
  /// 支持测试环境小规模验证和生产环境批量调度。
  ///
  /// **处理流程**：
  ///
  /// 1. 下载指定的 .xml.gz 文件
  /// 2. 从临时文件解压并解析 XML
  /// 3. Venue 匹配和 VenueInstance 创建
  /// 4. 批量写入 Publication 表
  ///
  /// @param params 导入参数（包含 baseUrl 和 fileIndex）
  /// @return 批处理执行标识符（Spring Batch Job Execution ID）
  /// @see PublicationImportParams
  Long launchBaselineImport(PublicationImportParams params);
}
