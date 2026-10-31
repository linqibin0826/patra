package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.util.Objects;
import java.util.UUID;

/// 「账号已注销」的通知，到期执行后发给注销前的邮箱。不是某次请求触发的，不带安全通知。
///
/// @param eventId 事件 ID：构造时生成，补投时不变
/// @param to 收件人：注销前的邮箱
/// @param displayName 收件人的昵称
/// @param username 用户名，永久占用、不再释放
public record AccountDeletionCompletedMail(
    UUID eventId, EmailAddress to, String displayName, String username) implements OutgoingMail {

  /// 除基本类型外的字段都不能为 `null`。
  public AccountDeletionCompletedMail {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(username, "username 不能为 null");
  }

  /// 创建邮件，生成新的事件 ID。
  ///
  /// @param to 收件人：注销前的邮箱
  /// @param displayName 收件人的昵称
  /// @param username 用户名，永久占用、不再释放
  /// @return 邮件
  public static AccountDeletionCompletedMail of(
      EmailAddress to, String displayName, String username) {
    return new AccountDeletionCompletedMail(UUID.randomUUID(), to, displayName, username);
  }

  /// 不输出收件人与链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AccountDeletionCompletedMail[eventId=" + eventId + ", displayName=" + displayName + "]";
  }
}
