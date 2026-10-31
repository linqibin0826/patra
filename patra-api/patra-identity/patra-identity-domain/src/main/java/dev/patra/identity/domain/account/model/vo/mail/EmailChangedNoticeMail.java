package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 「邮箱已更改」的安全通知，发给旧邮箱，内含 7 天有效的撤销链接；新地址只给遮掩过的形式。
///
/// @param eventId 事件 ID：构造时生成，补投时不变
/// @param to 收件人（旧邮箱）
/// @param displayName 收件人的昵称
/// @param newEmailMasked 遮掩过的新邮箱，如 `c***@example.com`
/// @param revertUrl 撤销链接，令牌在 URL 片段里
/// @param linkExpiresAt 撤销链接的过期时间
/// @param notice 安全通知上下文：时间、设备、IP 与属地
@Builder
public record EmailChangedNoticeMail(
    UUID eventId,
    EmailAddress to,
    String displayName,
    String newEmailMasked,
    String revertUrl,
    Instant linkExpiresAt,
    SecurityNotice notice)
    implements OutgoingMail {

  /// 除基本类型外的字段都不能为 `null`。
  public EmailChangedNoticeMail {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(newEmailMasked, "newEmailMasked 不能为 null");
    Objects.requireNonNull(revertUrl, "revertUrl 不能为 null");
    Objects.requireNonNull(linkExpiresAt, "linkExpiresAt 不能为 null");
    Objects.requireNonNull(notice, "notice 不能为 null");
  }

  /// 不输出收件人与链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "EmailChangedNoticeMail[eventId=" + eventId + ", displayName=" + displayName + "]";
  }

  /// 构造器：不指定事件 ID 时用构造时生成的新 ID。
  public static class EmailChangedNoticeMailBuilder {

    /// 事件 ID，默认在创建构造器时生成。
    private UUID eventId = UUID.randomUUID();
  }
}
