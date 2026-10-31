package dev.patra.notification.domain.preference.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.preference.exception.InvalidPreferenceRequestException;
import dev.patra.notification.domain.preference.model.enums.UnsubscribeCategory;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import org.junit.jupiter.api.Test;

/// 一个用户的邮件偏好：默认值、按类改档位（spec 5.1）。
class EmailPreferenceTest {

  private static final UnsubscribeToken TOKEN = new UnsubscribeToken("T".repeat(43));

  /// 新用户两类都是离站时逐条。
  @Test
  void default_is_instant_for_reply_and_mention() {
    EmailPreference preference = EmailPreference.createDefault(7L, TOKEN);

    assertThat(preference.getUserId()).isEqualTo(7L);
    assertThat(preference.getUnsubscribeToken()).isEqualTo(TOKEN);
    assertThat(preference.modeOf(NotificationCategory.REPLY)).isEqualTo(EmailMode.INSTANT);
    assertThat(preference.modeOf(NotificationCategory.MENTION)).isEqualTo(EmailMode.INSTANT);
  }

  /// 有帮助只在站内，读档位永远是关。
  @Test
  void helpful_mode_is_always_off() {
    EmailPreference preference = preference(EmailMode.DIGEST, EmailMode.DIGEST);

    assertThat(preference.modeOf(NotificationCategory.HELPFUL)).isEqualTo(EmailMode.OFF);
  }

  /// 改一类不动另一类。
  @Test
  void change_sets_only_that_category() {
    EmailPreference preference = EmailPreference.createDefault(7L, TOKEN);

    preference.change(NotificationCategory.REPLY, EmailMode.DIGEST);

    assertThat(preference.getReply()).isEqualTo(EmailMode.DIGEST);
    assertThat(preference.getMention()).isEqualTo(EmailMode.INSTANT);
  }

  /// 有帮助没有邮件选项，改它是请求错误。
  @Test
  void change_helpful_is_rejected() {
    EmailPreference preference = EmailPreference.createDefault(7L, TOKEN);

    assertThatThrownBy(() -> preference.change(NotificationCategory.HELPFUL, EmailMode.INSTANT))
        .isInstanceOf(InvalidPreferenceRequestException.class);
  }

  /// 把「回复我的」改成每日汇总后再点旧的逐条邮件退订：意思仍是别再发回复邮件。
  @Test
  void reply_unsubscribe_turns_off_regardless_of_current_mode() {
    EmailPreference preference = preference(EmailMode.DIGEST, EmailMode.INSTANT);

    assertThat(preference.unsubscribe(UnsubscribeCategory.REPLY))
        .containsExactly(NotificationCategory.REPLY);
    assertThat(preference.getReply()).isEqualTo(EmailMode.OFF);
    assertThat(preference.getMention()).isEqualTo(EmailMode.INSTANT);
  }

  /// 「@ 我的」的退订只关它。
  @Test
  void mention_unsubscribe_turns_off_only_mention() {
    EmailPreference preference = EmailPreference.createDefault(7L, TOKEN);

    assertThat(preference.unsubscribe(UnsubscribeCategory.MENTION))
        .containsExactly(NotificationCategory.MENTION);
    assertThat(preference.getReply()).isEqualTo(EmailMode.INSTANT);
    assertThat(preference.getMention()).isEqualTo(EmailMode.OFF);
  }

  /// 每日汇总的退订只关此刻在汇总的类别，逐条的不动。
  @Test
  void digest_unsubscribe_turns_off_only_digest_categories() {
    EmailPreference preference = preference(EmailMode.DIGEST, EmailMode.INSTANT);

    assertThat(preference.unsubscribe(UnsubscribeCategory.DIGEST))
        .containsExactly(NotificationCategory.REPLY);
    assertThat(preference.getReply()).isEqualTo(EmailMode.OFF);
    assertThat(preference.getMention()).isEqualTo(EmailMode.INSTANT);
  }

  /// 关闭全部：两类都关，按回复、@ 的顺序列出。
  @Test
  void all_unsubscribe_turns_off_both_in_order() {
    EmailPreference preference = preference(EmailMode.DIGEST, EmailMode.INSTANT);

    assertThat(preference.unsubscribe(UnsubscribeCategory.ALL))
        .containsExactly(NotificationCategory.REPLY, NotificationCategory.MENTION);
    assertThat(preference.getReply()).isEqualTo(EmailMode.OFF);
    assertThat(preference.getMention()).isEqualTo(EmailMode.OFF);
  }

  /// 重复退订：没有类别从开变成关，状态不变。
  @Test
  void repeated_unsubscribe_returns_empty_and_keeps_state() {
    EmailPreference preference = preference(EmailMode.OFF, EmailMode.INSTANT);

    assertThat(preference.unsubscribe(UnsubscribeCategory.REPLY)).isEmpty();
    assertThat(preference.unsubscribe(UnsubscribeCategory.DIGEST)).isEmpty();
    assertThat(preference.getReply()).isEqualTo(EmailMode.OFF);
    assertThat(preference.getMention()).isEqualTo(EmailMode.INSTANT);
  }

  /// 已经退订过：`REPLY` / `MENTION` 看这一类是不是关；`DIGEST` 看是不是没有类别在汇总；`ALL` 看是不是全关。
  @Test
  void already_unsubscribed_follows_spec_5_3() {
    EmailPreference replyOff = preference(EmailMode.OFF, EmailMode.DIGEST);
    assertThat(replyOff.isUnsubscribedFrom(UnsubscribeCategory.REPLY)).isTrue();
    assertThat(replyOff.isUnsubscribedFrom(UnsubscribeCategory.MENTION)).isFalse();
    assertThat(replyOff.isUnsubscribedFrom(UnsubscribeCategory.DIGEST)).isFalse();
    assertThat(replyOff.isUnsubscribedFrom(UnsubscribeCategory.ALL)).isFalse();

    EmailPreference noDigest = preference(EmailMode.INSTANT, EmailMode.OFF);
    assertThat(noDigest.isUnsubscribedFrom(UnsubscribeCategory.DIGEST)).isTrue();

    EmailPreference allOff = preference(EmailMode.OFF, EmailMode.OFF);
    assertThat(allOff.isUnsubscribedFrom(UnsubscribeCategory.ALL)).isTrue();
  }

  /// 退订这一类之后还有没有邮件开着：`REPLY` 看「@ 我的」，`DIGEST` 看有没有逐条的，`ALL` 永远没有。
  @Test
  void any_email_on_after_follows_spec_5_3() {
    EmailPreference preference = preference(EmailMode.DIGEST, EmailMode.INSTANT);
    assertThat(preference.anyEmailOnAfter(UnsubscribeCategory.REPLY)).isTrue();
    assertThat(preference.anyEmailOnAfter(UnsubscribeCategory.MENTION)).isTrue();
    assertThat(preference.anyEmailOnAfter(UnsubscribeCategory.DIGEST)).isTrue();
    assertThat(preference.anyEmailOnAfter(UnsubscribeCategory.ALL)).isFalse();

    EmailPreference bothDigest = preference(EmailMode.DIGEST, EmailMode.DIGEST);
    assertThat(bothDigest.anyEmailOnAfter(UnsubscribeCategory.DIGEST)).isFalse();

    EmailPreference mentionOff = preference(EmailMode.INSTANT, EmailMode.OFF);
    assertThat(mentionOff.anyEmailOnAfter(UnsubscribeCategory.REPLY)).isFalse();
  }

  /// 判断不改状态。
  @Test
  void flags_do_not_change_state() {
    EmailPreference preference = preference(EmailMode.DIGEST, EmailMode.INSTANT);

    preference.isUnsubscribedFrom(UnsubscribeCategory.ALL);
    preference.anyEmailOnAfter(UnsubscribeCategory.ALL);

    assertThat(preference.getReply()).isEqualTo(EmailMode.DIGEST);
    assertThat(preference.getMention()).isEqualTo(EmailMode.INSTANT);
  }

  /// 从库里恢复一份偏好。
  ///
  /// @param reply 「回复我的」档位
  /// @param mention 「@ 我的」档位
  /// @return 偏好
  private static EmailPreference preference(EmailMode reply, EmailMode mention) {
    return EmailPreference.restore(7L, TOKEN, reply, mention);
  }
}
