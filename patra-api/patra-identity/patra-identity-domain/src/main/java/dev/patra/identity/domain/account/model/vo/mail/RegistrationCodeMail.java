package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 第三方首次注册的验证码：发给用户填的邮箱。这时候还没有账号。
///
/// @param eventId 事件 ID：构造时生成，补投时不变
/// @param to 收件人
/// @param displayName 用户填的用户名，还没有账号也就没有昵称
/// @param code 验证码原文，只在这封邮件里
/// @param codeExpiresAt 验证码的过期时间
@Builder
public record RegistrationCodeMail(
    UUID eventId, EmailAddress to, String displayName, String code, Instant codeExpiresAt)
    implements OutgoingMail {

  /// 除基本类型外的字段都不能为 `null`。
  public RegistrationCodeMail {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(code, "code 不能为 null");
    Objects.requireNonNull(codeExpiresAt, "codeExpiresAt 不能为 null");
  }

  /// 不输出收件人与验证码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegistrationCodeMail[eventId=" + eventId + "]";
  }

  /// 构造器：不指定事件 ID 时用构造时生成的新 ID。
  public static class RegistrationCodeMailBuilder {

    /// 事件 ID，默认在创建构造器时生成。
    private UUID eventId = UUID.randomUUID();
  }
}
