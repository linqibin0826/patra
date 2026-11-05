package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 邮箱撞车时就地绑定的验证码：发给已有账号的邮箱，写明要把哪个第三方账号绑到哪个账号。
///
/// @param eventId 事件 ID：构造时生成，补投时不变
/// @param to 收件人
/// @param displayName 收件人的昵称
/// @param username 要绑到的账号的用户名
/// @param code 验证码原文，只在这封邮件里
/// @param codeExpiresAt 验证码的过期时间
/// @param providerName 平台名，如 `GitHub`
/// @param accountLabel 第三方账号标识
@Builder
public record LoginMethodLinkCodeMail(
    UUID eventId,
    EmailAddress to,
    String displayName,
    String username,
    String code,
    Instant codeExpiresAt,
    String providerName,
    String accountLabel)
    implements OutgoingMail {

  /// 除基本类型外的字段都不能为 `null`。
  public LoginMethodLinkCodeMail {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(username, "username 不能为 null");
    Objects.requireNonNull(code, "code 不能为 null");
    Objects.requireNonNull(codeExpiresAt, "codeExpiresAt 不能为 null");
    Objects.requireNonNull(providerName, "providerName 不能为 null");
    Objects.requireNonNull(accountLabel, "accountLabel 不能为 null");
  }

  /// 不输出收件人与验证码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LoginMethodLinkCodeMail[eventId=" + eventId + "]";
  }

  /// 构造器：不指定事件 ID 时用构造时生成的新 ID。
  public static class LoginMethodLinkCodeMailBuilder {

    /// 事件 ID，默认在创建构造器时生成。
    private UUID eventId = UUID.randomUUID();
  }
}
