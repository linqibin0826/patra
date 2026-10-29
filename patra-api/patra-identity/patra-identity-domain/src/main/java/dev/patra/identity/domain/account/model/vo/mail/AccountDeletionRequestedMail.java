package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/// 「注销已提交」的安全通知，发给当前邮箱：写明到期时刻和怎么取消（用邮箱和密码登录，不放链接）。
///
/// @param to 收件人
/// @param displayName 收件人的昵称
/// @param deletionDueAt 注销到期时刻，渲染器按时区格式化
/// @param notice 安全通知上下文
public record AccountDeletionRequestedMail(
    EmailAddress to, String displayName, Instant deletionDueAt, SecurityNotice notice)
    implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "account-deletion-requested";

  /// 主题。
  public static final String SUBJECT = "你的 Patra 账号将被注销";

  /// 不是本人操作时该怎么办。
  public static final String NOT_ME_ADVICE =
      "如果不是你，说明有人知道了你的密码：请在到期前登录并取消注销，然后立即修改密码；登录不了就用『忘记密码』重设";

  /// 字段都不能为 `null`。
  public AccountDeletionRequestedMail {
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(deletionDueAt, "deletionDueAt 不能为 null");
    Objects.requireNonNull(notice, "notice 不能为 null");
  }

  /// 创建邮件。
  ///
  /// @param to 收件人
  /// @param displayName 收件人的昵称
  /// @param deletionDueAt 注销到期时刻
  /// @param notice 安全通知上下文
  /// @return 邮件
  public static AccountDeletionRequestedMail of(
      EmailAddress to, String displayName, Instant deletionDueAt, SecurityNotice notice) {
    return new AccountDeletionRequestedMail(to, displayName, deletionDueAt, notice);
  }

  /// 模板名 `account-deletion-requested`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「你的 Patra 账号将被注销」。
  ///
  /// @return 主题
  @Override
  public String subject() {
    return SUBJECT;
  }

  /// 模板变量：`displayName`、`deletionDueAt`、`notice`。
  ///
  /// @return 变量
  @Override
  public Map<String, Object> model() {
    return Map.of("displayName", displayName, "deletionDueAt", deletionDueAt, "notice", notice);
  }

  /// 不输出收件人。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AccountDeletionRequestedMail[displayName=" + displayName + "]";
  }
}
