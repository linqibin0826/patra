package dev.linqibin.patra.identity.app.usecase.ban;

import dev.linqibin.commons.cqrs.Command;

/// 封禁前台用户。
///
/// @param userId 用户 ID
public record BanUserCommand(long userId) implements Command<Void> {

  /// 创建命令。
  ///
  /// @param userId 用户 ID
  /// @return 命令
  public static BanUserCommand of(long userId) {
    return new BanUserCommand(userId);
  }
}
