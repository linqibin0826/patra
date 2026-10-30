package dev.patra.notification.adapter.preference.rest.request;

/// 退订落地页上点了按钮。
///
/// @param token 退订令牌原文；`toString()` 不输出它
/// @param category `REPLY`、`MENTION`、`DIGEST` 或 `ALL`（关闭全部提醒邮件）
public record UnsubscribeRequest(String token, String category) {

  /// 不输出令牌。
  ///
  /// @return 形如 `UnsubscribeRequest[token=***, category=ALL]`
  @Override
  public String toString() {
    return "UnsubscribeRequest[token=***, category=" + category + "]";
  }
}
