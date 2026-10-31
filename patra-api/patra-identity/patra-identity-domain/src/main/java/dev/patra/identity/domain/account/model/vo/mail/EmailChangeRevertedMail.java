package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 「邮箱已改回，请重设密码」的安全通知，撤销改邮箱后发给改回的旧邮箱，内含重设密码的链接。
///
/// @param eventId 事件 ID：构造时生成，补投时不变
/// @param to 收件人（改回的旧邮箱）
/// @param displayName 收件人的昵称
/// @param resetUrl 重设密码的链接，令牌在 URL 片段里
/// @param linkExpiresAt 重设链接的过期时间
/// @param notice 安全通知上下文：时间、设备、IP 与属地
@Builder
public record EmailChangeRevertedMail(
    UUID eventId,
    EmailAddress to,
    String displayName,
    String resetUrl,
    Instant linkExpiresAt,
    SecurityNotice notice)
    implements OutgoingMail {

  /// 除基本类型外的字段都不能为 `null`。
  public EmailChangeRevertedMail {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(resetUrl, "resetUrl 不能为 null");
    Objects.requireNonNull(linkExpiresAt, "linkExpiresAt 不能为 null");
    Objects.requireNonNull(notice, "notice 不能为 null");
  }

  /// 不输出收件人与链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "EmailChangeRevertedMail[eventId=" + eventId + ", displayName=" + displayName + "]";
  }

  /// 构造器：不指定事件 ID 时用构造时生成的新 ID。
  public static class EmailChangeRevertedMailBuilder {

    /// 事件 ID，默认在创建构造器时生成。
    private UUID eventId = UUID.randomUUID();
  }
}
