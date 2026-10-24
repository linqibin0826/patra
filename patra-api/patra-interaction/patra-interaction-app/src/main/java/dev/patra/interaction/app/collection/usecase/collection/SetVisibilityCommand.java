package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.tool.cqrs.Command;

/// 设收藏夹的可见性。
///
/// @param collectionId 收藏夹
/// @param visibility `PRIVATE` / `PUBLIC`（大小写不敏感）
public record SetVisibilityCommand(long collectionId, String visibility)
    implements Command<CollectionSummary> {

  /// 创建命令。
  ///
  /// @param collectionId 收藏夹
  /// @param visibility 可见性
  /// @return 命令
  public static SetVisibilityCommand of(long collectionId, String visibility) {
    return new SetVisibilityCommand(collectionId, visibility);
  }
}
