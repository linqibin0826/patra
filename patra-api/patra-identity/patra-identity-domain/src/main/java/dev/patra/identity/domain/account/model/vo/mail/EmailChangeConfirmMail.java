package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 改邮箱的确认邮件：带确认链接，发给新邮箱。
///
/// @param eventId 事件 ID：构造时生成，补投时不变
/// @param to 收件人（新邮箱）
/// @param displayName 收件人的昵称
/// @param confirmUrl 确认链接，令牌在 URL 片段里
/// @param linkExpiresAt 链接的过期时间
@Builder
public record EmailChangeConfirmMail(
    UUID eventId, EmailAddress to, String displayName, String confirmUrl, Instant linkExpiresAt)
    implements OutgoingMail {

  /// 除基本类型外的字段都不能为 `null`。
  public EmailChangeConfirmMail {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(confirmUrl, "confirmUrl 不能为 null");
    Objects.requireNonNull(linkExpiresAt, "linkExpiresAt 不能为 null");
  }

  /// 不输出收件人与链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "EmailChangeConfirmMail[eventId=" + eventId + ", displayName=" + displayName + "]";
  }

  /// 构造器：不指定事件 ID 时用构造时生成的新 ID。
  public static class EmailChangeConfirmMailBuilder {

    /// 事件 ID，默认在创建构造器时生成。
    private UUID eventId = UUID.randomUUID();
  }
}
