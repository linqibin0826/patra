package dev.patra.notification.domain.mail.model.enums;

import java.util.Map;

/// identity 请通知服务发的 13 类安全邮件：模板名、主题、「不是我怎么办」与链接过期后发不发。
///
/// 模板名就是 identity 事件路由键 `security-mail.<模板名>` 的后半段，渲染器据此找 `html/<模板名>` 与
/// `text/<模板名>`。主题与「不是我怎么办」是 v0.16 之前 identity 里的原文，随模板一起搬来。
public enum SecurityMailKind {
  /// 验证邮箱：只为链接而发。
  EMAIL_VERIFICATION("email-verification", "验证你的 Patra 邮箱", null, true),
  /// 找回密码：只为链接而发。
  PASSWORD_RESET("password-reset", "重设你的 Patra 密码", null, true),
  /// 密码已重设。
  PASSWORD_RESET_DONE("password-reset-done", "你的 Patra 密码已重设", Advice.STOLEN_MAILBOX, false),
  /// 密码已修改。
  PASSWORD_CHANGED("password-changed", "你的 Patra 密码已修改", Advice.STOLEN_MAILBOX, false),
  /// 改邮箱的确认信（发新邮箱）：只为链接而发。
  EMAIL_CHANGE_CONFIRM("email-change-confirm", "确认你的新 Patra 邮箱", null, true),
  /// 邮箱已更改（发旧邮箱）：「不是我」就是撤销链接，片段已经问了「不是你本人的操作？」，这里直接接动作。
  EMAIL_CHANGED_NOTICE(
      "email-changed-notice", "你的 Patra 邮箱已更改", "点下面的链接撤销：邮箱会改回本地址，所有设备下线，并给你发重设密码的链接", false),
  /// 本邮箱已绑定（发新邮箱）。
  EMAIL_CHANGED_CONFIRMATION(
      "email-changed-confirmation", "本邮箱已绑定你的 Patra 账号", "如果不是你，请联系旧邮箱的主人或忽略", false),
  /// 邮箱已改回，请重设密码：撤销只能由读得到旧邮箱的人点，不是本人就说明邮箱被人看到了。
  EMAIL_CHANGE_REVERTED(
      "email-change-reverted",
      "你的 Patra 邮箱已改回，请重设密码",
      "说明有人能看到这个邮箱里的邮件：先保护好邮箱（改邮箱密码、检查收件转发），再用上面的链接设置新密码",
      false),
  /// 新设备登录。
  NEW_DEVICE_LOGIN(
      "new-device-login",
      "你的 Patra 账号在新设备上登录",
      "如果不是你，你的密码可能已经泄露：请到安全设置修改密码（其他设备会全部退出登录），并检查登录设备",
      false),
  /// 注销已提交。
  ACCOUNT_DELETION_REQUESTED(
      "account-deletion-requested",
      "你的 Patra 账号将被注销",
      "如果不是你，说明有人知道了你的密码：请在到期前登录并取消注销，然后立即修改密码；登录不了就用『忘记密码』重设",
      false),
  /// 已取消注销。
  ACCOUNT_DELETION_CANCELLED(
      "account-deletion-cancelled",
      "你的 Patra 账号已取消注销",
      "如果不是你，说明有人知道了你的密码：请立即修改密码，并在登录设备里退出不认识的设备；仍想注销可以重新申请",
      false),
  /// 账号即将删除：定时任务发的，不带安全通知片段。
  ACCOUNT_DELETION_REMINDER("account-deletion-reminder", null, null, false) {
    /// 「你的 Patra 账号将在 N 天后删除」：天数取自变量 `daysLeft`。
    ///
    /// @param facts 模板变量
    /// @return 主题
    @Override
    public String subjectFor(Map<String, Object> facts) {
      return "你的 Patra 账号将在 " + facts.get("daysLeft") + " 天后删除";
    }
  },
  /// 账号已注销：到期执行后发的，不带安全通知片段。
  ACCOUNT_DELETION_COMPLETED("account-deletion-completed", "你的 Patra 账号已注销", null, false);

  private final String template;
  private final String subject;
  private final String notMeAdvice;
  private final boolean skipsWhenLinkExpired;

  /// 创建。
  ///
  /// @param template 模板名
  /// @param subject 固定主题；随变量变化的类别为 `null` 并覆盖 {@link #subjectFor}
  /// @param notMeAdvice 不是本人操作时该怎么办；没有安全通知片段的类别为 `null`
  /// @param skipsWhenLinkExpired 只为链接而发、链接过期后不发
  SecurityMailKind(
      String template, String subject, String notMeAdvice, boolean skipsWhenLinkExpired) {
    this.template = template;
    this.subject = subject;
    this.notMeAdvice = notMeAdvice;
    this.skipsWhenLinkExpired = skipsWhenLinkExpired;
  }

  /// 模板名。
  ///
  /// @return 模板名，如 `password-reset`
  public String template() {
    return template;
  }

  /// 这一类信的主题。
  ///
  /// @param facts 这封信的模板变量，只有主题随变量变化的类别会读
  /// @return 主题
  public String subjectFor(Map<String, Object> facts) {
    return subject;
  }

  /// 不是本人操作时该怎么办，放进安全通知片段。
  ///
  /// @return 一句话；没有安全通知片段的类别为 `null`
  public String notMeAdvice() {
    return notMeAdvice;
  }

  /// 是否带安全通知片段（时间、设备、IP 与「不是我怎么办」）。
  ///
  /// @return 带片段为 `true`
  public boolean hasNotice() {
    return notMeAdvice != null;
  }

  /// 链接过期后是否不发：只为链接而发的信（验证邮箱、找回密码、改邮箱确认）过期了就没用了。
  ///
  /// @return 过期不发为 `true`
  public boolean skipsWhenLinkExpired() {
    return skipsWhenLinkExpired;
  }

  /// 几类安全通知共用的「不是我怎么办」。
  private static final class Advice {

    /// 密码被重设或修改：多半是邮箱被盗。
    static final String STOLEN_MAILBOX = "如果不是你，你的邮箱可能已被盗：请先找回邮箱，再用『忘记密码』重设密码";

    /// 不实例化。
    private Advice() {}
  }
}
