package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.tool.cqrs.Command;
import java.util.List;
import java.util.Objects;

/// 把文献库里的条目放进自己的收藏夹。拷贝时 `null` 或含 `null` 的列表变成空列表，由用例按「个数越界」拒绝（422）。
///
/// @param collectionId 收藏夹
/// @param itemIds 条目 ID，1 ~ 100 个
public record AddToCollectionCommand(long collectionId, List<Long> itemIds)
    implements Command<AddToCollectionResult> {

  /// 防御性拷贝。
  public AddToCollectionCommand {
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
  public static AddToCollectionCommand of(long collectionId, List<Long> itemIds) {
    return new AddToCollectionCommand(collectionId, itemIds);
  }
}
