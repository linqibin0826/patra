package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Objects;
import java.util.UUID;

/// 「密码已重设」的安全通知，发给当前邮箱。
///
/// @param eventId 事件 ID：构造时生成，补投时不变
/// @param to 收件人
/// @param displayName 收件人的昵称
/// @param notice 安全通知上下文：时间、设备、IP 与属地
public record PasswordResetDoneMail(
    UUID eventId, EmailAddress to, String displayName, SecurityNotice notice)
    implements OutgoingMail {

  /// 除基本类型外的字段都不能为 `null`。
  public PasswordResetDoneMail {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(notice, "notice 不能为 null");
  }

  /// 创建邮件，生成新的事件 ID。
  ///
  /// @param to 收件人
  /// @param displayName 收件人的昵称
  /// @param notice 安全通知上下文：时间、设备、IP 与属地
  /// @return 邮件
  public static PasswordResetDoneMail of(
      EmailAddress to, String displayName, SecurityNotice notice) {
    return new PasswordResetDoneMail(UUID.randomUUID(), to, displayName, notice);
  }

  /// 不输出收件人与链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "PasswordResetDoneMail[eventId=" + eventId + ", displayName=" + displayName + "]";
  }
}
