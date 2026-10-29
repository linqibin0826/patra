package dev.patra.catalog.app.fulltext.usecase.fetch;

import dev.patra.tool.cqrs.Command;
import java.util.List;

/// PMC 全文抓取命令。
///
/// @param publicationIds 手动重抓的文献 ID（忽略状态）；为空时处理全部到期候选
/// @author linqibin
/// @since 0.11.0
public record PmcFullTextFetchCommand(List<Long> publicationIds)
    implements Command<FullTextFetchRunStats> {

  /// 防御性拷贝，`null` 视为空。
  public PmcFullTextFetchCommand {
    publicationIds = publicationIds != null ? List.copyOf(publicationIds) : List.of();
  }
}
