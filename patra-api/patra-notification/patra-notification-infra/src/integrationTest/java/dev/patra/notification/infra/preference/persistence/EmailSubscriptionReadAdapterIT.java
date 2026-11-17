package dev.patra.notification.infra.preference.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.read.EmailModes;
import dev.patra.notification.domain.preference.model.read.EmailSubscription;
import dev.patra.notification.domain.preference.port.read.EmailSubscriptionReadPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 给 mail 的读端口：只读。要发信的人在写提醒时已预建偏好行，读不到行视为不变量被破坏。
class EmailSubscriptionReadAdapterIT extends PreferencePersistenceITSupport {

  @Autowired private EmailSubscriptionReadPort subscriptions;

  /// 已有行（用户改过档位）时不动它。
  @Test
  void existing_row_is_returned_as_is() {
    insertRow(7L, "DIGEST", "OFF", TOKEN_A);

    EmailSubscription subscription = subscriptions.subscriptionOf(7L);

    assertThat(subscription.reply()).isEqualTo(EmailMode.DIGEST);
    assertThat(subscription.mention()).isEqualTo(EmailMode.OFF);
    assertThat(subscription.unsubscribeToken()).isEqualTo(TOKEN_A);
  }

  /// 读端口只读：读多少次都不建行、不改行（偏好行由写提醒时的监听器预建）。
  @Test
  void should_not_insert_when_reading() {
    insertRow(7L, "INSTANT", "INSTANT", TOKEN_A);

    subscriptions.subscriptionOf(7L);
    subscriptions.subscriptionOf(7L);

    assertThat(rows()).isEqualTo(1L);
    assertThat(subscriptions.subscriptionOf(7L).unsubscribeToken()).isEqualTo(TOKEN_A);
  }

  /// 要发信的人一定已经有偏好行；读不到说明不变量被破坏，抛异常走单封失败的路径，不悄悄补一行。
  @Test
  void should_fail_when_preference_row_missing() {
    assertThatThrownBy(() -> subscriptions.subscriptionOf(8L))
        .isInstanceOf(IllegalStateException.class);
    assertThat(rows()).isZero();
  }

  /// 至少一类是每日汇总的人，按用户 ID 升序分页；不建行。
  @Test
  void digest_subscribers_lists_only_digest_and_pages() {
    insertRow(5L, "DIGEST", "OFF", "A".repeat(43));
    insertRow(6L, "INSTANT", "INSTANT", "B".repeat(43));
    insertRow(7L, "OFF", "DIGEST", "C".repeat(43));
    insertRow(8L, "DIGEST", "DIGEST", "D".repeat(43));

    assertThat(subscriptions.digestSubscribers(0L, 10))
        .extracting(EmailSubscription::userId)
        .containsExactly(5L, 7L, 8L);
    assertThat(subscriptions.digestSubscribers(5L, 1))
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.userId()).isEqualTo(7L);
              assertThat(s.mention()).isEqualTo(EmailMode.DIGEST);
              assertThat(s.unsubscribeToken()).isEqualTo("C".repeat(43));
            });
    assertThat(rows()).isEqualTo(4L);
  }

  /// 只读档位：没有行时给默认值，也不建行。
  @Test
  void find_subscription_defaults_without_inserting() {
    assertThat(subscriptions.findSubscription(7L))
        .isEqualTo(EmailModes.of(EmailMode.INSTANT, EmailMode.INSTANT));
    assertThat(rows()).isZero();

    insertRow(7L, "DIGEST", "OFF", "A".repeat(43));
    assertThat(subscriptions.findSubscription(7L))
        .isEqualTo(EmailModes.of(EmailMode.DIGEST, EmailMode.OFF));
  }
}
