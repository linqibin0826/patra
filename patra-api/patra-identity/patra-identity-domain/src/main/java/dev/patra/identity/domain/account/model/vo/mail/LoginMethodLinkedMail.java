package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Objects;
import java.util.UUID;

/// 「绑定了新的第三方登录方式」的安全通知，发给当前邮箱。
///
/// @param eventId 事件 ID：构造时生成，补投时不变
/// @param to 收件人
/// @param displayName 收件人的昵称
/// @param providerName 平台名，如 `GitHub`
/// @param accountLabel 第三方账号标识
/// @param notice 安全通知上下文：时间、设备、IP 与属地
public record LoginMethodLinkedMail(
    UUID eventId,
    EmailAddress to,
    String displayName,
    String providerName,
    String accountLabel,
    SecurityNotice notice)
    implements OutgoingMail {

  /// 除基本类型外的字段都不能为 `null`。
  public LoginMethodLinkedMail {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(providerName, "providerName 不能为 null");
    Objects.requireNonNull(accountLabel, "accountLabel 不能为 null");
    Objects.requireNonNull(notice, "notice 不能为 null");
  }

  /// 创建邮件，生成新的事件 ID。
  ///
  /// @param to 收件人
  /// @param displayName 收件人的昵称
  /// @param providerName 平台名
  /// @param accountLabel 第三方账号标识
  /// @param notice 安全通知上下文
  /// @return 邮件
  public static LoginMethodLinkedMail of(
      EmailAddress to,
      String displayName,
      String providerName,
      String accountLabel,
      SecurityNotice notice) {
    return new LoginMethodLinkedMail(
        UUID.randomUUID(), to, displayName, providerName, accountLabel, notice);
  }

  /// 不输出收件人。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LoginMethodLinkedMail[eventId=" + eventId + "]";
  }
}
