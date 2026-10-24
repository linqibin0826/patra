package dev.patra.interaction.app.collection.usecase.library;

import dev.patra.tool.cqrs.Command;

/// 改文献库条目的备注；空白即删除。
///
/// @param itemId 条目 ID
/// @param note 新备注，可空
public record EditNoteCommand(long itemId, String note) implements Command<Void> {

  /// 创建命令。
  ///
  /// @param itemId 条目 ID
  /// @param note 新备注
  /// @return 命令
  public static EditNoteCommand of(long itemId, String note) {
    return new EditNoteCommand(itemId, note);
  }
}
