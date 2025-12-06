package com.patra.catalog.app.usecase.serfile;

import com.patra.catalog.app.usecase.serfile.command.SerfileImportCommand;
import com.patra.catalog.app.usecase.serfile.dto.SerfileImportResult;

/// NLM Serfile 导入用例接口，定义调度入口契约。
///
/// 用于 Adapter 层依赖，实现依赖倒置。
///
/// **导入流程**：
///
/// 1. 从远程 URL 下载 Serfile XML 文件
/// 2. 使用 StAX 流式解析 Serial 记录
/// 3. 按批次处理，匹配现有期刊或创建新记录
/// 4. 返回导入结果摘要
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
public interface SerfileImportUseCase {

  /// 执行 NLM Serfile 期刊数据导入。
  ///
  /// 下载、解析并导入 Serfile XML 中的期刊记录。
  /// 对于匹配的期刊覆盖更新，对于新期刊创建记录。
  ///
  /// @param command 导入命令（包含 URL 和版本号）
  /// @return 导入结果摘要（包含处理统计和耗时）
  SerfileImportResult importSerfile(SerfileImportCommand command);
}
