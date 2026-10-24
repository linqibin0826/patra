package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.tool.cqrs.Command;

/// 删收藏夹（幂等，文献库条目不动）。
///
/// @param collectionId 收藏夹
public record DeleteCollectionCommand(long collectionId) implements Command<Void> {

  /// 创建命令。
  ///
  /// @param collectionId 收藏夹
  /// @return 命令
  public static DeleteCollectionCommand of(long collectionId) {
    return new DeleteCollectionCommand(collectionId);
  }
}
