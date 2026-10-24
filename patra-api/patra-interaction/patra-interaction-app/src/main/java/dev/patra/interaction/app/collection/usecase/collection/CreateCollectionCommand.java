package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.tool.cqrs.Command;

/// 新建私有收藏夹。
///
/// @param name 名称
/// @param description 简介，可空
public record CreateCollectionCommand(String name, String description)
    implements Command<CollectionSummary> {

  /// 创建命令。
  ///
  /// @param name 名称
  /// @param description 简介
  /// @return 命令
  public static CreateCollectionCommand of(String name, String description) {
    return new CreateCollectionCommand(name, description);
  }
}
