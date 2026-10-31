package dev.patra.notification.app.mail.usecase.security;

import dev.patra.notification.domain.mail.exception.MailDeliveryFailedException;
import dev.patra.notification.domain.mail.model.aggregate.MailDelivery;
import dev.patra.notification.domain.mail.model.vo.SecurityMail;
import dev.patra.notification.domain.mail.port.delivery.MailDeliveryPort;
import dev.patra.notification.domain.mail.port.repository.MailDeliveryRepository;
import dev.patra.tool.core.event.ProcessedEventPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 发一封 identity 请求的安全邮件（spec 第 5 节）。
///
/// 1. 按投递 ID（就是事件 ID）查发送记录，有就是重复投递，直接结束；
/// 2. 只为链接而发的信、链接已过期：不发，短事务里记 `LINK_EXPIRED`；
/// 3. 在事务外发信：发信是远程调用，不进事务（`transaction.md` 第 2 节）。失败按类别处理（spec 6.6）：
///    服务商已接受过这个幂等键，当作已发；永久拒收，短事务里记 `REJECTED`、不再重试；临时失败打 WARN 后
///    原样抛出，由监听容器重试、耗尽后进死信；
/// 4. 一个短事务里先 `markProcessed(队列, 事件 ID)`，第一次记下才追加发送记录。
///
/// 发出之后、第 3 步提交之前进程死了，重投时第 1 步查不到记录，会用同一个幂等键再发一次，由 Resend 挡掉。
/// 日志只打事件 ID 与类别，不打收件地址与链接。
@Slf4j
@Component
@RequiredArgsConstructor
public class SendSecurityMailHandler
    implements CommandHandler<SendSecurityMailCommand, SendSecurityMailResult> {

  private final MailDeliveryRepository deliveries;
  private final MailDeliveryPort mailDelivery;
  private final ProcessedEventPort processedEvents;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 处理一次投递。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  public SendSecurityMailResult handle(SendSecurityMailCommand command) {
    SecurityMail mail = command.mail();
    if (deliveries.exists(mail.eventId())) {
      log.info("安全邮件已处理过，跳过重复投递: kind={}, eventId={}", mail.kind(), mail.eventId());
      return SendSecurityMailResult.DUPLICATE;
    }
    Instant now = clock.instant();
    if (mail.linkExpiredAt(now)) {
      record(command, MailDelivery.linkExpired(mail.eventId(), mail.kind().template(), now));
      log.info("链接已过期，不发: kind={}, eventId={}", mail.kind(), mail.eventId());
      return SendSecurityMailResult.LINK_EXPIRED;
    }
    try {
      mailDelivery.deliver(mail.compose());
    } catch (MailDeliveryFailedException e) {
      return onFailure(command, e);
    }
    return recordSent(command);
  }

  /// 发信失败按类别处理（spec 6.6）；穷举 switch 表达式，加了新类别而这里没跟上时编译不过。
  ///
  /// @param command 命令
  /// @param e 发信失败
  /// @return 结果
  /// @throws MailDeliveryFailedException 临时失败，原样抛出交给重试与死信
  private SendSecurityMailResult onFailure(
      SendSecurityMailCommand command, MailDeliveryFailedException e) {
    SecurityMail mail = command.mail();
    return switch (e.failure()) {
      case ALREADY_ACCEPTED -> {
        log.info("服务商已接受过这个幂等键，按已发处理: kind={}, eventId={}", mail.kind(), mail.eventId());
        yield recordSent(command);
      }
      case REJECTED -> {
        record(
            command,
            MailDelivery.rejected(mail.eventId(), mail.kind().template(), clock.instant()));
        log.warn(
            "安全邮件被永久拒收，不再重试: kind={}, eventId={}, {}", mail.kind(), mail.eventId(), e.getMessage());
        yield SendSecurityMailResult.REJECTED;
      }
      case TRANSIENT -> {
        log.warn(
            "安全邮件发送失败，交给重试或死信: kind={}, eventId={}, {}",
            mail.kind(),
            mail.eventId(),
            e.getMessage());
        throw e;
      }
    };
  }

  /// 记已发并返回 `SENT`。
  ///
  /// @param command 命令
  /// @return `SENT`
  private SendSecurityMailResult recordSent(SendSecurityMailCommand command) {
    SecurityMail mail = command.mail();
    record(command, MailDelivery.sent(mail.eventId(), mail.kind().template(), clock.instant()));
    log.info("安全邮件已发送: kind={}, eventId={}", mail.kind(), mail.eventId());
    return SendSecurityMailResult.SENT;
  }

  /// 短事务：先记「这个事件处理过了」，第一次记下才追加发送记录（同一事件的另一份投递抢先提交时不再追加）。
  ///
  /// @param command 命令，取消费方名
  /// @param delivery 发送记录
  private void record(SendSecurityMailCommand command, MailDelivery delivery) {
    transactions.executeWithoutResult(
        status -> {
          if (processedEvents.markProcessed(command.consumer(), delivery.deliveryId())) {
            deliveries.add(delivery);
          }
        });
  }
}
