package dev.patra.notification.domain.mail.model.aggregate;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.model.enums.DeliveryKind;
import dev.patra.notification.domain.mail.model.enums.DeliveryStatus;
import dev.patra.notification.domain.mail.model.enums.SkipReason;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/// 一封提醒邮件的投递记录（spec 2.4、5.4、6.2）。投递 ID 是发信端口的幂等键，重试时不变。
///
/// 每次尝试都从头装配。还没调用过发信端口时（identity、interaction 挂着），失败只推迟、不过期；调用过以后结果可能
/// 不确定（比如超时其实已被收下），只在第一次调用后 {@link #SEND_WINDOW} 内重试：幂等键过了这段时间就失效，
/// 再发可能重复送达，于是改记过期。
@Getter
public final class ReminderDelivery {

  /// 调用过发信端口之后最多重试多久，等于服务商幂等键的有效期。
  public static final Duration SEND_WINDOW = Duration.ofHours(24);

  /// 退避的上限。
  private static final long MAX_BACKOFF_MINUTES = 30;

  private final UUID deliveryId;
  private final long recipientId;
  private final DeliveryKind kind;
  private final NotificationCategory category;
  private final List<Long> notificationIds;
  private final Instant windowEnd;
  private final Instant createdAt;
  private DeliveryStatus status;
  private SkipReason skipReason;
  private int attempts;
  private Instant nextAttemptAt;
  private Instant firstSentAt;
  private Instant finishedAt;

  /// 恢复或新建一条记录。
  ///
  /// @param deliveryId 投递 ID
  /// @param recipientId 收件人
  /// @param kind 种类
  /// @param category 逐条邮件的类别；汇总为空
  /// @param notificationIds 逐条邮件合进的提醒；汇总为空列表
  /// @param windowEnd 汇总窗口右端；逐条为空
  /// @param createdAt 建记录的时刻
  /// @param status 状态
  /// @param skipReason 跳过原因，只有跳过时有值
  /// @param attempts 已失败的次数
  /// @param nextAttemptAt 下一次可以尝试的时刻
  /// @param firstSentAt 第一次调用发信端口的时刻，可空
  /// @param finishedAt 结束的时刻，可空
  @Builder(builderMethodName = "restore")
  private ReminderDelivery(
      UUID deliveryId,
      long recipientId,
      DeliveryKind kind,
      NotificationCategory category,
      List<Long> notificationIds,
      Instant windowEnd,
      Instant createdAt,
      DeliveryStatus status,
      SkipReason skipReason,
      int attempts,
      Instant nextAttemptAt,
      Instant firstSentAt,
      Instant finishedAt) {
    this.deliveryId = Objects.requireNonNull(deliveryId, "deliveryId 不能为 null");
    this.recipientId = recipientId;
    this.kind = Objects.requireNonNull(kind, "kind 不能为 null");
    this.category = category;
    this.notificationIds = notificationIds == null ? List.of() : List.copyOf(notificationIds);
    this.windowEnd = windowEnd;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为 null");
    this.status = Objects.requireNonNull(status, "status 不能为 null");
    this.skipReason = skipReason;
    this.attempts = attempts;
    this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt 不能为 null");
    this.firstSentAt = firstSentAt;
    this.finishedAt = finishedAt;
  }

  /// 新建一封逐条邮件的投递，马上可以尝试。
  ///
  /// @param deliveryId 投递 ID
  /// @param recipientId 收件人
  /// @param category 类别
  /// @param notificationIds 合进这封的提醒，按入队先后
  /// @param now 此刻
  /// @return 待发的投递
  /// @throws IllegalArgumentException 类别没有邮件，或一条提醒都没有
  public static ReminderDelivery instant(
      UUID deliveryId,
      long recipientId,
      NotificationCategory category,
      List<Long> notificationIds,
      Instant now) {
    if (!category.hasEmail()) {
      throw new IllegalArgumentException("没有邮件的类别不建投递: " + category);
    }
    if (notificationIds.isEmpty()) {
      throw new IllegalArgumentException("逐条投递至少要有一条提醒");
    }
    return pending(
        deliveryId, recipientId, DeliveryKind.INSTANT, category, notificationIds, null, now);
  }

  /// 新建一封每日汇总的投递，马上可以尝试。
  ///
  /// @param deliveryId 投递 ID
  /// @param recipientId 收件人
  /// @param windowEnd 汇总窗口右端
  /// @param now 此刻
  /// @return 待发的投递
  public static ReminderDelivery digest(
      UUID deliveryId, long recipientId, Instant windowEnd, Instant now) {
    Objects.requireNonNull(windowEnd, "windowEnd 不能为 null");
    return pending(deliveryId, recipientId, DeliveryKind.DIGEST, null, List.of(), windowEnd, now);
  }

  /// 第 `attempts` 次失败之后等多久：1、2、4、8、16 分钟，之后一律 30 分钟。
  ///
  /// @param attempts 已失败的次数，至少 1
  /// @return 等待时长
  public static Duration backoff(int attempts) {
    if (attempts < 1) {
      throw new IllegalArgumentException("attempts 至少为 1: " + attempts);
    }
    return Duration.ofMinutes(attempts >= 6 ? MAX_BACKOFF_MINUTES : 1L << (attempts - 1));
  }

  /// 记下这一次要调用发信端口了；只有第一次会记。
  ///
  /// @param now 此刻
  public void recordSendAttempt(Instant now) {
    requirePending();
    if (firstSentAt == null) {
      firstSentAt = now;
    }
  }

  /// 发出去了。
  ///
  /// @param now 此刻
  public void markSent(Instant now) {
    finish(DeliveryStatus.SENT, null, now);
  }

  /// 确定不该发。
  ///
  /// @param reason 原因
  /// @param now 此刻
  public void skip(SkipReason reason, Instant now) {
    finish(DeliveryStatus.SKIPPED, Objects.requireNonNull(reason, "reason 不能为 null"), now);
  }

  /// 发送窗口是否已关：调用过发信端口、且已满 {@link #SEND_WINDOW}。关了以后不能再调发信端口（幂等键已失效），
  /// 即使这次装配得出来也只能记过期。
  ///
  /// @param now 此刻
  /// @return 关了为真；从没调用过发信端口时永远为假
  public boolean sendWindowClosed(Instant now) {
    return firstSentAt != null && !now.isBefore(firstSentAt.plus(SEND_WINDOW));
  }

  /// 记为过期：发送窗口已关，不再尝试。
  ///
  /// @param now 此刻
  public void expire(Instant now) {
    finish(DeliveryStatus.EXPIRED, null, now);
  }

  /// 这一次失败了，按退避推迟；调用过发信端口且已满 {@link #SEND_WINDOW} 时改记过期。
  ///
  /// @param now 此刻
  public void defer(Instant now) {
    requirePending();
    if (sendWindowClosed(now)) {
      expire(now);
      return;
    }
    attempts++;
    nextAttemptAt = now.plus(backoff(attempts));
  }

  /// 一条待发、马上可以尝试的新记录。
  ///
  /// @param deliveryId 投递 ID
  /// @param recipientId 收件人
  /// @param kind 种类
  /// @param category 类别，可空
  /// @param notificationIds 提醒 ID
  /// @param windowEnd 汇总窗口右端，可空
  /// @param now 此刻
  /// @return 记录
  private static ReminderDelivery pending(
      UUID deliveryId,
      long recipientId,
      DeliveryKind kind,
      NotificationCategory category,
      List<Long> notificationIds,
      Instant windowEnd,
      Instant now) {
    return restore()
        .deliveryId(deliveryId)
        .recipientId(recipientId)
        .kind(kind)
        .category(category)
        .notificationIds(notificationIds)
        .windowEnd(windowEnd)
        .createdAt(now)
        .status(DeliveryStatus.PENDING)
        .attempts(0)
        .nextAttemptAt(now)
        .build();
  }

  /// 结束这条投递。
  ///
  /// @param finalStatus 结局
  /// @param reason 跳过原因，可空
  /// @param now 此刻
  private void finish(DeliveryStatus finalStatus, SkipReason reason, Instant now) {
    requirePending();
    status = finalStatus;
    skipReason = reason;
    finishedAt = now;
  }

  /// 只有待发的记录能改。
  ///
  /// @throws IllegalStateException 已经结束
  private void requirePending() {
    if (status.finished()) {
      throw new IllegalStateException("投递已结束: " + deliveryId + " " + status);
    }
  }
}
