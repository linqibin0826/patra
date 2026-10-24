package dev.patra.interaction.app.collection.usecase.library;

import dev.patra.tool.cqrs.Command;
import java.util.List;
import java.util.Objects;

/// 从当前用户的文献库移除条目，同时移出所有收藏夹、删备注（幂等）。拷贝时 `null` 或含 `null` 的列表变成空列表，由用例按「个数越界」拒绝（422）。
///
/// @param itemIds 条目 ID，1 ~ 100 个
public record RemoveItemsCommand(List<Long> itemIds) implements Command<Void> {

  /// 防御性拷贝。
  public RemoveItemsCommand {
    itemIds =
        itemIds == null || itemIds.stream().anyMatch(Objects::isNull)
            ? List.of()
            : List.copyOf(itemIds);
  }

  /// 创建命令。
  ///
  /// @param itemIds 条目 ID
  /// @return 命令
  public static RemoveItemsCommand of(List<Long> itemIds) {
    return new RemoveItemsCommand(itemIds);
  }
}
