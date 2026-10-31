package dev.patra.identity.api.account.event;

import java.util.Objects;
import java.util.UUID;

/// 请发「你的 Patra 账号已注销」通知：到期执行后发出，不带安全通知片段。
///
/// @param eventId 事件 ID：identity 构造这封邮件时生成，补投时不变；发信时用作 `Resend-Idempotency-Key`
/// @param to 收件地址：注销前的邮箱（账号里已置空）
/// @param displayName 收件人的昵称（没设时已回退成用户名）
/// @param username 用户名：永久占用、不再释放
public record AccountDeletionCompletedMailEvent(
    UUID eventId, String to, String displayName, String username) implements SecurityMailEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "security-mail.account-deletion-completed";

  /// 除基本类型外的字段都不能为 `null`。
  public AccountDeletionCompletedMailEvent {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(username, "username 不能为 null");
  }

  /// 创建事件。
  ///
  /// @param eventId 同名字段
  /// @param to 同名字段
  /// @param displayName 同名字段
  /// @param username 同名字段
  /// @return 事件
  public static AccountDeletionCompletedMailEvent of(
      UUID eventId, String to, String displayName, String username) {
    return new AccountDeletionCompletedMailEvent(eventId, to, displayName, username);
  }

  /// 只输出事件 ID：收件地址与链接不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AccountDeletionCompletedMailEvent[eventId=" + eventId + "]";
  }
}
