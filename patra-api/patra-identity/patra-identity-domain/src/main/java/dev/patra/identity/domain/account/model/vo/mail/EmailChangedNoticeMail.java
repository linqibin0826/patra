package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Map;
import java.util.Objects;
import lombok.Builder;

/// 「邮箱已更改」的安全通知，发给旧邮箱，内含 7 天有效的撤销链接；新地址只给遮掩过的形式。
///
/// @param to 收件人（旧邮箱）
/// @param displayName 收件人的昵称
/// @param newEmailMasked 遮掩过的新邮箱，如 `c***@example.com`
/// @param revertUrl 撤销链接，令牌在 URL 片段里
/// @param notice 安全通知上下文
@Builder
public record EmailChangedNoticeMail(
    EmailAddress to,
    String displayName,
    String newEmailMasked,
    String revertUrl,
    SecurityNotice notice)
    implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "email-changed-notice";

  /// 主题。
  public static final String SUBJECT = "你的 Patra 邮箱已更改";

  /// 不是本人操作时该怎么办：「不是我」就是撤销链接。片段已经问了「不是你本人的操作？」，这里直接接动作，
  /// 模板正文不再重复这句。
  public static final String NOT_ME_ADVICE = "点下面的链接撤销：邮箱会改回本地址，所有设备下线，并给你发重设密码的链接";

  /// 字段都不能为 `null`。
  public EmailChangedNoticeMail {
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(newEmailMasked, "newEmailMasked 不能为 null");
    Objects.requireNonNull(revertUrl, "revertUrl 不能为 null");
    Objects.requireNonNull(notice, "notice 不能为 null");
  }

  /// 模板名 `email-changed-notice`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「你的 Patra 邮箱已更改」。
  ///
  /// @return 主题
  @Override
  public String subject() {
    return SUBJECT;
  }

  /// 模板变量：`displayName`、`newEmailMasked`、`revertUrl`、`validDays`（撤销令牌的有效天数）、`notice`。
  ///
  /// @return 变量
  @Override
  public Map<String, Object> model() {
    return Map.of(
        "displayName",
        displayName,
        "newEmailMasked",
        newEmailMasked,
        "revertUrl",
        revertUrl,
        "validDays",
        EmailTokenPurpose.EMAIL_CHANGE_REVERT.validity().toDays(),
        "notice",
        notice);
  }

  /// 不输出收件人和链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "EmailChangedNoticeMail[displayName=" + displayName + "]";
  }
}
