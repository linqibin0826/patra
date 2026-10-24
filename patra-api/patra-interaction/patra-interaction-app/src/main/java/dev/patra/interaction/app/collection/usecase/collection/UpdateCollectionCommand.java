package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.tool.cqrs.Command;

/// 改收藏夹的名称与简介（整体替换）。
///
/// @param collectionId 收藏夹
/// @param name 名称
/// @param description 简介，可空
public record UpdateCollectionCommand(long collectionId, String name, String description)
    implements Command<CollectionSummary> {

  /// 创建命令。
  ///
  /// @param collectionId 收藏夹
  /// @param name 名称
  /// @param description 简介
  /// @return 命令
  public static UpdateCollectionCommand of(long collectionId, String name, String description) {
    return new UpdateCollectionCommand(collectionId, name, description);
  }
}
