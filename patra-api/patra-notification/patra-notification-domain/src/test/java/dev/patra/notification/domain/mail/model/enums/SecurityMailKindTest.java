package dev.patra.notification.domain.mail.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SecurityMailKind 单元测试：13 类安全邮件的模板名、主题、「不是我怎么办」与过期规则。
@DisplayName("SecurityMailKind 单元测试")
class SecurityMailKindTest {

  /// 模板名就是 identity 事件路由键 `security-mail.<模板名>` 的后半段，顺序与契约表一致。
  @Test
  void should_use_routing_key_suffix_as_template() {
    assertThat(Arrays.stream(SecurityMailKind.values()).map(SecurityMailKind::template))
        .containsExactly(
            "email-verification",
            "password-reset",
            "password-reset-done",
            "password-changed",
            "email-change-confirm",
            "email-changed-notice",
            "email-changed-confirmation",
            "email-change-reverted",
            "new-device-login",
            "account-deletion-requested",
            "account-deletion-cancelled",
            "account-deletion-reminder",
            "account-deletion-completed");
  }

  /// 主题照搬 identity 的原文；注销提醒的主题按 `daysLeft` 拼。
  @Test
  void should_keep_identity_subjects() {
    Map<String, Object> none = Map.of();
    assertThat(SecurityMailKind.EMAIL_VERIFICATION.subjectFor(none)).isEqualTo("验证你的 Patra 邮箱");
    assertThat(SecurityMailKind.PASSWORD_RESET.subjectFor(none)).isEqualTo("重设你的 Patra 密码");
    assertThat(SecurityMailKind.PASSWORD_RESET_DONE.subjectFor(none)).isEqualTo("你的 Patra 密码已重设");
    assertThat(SecurityMailKind.PASSWORD_CHANGED.subjectFor(none)).isEqualTo("你的 Patra 密码已修改");
    assertThat(SecurityMailKind.EMAIL_CHANGE_CONFIRM.subjectFor(none)).isEqualTo("确认你的新 Patra 邮箱");
    assertThat(SecurityMailKind.EMAIL_CHANGED_NOTICE.subjectFor(none)).isEqualTo("你的 Patra 邮箱已更改");
    assertThat(SecurityMailKind.EMAIL_CHANGED_CONFIRMATION.subjectFor(none))
        .isEqualTo("本邮箱已绑定你的 Patra 账号");
    assertThat(SecurityMailKind.EMAIL_CHANGE_REVERTED.subjectFor(none))
        .isEqualTo("你的 Patra 邮箱已改回，请重设密码");
    assertThat(SecurityMailKind.NEW_DEVICE_LOGIN.subjectFor(none)).isEqualTo("你的 Patra 账号在新设备上登录");
    assertThat(SecurityMailKind.ACCOUNT_DELETION_REQUESTED.subjectFor(none))
        .isEqualTo("你的 Patra 账号将被注销");
    assertThat(SecurityMailKind.ACCOUNT_DELETION_CANCELLED.subjectFor(none))
        .isEqualTo("你的 Patra 账号已取消注销");
    assertThat(SecurityMailKind.ACCOUNT_DELETION_REMINDER.subjectFor(Map.of("daysLeft", 3L)))
        .isEqualTo("你的 Patra 账号将在 3 天后删除");
    assertThat(SecurityMailKind.ACCOUNT_DELETION_COMPLETED.subjectFor(none))
        .isEqualTo("你的 Patra 账号已注销");
  }

  /// 带安全通知片段的恰好是 8 类，「不是我怎么办」照搬 identity 原文；其余 5 类没有。
  @Test
  void should_have_advice_exactly_for_notice_kinds() {
    String stolenMailbox = "如果不是你，你的邮箱可能已被盗：请先找回邮箱，再用『忘记密码』重设密码";
    assertThat(SecurityMailKind.PASSWORD_RESET_DONE.notMeAdvice()).isEqualTo(stolenMailbox);
    assertThat(SecurityMailKind.PASSWORD_CHANGED.notMeAdvice()).isEqualTo(stolenMailbox);
    assertThat(SecurityMailKind.EMAIL_CHANGED_NOTICE.notMeAdvice())
        .isEqualTo("点下面的链接撤销：邮箱会改回本地址，所有设备下线，并给你发重设密码的链接");
    assertThat(SecurityMailKind.EMAIL_CHANGED_CONFIRMATION.notMeAdvice())
        .isEqualTo("如果不是你，请联系旧邮箱的主人或忽略");
    assertThat(SecurityMailKind.EMAIL_CHANGE_REVERTED.notMeAdvice())
        .isEqualTo("说明有人能看到这个邮箱里的邮件：先保护好邮箱（改邮箱密码、检查收件转发），再用上面的链接设置新密码");
    assertThat(SecurityMailKind.NEW_DEVICE_LOGIN.notMeAdvice())
        .isEqualTo("如果不是你，你的密码可能已经泄露：请到安全设置修改密码（其他设备会全部退出登录），并检查登录设备");
    assertThat(SecurityMailKind.ACCOUNT_DELETION_REQUESTED.notMeAdvice())
        .isEqualTo("如果不是你，说明有人知道了你的密码：请在到期前登录并取消注销，然后立即修改密码；登录不了就用『忘记密码』重设");
    assertThat(SecurityMailKind.ACCOUNT_DELETION_CANCELLED.notMeAdvice())
        .isEqualTo("如果不是你，说明有人知道了你的密码：请立即修改密码，并在登录设备里退出不认识的设备；仍想注销可以重新申请");
    assertThat(Arrays.stream(SecurityMailKind.values()).filter(SecurityMailKind::hasNotice))
        .containsExactlyInAnyOrder(
            SecurityMailKind.PASSWORD_RESET_DONE,
            SecurityMailKind.PASSWORD_CHANGED,
            SecurityMailKind.EMAIL_CHANGED_NOTICE,
            SecurityMailKind.EMAIL_CHANGED_CONFIRMATION,
            SecurityMailKind.EMAIL_CHANGE_REVERTED,
            SecurityMailKind.NEW_DEVICE_LOGIN,
            SecurityMailKind.ACCOUNT_DELETION_REQUESTED,
            SecurityMailKind.ACCOUNT_DELETION_CANCELLED);
    assertThat(
            Arrays.stream(SecurityMailKind.values())
                .filter(kind -> !kind.hasNotice())
                .map(SecurityMailKind::notMeAdvice))
        .hasSize(5)
        .containsOnlyNulls();
  }

  /// 链接过期就不发的恰好是三类只为链接而发的信（spec 第 5 节）。
  @Test
  void should_skip_only_link_purpose_kinds_when_expired() {
    assertThat(
            Arrays.stream(SecurityMailKind.values()).filter(SecurityMailKind::skipsWhenLinkExpired))
        .containsExactlyInAnyOrder(
            SecurityMailKind.EMAIL_VERIFICATION,
            SecurityMailKind.PASSWORD_RESET,
            SecurityMailKind.EMAIL_CHANGE_CONFIRM);
  }
}
