package dev.patra.notification.app.mail.usecase.send;

import dev.patra.notification.app.mail.service.AssembledReminder;
import dev.patra.notification.app.mail.service.AssembledReminder.Ready;
import dev.patra.notification.app.mail.service.AssembledReminder.Skip;
import dev.patra.notification.app.mail.service.ReminderDeliveryAssembler;
import dev.patra.notification.app.mail.service.ReminderMailModel;
import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.exception.InteractionUnavailableException;
import dev.patra.notification.domain.mail.exception.MailDeliveryFailedException;
import dev.patra.notification.domain.mail.model.aggregate.ReminderDelivery;
import dev.patra.notification.domain.mail.model.enums.SkipReason;
import dev.patra.notification.domain.mail.model.vo.OutgoingMail;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent;
import dev.patra.notification.domain.mail.port.delivery.MailDeliveryPort;
import dev.patra.notification.domain.mail.port.repository.ReminderDeliveryRepository;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 装配并发出到点的投递（spec 5.4、7.2、7.3）：按下一次尝试时刻先后，每次取 {@value #PAGE} 条，直到取完。
///
/// 每一条：装配 → 不该发记跳过；该发就先记下「第一次调用发信端口」，再在事务外调用发信端口（投递 ID 是幂等键）。
/// identity、interaction 不可用或临时发信故障：这一条推迟，本轮停下（别人也一样会失败）；永久拒绝：跳过、继续；
/// 意外异常：推迟、记 ERROR、继续。写回都是单条更新，不跨远程调用开事务。同一轮里每条只处理一次。
@Slf4j
@Component
@RequiredArgsConstructor
public class SendPendingDeliveriesHandler
    implements CommandHandler<SendPendingDeliveriesCommand, SendResult> {

  /// 一次取几条。
  static final int PAGE = 50;

  /// `List-Unsubscribe-Post` 的固定值（RFC 8058）。
  static final String ONE_CLICK = "List-Unsubscribe=One-Click";

  private final ReminderDeliveryRepository deliveries;
  private final ReminderDeliveryAssembler assembler;
  private final MailDeliveryPort mail;
  private final Clock clock;

  /// 发一轮。
  ///
  /// @param command 命令
  /// @return 计数
  @Override
  public SendResult handle(SendPendingDeliveriesCommand command) {
    Instant now = clock.instant();
    Set<UUID> seen = new HashSet<>();
    int sent = 0;
    int skipped = 0;
    int deferred = 0;
    while (true) {
      List<ReminderDelivery> page =
          deliveries.findDue(now, PAGE).stream().filter(d -> seen.add(d.getDeliveryId())).toList();
      if (page.isEmpty()) {
        break;
      }
      for (ReminderDelivery delivery : page) {
        Outcome outcome = attempt(delivery, now);
        switch (outcome) {
          case SENT -> sent++;
          case SKIPPED -> skipped++;
          case DEFERRED -> deferred++;
          case STOP -> {
            log.info("提醒邮件本轮提前停下: sent={}, skipped={}, deferred={}", sent, skipped, deferred + 1);
            return SendResult.of(sent, skipped, deferred + 1, true);
          }
        }
      }
    }
    if (sent + skipped + deferred > 0) {
      log.info("提醒邮件本轮: sent={}, skipped={}, deferred={}", sent, skipped, deferred);
    }
    return SendResult.of(sent, skipped, deferred, false);
  }

  /// 处理一条投递。
  ///
  /// @param delivery 投递
  /// @param now 此刻
  /// @return 结果
  private Outcome attempt(ReminderDelivery delivery, Instant now) {
    try {
      AssembledReminder assembled = assembler.assemble(delivery, now);
      if (assembled instanceof Skip skip) {
        delivery.skip(skip.reason(), now);
        deliveries.update(delivery);
        return Outcome.SKIPPED;
      }
      Ready ready = (Ready) assembled;
      delivery.recordSendAttempt(now);
      deliveries.update(delivery);
      return deliver(delivery, ready, now);
    } catch (IdentityUnavailableException | InteractionUnavailableException ex) {
      log.warn(
          "提醒邮件装配时依赖不可用，推迟: deliveryId={}, reason={}", delivery.getDeliveryId(), ex.getMessage());
      defer(delivery, now);
      return Outcome.STOP;
    } catch (RuntimeException ex) {
      log.error("提醒邮件处理失败，推迟: deliveryId={}", delivery.getDeliveryId(), ex);
      if (!delivery.getStatus().finished()) {
        defer(delivery, now);
      }
      return Outcome.DEFERRED;
    }
  }

  /// 调用发信端口并按结果写回。
  ///
  /// @param delivery 投递
  /// @param ready 装配好的邮件
  /// @param now 此刻
  /// @return 结果
  private Outcome deliver(ReminderDelivery delivery, Ready ready, Instant now) {
    ReminderMailContent content = ready.content();
    try {
      mail.deliver(
          OutgoingMail.builder()
              .deliveryId(delivery.getDeliveryId())
              .to(ready.to())
              .template(template(content))
              .subject(content.subject())
              .model(ReminderMailModel.of(content))
              .headers(
                  Map.of(
                      "List-Unsubscribe",
                      "<" + content.oneClickUrl() + ">",
                      "List-Unsubscribe-Post",
                      ONE_CLICK))
              .build());
    } catch (MailDeliveryFailedException ex) {
      switch (ex.failure()) {
        case ALREADY_ACCEPTED -> log.warn("提醒邮件之前已被接受，记为已发: {}", ex.getMessage());
        case REJECTED -> {
          log.warn("提醒邮件被永久拒绝，跳过: {}", ex.getMessage());
          delivery.skip(SkipReason.REJECTED, now);
          deliveries.update(delivery);
          return Outcome.SKIPPED;
        }
        case TRANSIENT -> {
          log.warn("提醒邮件临时发不出去，推迟: {}", ex.getMessage());
          defer(delivery, now);
          return Outcome.STOP;
        }
      }
    }
    delivery.markSent(now);
    deliveries.update(delivery);
    return Outcome.SENT;
  }

  /// 推迟并写回；已满 24 小时会变成过期。
  ///
  /// @param delivery 投递
  /// @param now 此刻
  private void defer(ReminderDelivery delivery, Instant now) {
    delivery.defer(now);
    if (delivery.getStatus().finished()) {
      log.error("提醒邮件第一次发送后 24 小时仍未成功，放弃: deliveryId={}", delivery.getDeliveryId());
    }
    deliveries.update(delivery);
  }

  /// 版式对应的模板名。
  ///
  /// @param content 内容
  /// @return 模板名
  private static String template(ReminderMailContent content) {
    return switch (content.layout()) {
      case SINGLE -> "notification-single";
      case BATCH -> "notification-batch";
      case DIGEST -> "notification-digest";
    };
  }

  /// 一条投递这一次的结果。
  private enum Outcome {

    /// 发出了。
    SENT,

    /// 不该发。
    SKIPPED,

    /// 推迟，继续下一条。
    DEFERRED,

    /// 推迟，本轮停下。
    STOP
  }
}
