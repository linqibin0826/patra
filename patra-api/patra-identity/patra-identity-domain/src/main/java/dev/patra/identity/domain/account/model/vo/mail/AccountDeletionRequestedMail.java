package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 「注销已提交」的安全通知，发给当前邮箱：写明到期时刻和怎么取消（用邮箱和密码登录，不放链接）。
///
/// @param eventId 事件 ID：构造时生成，补投时不变
/// @param to 收件人
/// @param displayName 收件人的昵称
/// @param deletionDueAt 注销到期时刻
/// @param notice 安全通知上下文：时间、设备、IP 与属地
@Builder
public record AccountDeletionRequestedMail(
    UUID eventId, EmailAddress to, String displayName, Instant deletionDueAt, SecurityNotice notice)
    implements OutgoingMail {

  /// 除基本类型外的字段都不能为 `null`。
  public AccountDeletionRequestedMail {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(deletionDueAt, "deletionDueAt 不能为 null");
    Objects.requireNonNull(notice, "notice 不能为 null");
  }

  /// 不输出收件人与链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AccountDeletionRequestedMail[eventId=" + eventId + ", displayName=" + displayName + "]";
  }

  /// 构造器：不指定事件 ID 时用构造时生成的新 ID。
  public static class AccountDeletionRequestedMailBuilder {

    /// 事件 ID，默认在创建构造器时生成。
    private UUID eventId = UUID.randomUUID();
  }
}
