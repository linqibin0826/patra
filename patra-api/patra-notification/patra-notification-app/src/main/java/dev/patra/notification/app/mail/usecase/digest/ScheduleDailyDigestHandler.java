package dev.patra.notification.app.mail.usecase.digest;

import dev.patra.notification.domain.mail.model.aggregate.ReminderDelivery;
import dev.patra.notification.domain.mail.model.vo.DigestWindow;
import dev.patra.notification.domain.mail.policy.ReminderTimeText;
import dev.patra.notification.domain.mail.port.repository.ReminderDeliveryRepository;
import dev.patra.notification.domain.preference.model.read.EmailSubscription;
import dev.patra.notification.domain.preference.port.read.EmailSubscriptionReadPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 建每日汇总的投递（spec 5.2、7.4）。只建记录、不发信：下一分钟的 `reminderMailJob` 装配并发出。
///
/// 定时：窗口右端是北京时间当天 8:00，按用户 ID 升序分页取设为汇总的人，每人建一条；一个人一个窗口只有一条（唯一约束），
/// 8 点那一小时里补跑不会多建。手动：只给列出的、此刻有汇总类别的人建，窗口右端是此刻截到分钟。
/// 顺带删掉结束超过 {@link #RETENTION} 的投递记录。
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleDailyDigestHandler
    implements CommandHandler<ScheduleDailyDigestCommand, DigestScheduleResult> {

  /// 名单一页取几个人。
  static final int PAGE = 500;

  /// 结束的投递记录留多久。
  static final Duration RETENTION = Duration.ofDays(7);

  private final EmailSubscriptionReadPort subscriptions;
  private final ReminderDeliveryRepository deliveries;
  private final ReminderTimeText time;
  private final Clock clock;

  /// 建一轮。
  ///
  /// @param command 命令
  /// @return 计数
  @Override
  public DigestScheduleResult handle(ScheduleDailyDigestCommand command) {
    Instant now = clock.instant();
    Instant windowEnd =
        command.manual()
            ? DigestWindow.manualEnd(now)
            : DigestWindow.scheduledEnd(now, time.zone());
    int subscribers = 0;
    int created = 0;
    if (command.manual()) {
      for (long userId : command.manualUsers().stream().sorted().toList()) {
        if (subscriptions.findSubscription(userId).anyDigest()) {
          subscribers++;
          created += create(userId, windowEnd, now);
        }
      }
    } else {
      long cursor = 0L;
      List<EmailSubscription> page;
      while (!(page = subscriptions.digestSubscribers(cursor, PAGE)).isEmpty()) {
        cursor = page.getLast().userId();
        for (EmailSubscription subscription : page) {
          subscribers++;
          created += create(subscription.userId(), windowEnd, now);
        }
      }
    }
    int purged = deliveries.deleteFinishedBefore(now.minus(RETENTION));
    log.info(
        "每日汇总建名单: windowEnd={}, manual={}, subscribers={}, created={}, purged={}",
        windowEnd,
        command.manual(),
        subscribers,
        created,
        purged);
    return DigestScheduleResult.of(windowEnd, subscribers, created, purged);
  }

  /// 给一个人建这个窗口的汇总投递。
  ///
  /// @param userId 用户 ID
  /// @param windowEnd 窗口右端
  /// @param now 此刻
  /// @return 真建了为 1，已有为 0
  private int create(long userId, Instant windowEnd, Instant now) {
    return deliveries.insertDigestIfAbsent(
            ReminderDelivery.digest(UUID.randomUUID(), userId, windowEnd, now))
        ? 1
        : 0;
  }
}
