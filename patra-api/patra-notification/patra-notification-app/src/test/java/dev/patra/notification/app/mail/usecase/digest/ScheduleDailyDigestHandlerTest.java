package dev.patra.notification.app.mail.usecase.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.mail.model.aggregate.ReminderDelivery;
import dev.patra.notification.domain.mail.model.enums.DeliveryKind;
import dev.patra.notification.domain.mail.policy.ReminderTimeText;
import dev.patra.notification.domain.mail.port.repository.ReminderDeliveryRepository;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.read.EmailModes;
import dev.patra.notification.domain.preference.model.read.EmailSubscription;
import dev.patra.notification.domain.preference.port.read.EmailSubscriptionReadPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// 每日汇总建名单：定时按北京时间 8:00 的窗口给设为汇总的人各建一条，补跑不重复；手动只给列出的人（spec 5.2、7.4）。
class ScheduleDailyDigestHandlerTest {

  /// 北京时间 10 月 31 日 08:10。
  private static final Instant NOW = Instant.parse("2026-10-31T00:10:00Z");
  private static final Instant EIGHT = Instant.parse("2026-10-31T00:00:00Z");

  private final EmailSubscriptionReadPort subscriptions = mock(EmailSubscriptionReadPort.class);
  private final ReminderDeliveryRepository deliveries = mock(ReminderDeliveryRepository.class);
  private final ScheduleDailyDigestHandler handler =
      new ScheduleDailyDigestHandler(
          subscriptions,
          deliveries,
          ReminderTimeText.of(ZoneId.of("Asia/Shanghai")),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 定时：窗口右端是当天 8:00，每个设为汇总的人一条，马上可以尝试。
  @Test
  void scheduled_run_uses_today_eight_oclock() {
    when(subscriptions.digestSubscribers(0L, 500))
        .thenReturn(List.of(subscription(5L), subscription(8L)));
    when(subscriptions.digestSubscribers(8L, 500)).thenReturn(List.of());
    when(deliveries.insertDigestIfAbsent(any())).thenReturn(true);

    DigestScheduleResult result = handler.handle(ScheduleDailyDigestCommand.scheduled());

    ArgumentCaptor<ReminderDelivery> created = ArgumentCaptor.captor();
    verify(deliveries, times(2)).insertDigestIfAbsent(created.capture());
    assertThat(created.getAllValues())
        .allSatisfy(
            d -> {
              assertThat(d.getKind()).isEqualTo(DeliveryKind.DIGEST);
              assertThat(d.getWindowEnd()).isEqualTo(EIGHT);
              assertThat(d.getNextAttemptAt()).isEqualTo(NOW);
            })
        .extracting(ReminderDelivery::getRecipientId)
        .containsExactly(5L, 8L);
    assertThat(result.windowEnd()).isEqualTo(EIGHT);
    assertThat(result.subscribers()).isEqualTo(2);
    assertThat(result.created()).isEqualTo(2);
  }

  /// 补跑：已经建过的不再建。
  @Test
  void rerun_creates_nothing_new() {
    when(subscriptions.digestSubscribers(0L, 500)).thenReturn(List.of(subscription(5L)));
    when(subscriptions.digestSubscribers(5L, 500)).thenReturn(List.of());
    when(deliveries.insertDigestIfAbsent(any())).thenReturn(false);

    DigestScheduleResult result = handler.handle(ScheduleDailyDigestCommand.scheduled());

    assertThat(result.created()).isZero();
    assertThat(result.subscribers()).isEqualTo(1);
  }

  /// 手动：窗口右端是此刻截到分钟，只给列出的、此刻有汇总类别的人建，不翻名单。
  @Test
  void manual_run_ends_now_and_only_targets_listed_digest_users() {
    when(subscriptions.findSubscription(5L))
        .thenReturn(EmailModes.of(EmailMode.INSTANT, EmailMode.DIGEST));
    when(subscriptions.findSubscription(6L)).thenReturn(EmailModes.DEFAULT);
    when(deliveries.insertDigestIfAbsent(any())).thenReturn(true);

    DigestScheduleResult result = handler.handle(ScheduleDailyDigestCommand.manual(Set.of(6L, 5L)));

    ArgumentCaptor<ReminderDelivery> created = ArgumentCaptor.captor();
    verify(deliveries).insertDigestIfAbsent(created.capture());
    assertThat(created.getValue().getRecipientId()).isEqualTo(5L);
    assertThat(created.getValue().getWindowEnd()).isEqualTo(NOW);
    verify(subscriptions, never()).digestSubscribers(anyLong(), anyInt());
    assertThat(result.subscribers()).isEqualTo(1);
  }

  /// 名单分页；顺带删掉结束超过 7 天的投递记录。
  @Test
  void pages_subscribers_and_purges_old_records() {
    List<EmailSubscription> full =
        LongStream.rangeClosed(1, 500)
            .mapToObj(ScheduleDailyDigestHandlerTest::subscription)
            .toList();
    when(subscriptions.digestSubscribers(0L, 500)).thenReturn(full);
    when(subscriptions.digestSubscribers(500L, 500)).thenReturn(List.of(subscription(501L)));
    when(subscriptions.digestSubscribers(501L, 500)).thenReturn(List.of());
    when(deliveries.deleteFinishedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(3);

    DigestScheduleResult result = handler.handle(ScheduleDailyDigestCommand.scheduled());

    assertThat(result.subscribers()).isEqualTo(501);
    assertThat(result.purged()).isEqualTo(3);
  }

  private static EmailSubscription subscription(long userId) {
    return EmailSubscription.of(userId, EmailMode.DIGEST, EmailMode.INSTANT, "T".repeat(43));
  }
}
