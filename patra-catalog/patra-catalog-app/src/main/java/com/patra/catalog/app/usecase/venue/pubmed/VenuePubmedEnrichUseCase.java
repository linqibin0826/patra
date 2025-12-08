package com.patra.catalog.app.usecase.venue.pubmed;

import com.patra.catalog.app.usecase.venue.pubmed.command.VenuePubmedEnrichCommand;
import com.patra.catalog.app.usecase.venue.pubmed.dto.VenuePubmedEnrichResult;

/// PubMed Venue 数据富化用例接口，定义调度入口契约。
///
/// 用于 Adapter 层依赖，实现依赖倒置。
///
/// **富化流程**：
///
/// 1. 从远程 URL 下载 NLM Serfile XML 文件
/// 2. 使用 StAX 流式解析 Serial 记录
/// 3. 按批次处理，匹配现有期刊或创建新记录
/// 4. 返回富化结果摘要
///
/// **匹配策略**：
///
/// - 优先级 1: ISSN-L 匹配
/// - 优先级 2: NLM ID 匹配
/// - 优先级 3: ISSN（Print 或 Electronic）匹配
///
/// **覆盖策略**：
///
/// - 匹配成功：PubMed 数据完全覆盖现有数据
/// - 无匹配：为 PubMed 独有期刊创建新 VenueAggregate
///
/// @author linqibin
/// @since 0.1.0
public interface VenuePubmedEnrichUseCase {

  /// 执行 PubMed Venue 数据富化。
  ///
  /// 下载、解析 NLM Serfile XML 中的期刊记录并富化 VenueAggregate。
  /// 对于匹配的期刊覆盖更新，对于新期刊创建记录。
  ///
  /// @param command 富化命令（包含 URL 和版本号）
  /// @return 富化结果摘要（包含处理统计和耗时）
  VenuePubmedEnrichResult enrichFromPubmed(VenuePubmedEnrichCommand command);
}
