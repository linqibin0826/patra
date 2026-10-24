package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.tool.cqrs.Command;
import java.util.List;
import java.util.Objects;

/// 把条目移出自己的收藏夹（文献库条目不动）。拷贝时 `null` 或含 `null` 的列表变成空列表，由用例按「个数越界」拒绝（422）。
///
/// @param collectionId 收藏夹
/// @param itemIds 条目 ID，1 ~ 100 个
public record RemoveFromCollectionCommand(long collectionId, List<Long> itemIds)
    implements Command<Void> {

  /// 防御性拷贝。
  public RemoveFromCollectionCommand {
    itemIds =
        itemIds == null || itemIds.stream().anyMatch(Objects::isNull)
            ? List.of()
            : List.copyOf(itemIds);
  }

  /// 创建命令。
  ///
  /// @param collectionId 收藏夹
  /// @param itemIds 条目 ID
  /// @return 命令
  public static RemoveFromCollectionCommand of(long collectionId, List<Long> itemIds) {
    return new RemoveFromCollectionCommand(collectionId, itemIds);
  }
}
