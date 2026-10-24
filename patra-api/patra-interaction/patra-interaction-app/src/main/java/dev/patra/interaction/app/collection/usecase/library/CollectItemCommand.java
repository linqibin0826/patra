package dev.patra.interaction.app.collection.usecase.library;

import dev.patra.tool.cqrs.Command;

/// 把一个对象收进当前用户的文献库（幂等）。
///
/// @param type 对外类型名（`publication`）
/// @param key 对外 key（catalog 文献 ID）
public record CollectItemCommand(String type, String key) implements Command<CollectResult> {

  /// 创建命令。
  ///
  /// @param type 对外类型名
  /// @param key 对外 key
  /// @return 命令
  public static CollectItemCommand of(String type, String key) {
    return new CollectItemCommand(type, key);
  }
}
