package dev.patra.interaction.app.collection.usecase.copy;

import dev.patra.tool.cqrs.Command;

/// 把一个对当前用户可见的公开收藏夹复制成他自己的私有收藏夹。
///
/// @param collectionId 源收藏夹
public record CopyCollectionCommand(long collectionId) implements Command<CopyResult> {

  /// 创建命令。
  ///
  /// @param collectionId 源收藏夹
  /// @return 命令
  public static CopyCollectionCommand of(long collectionId) {
    return new CopyCollectionCommand(collectionId);
  }
}
