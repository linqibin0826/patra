package dev.patra.identity.app.account.usecase.avatar;

import dev.patra.tool.cqrs.Command;

/// 当前用户移除头像，回到默认头像。
public record RemoveAvatarCommand() implements Command<Void> {

  /// 创建命令。
  ///
  /// @return 命令
  public static RemoveAvatarCommand of() {
    return new RemoveAvatarCommand();
  }
}
