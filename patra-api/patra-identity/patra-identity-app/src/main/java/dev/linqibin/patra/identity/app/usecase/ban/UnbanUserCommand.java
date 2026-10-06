package dev.linqibin.patra.identity.app.usecase.ban;

import dev.linqibin.commons.cqrs.Command;

/// 解封前台用户。
///
/// @param userId 用户 ID
public record UnbanUserCommand(long userId) implements Command<Void> {

  /// 创建命令。
  ///
  /// @param userId 用户 ID
  /// @return 命令
  public static UnbanUserCommand of(long userId) {
    return new UnbanUserCommand(userId);
  }
}
