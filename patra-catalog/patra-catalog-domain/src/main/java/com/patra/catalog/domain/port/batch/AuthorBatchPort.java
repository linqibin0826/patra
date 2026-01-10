package com.patra.catalog.domain.port.batch;

import com.patra.catalog.domain.model.vo.author.AuthorImportParams;

/// PubMed Computed Authors 批量导入端口接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 六边形架构：领域层定义接口，隐藏批处理框架细节
/// - Application 层通过此端口调用批量导入，无需知道 Spring Batch 等技术实现
/// - 返回执行标识符供上层追踪任务状态
///
/// **数据源说明**：
///
/// - NLM FTP 站点的 PubMed Computed Authors JSON Lines 文件
/// - 文件约 3.6GB，包含约 2100 万+ 作者记录
/// - 每周更新，无版本号概念
///
/// **使用场景**：
///
/// - 导入 PubMed Computed Authors（约 2100 万+ 条记录）
/// - 支持断点续传和全量重导入
///
/// @author linqibin
/// @since 0.1.0
public interface AuthorBatchPort {

  /// 启动 PubMed Computed Authors 批量导入任务。
  ///
  /// @param params 导入参数（包含下载 URL）
  /// @return 批处理执行标识符（Spring Batch Job Execution ID）
  /// @see AuthorImportParams
  Long launchAuthorImport(AuthorImportParams params);
}
