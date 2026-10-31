package dev.patra.notification.app.preference.usecase.unsubscribe;

import dev.patra.tool.cqrs.Command;

/// 凭退订令牌关掉一类提醒邮件，免登录。落地页的确认与 RFC 8058 一键退订共用；两个字段都是请求原文，可空，
/// 由处理器解析（不合法即链接无效）。
///
/// @param token 退订令牌原文；`toString()` 不输出它
/// @param category 退订类别：`REPLY`、`MENTION`、`DIGEST`，落地页的确认还可以是 `ALL`
/// @param oneClick 是不是邮箱客户端的一键退订：一键退订的地址里不会有 `ALL`
public record UnsubscribeCommand(String token, String category, boolean oneClick)
    implements Command<UnsubscribeResult> {

  /// 落地页上点了按钮。
  ///
  /// @param token 令牌原文
  /// @param category 退订类别
  /// @return 命令
  public static UnsubscribeCommand confirm(String token, String category) {
    return new UnsubscribeCommand(token, category, false);
  }

  /// 邮箱客户端的一键退订。
  ///
  /// @param token 令牌原文
  /// @param category 退订类别
  /// @return 命令
  public static UnsubscribeCommand oneClick(String token, String category) {
    return new UnsubscribeCommand(token, category, true);
  }

  /// 不输出令牌。
  ///
  /// @return 形如 `UnsubscribeCommand[token=***, category=REPLY, oneClick=true]`
  @Override
  public String toString() {
    return "UnsubscribeCommand[token=***, category=" + category + ", oneClick=" + oneClick + "]";
  }
}
