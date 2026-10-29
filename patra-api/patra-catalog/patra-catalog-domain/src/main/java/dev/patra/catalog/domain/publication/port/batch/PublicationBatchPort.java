package dev.patra.catalog.domain.publication.port.batch;

import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileImportParams;

/// PubMed 文献批处理端口：处理一个文件（baseline 或 updatefiles），或在本地按原文重算。
///
/// 实现封装 Spring Batch；调用方同步等待作业结束并拿到计数。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationBatchPort {

  /// 同步处理一个 PubMed 文件：下载并校验 md5、逐篇写入（来源位置守卫）、按 `DeleteCitation` 软删。
  ///
  /// @param params 文件与范围
  /// @return 处理计数
  /// @throws IllegalStateException 作业执行失败时
  PubmedFileCounts launchFileImport(PubmedFileImportParams params);

  /// 同步执行本地重算：按来源表里保存的原文重新解析并覆盖，不访问网络。
  ///
  /// @param generation 作业 generation（每次重算一个新的作业实例）
  /// @return 处理计数
  /// @throws IllegalStateException 作业执行失败时
  PubmedFileCounts launchReparse(String generation);
}
