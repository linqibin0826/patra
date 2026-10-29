package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Map;
import java.util.Objects;

/// 「你的账号在新设备上登录」的安全通知，发给当前邮箱（PAP-99 设计第 12.4 节）。
///
/// @param to 收件人
/// @param displayName 收件人的昵称
/// @param notice 安全通知上下文：时间、设备、IP 与属地
public record NewDeviceLoginMail(EmailAddress to, String displayName, SecurityNotice notice)
    implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "new-device-login";

  /// 主题。
  public static final String SUBJECT = "你的 Patra 账号在新设备上登录";

  /// 不是本人操作时该怎么办。
  public static final String NOT_ME_ADVICE = "如果不是你，你的密码可能已经泄露：请到安全设置修改密码（其他设备会全部退出登录），并检查登录设备";

  /// 字段都不能为 `null`。
  public NewDeviceLoginMail {
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(notice, "notice 不能为 null");
  }

  /// 创建邮件。
  ///
  /// @param to 收件人
  /// @param displayName 收件人的昵称
  /// @param notice 安全通知上下文
  /// @return 邮件
  public static NewDeviceLoginMail of(EmailAddress to, String displayName, SecurityNotice notice) {
    return new NewDeviceLoginMail(to, displayName, notice);
  }

  /// 模板名 `new-device-login`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「你的 Patra 账号在新设备上登录」。
  ///
  /// @return 主题
  @Override
  public String subject() {
    return SUBJECT;
  }

  /// 模板变量：`displayName`、`notice`。
  ///
  /// @return 变量
  @Override
  public Map<String, Object> model() {
    return Map.of("displayName", displayName, "notice", notice);
  }

  /// 不输出收件人。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "NewDeviceLoginMail[displayName=" + displayName + "]";
  }
}
