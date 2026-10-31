package dev.patra.identity.infra.account.mail;

import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 邮件投递端口的实现：把邮件当领域事件发布，由事件模块（`patra-tool-events`）登记进调用方事务里的
/// `event_publication`，提交后映射成集成事件外送到 RabbitMQ，交给通知服务发信（PAP-104）。
///
/// `MANDATORY`：调用方没有事务就抛 `IllegalTransactionStateException`。事务外发布时 Modulith 的提交后监听器
/// 不会被调用，事件会悄悄丢掉，所以宁可立刻失败。日志只打邮件种类与事件 ID，不打收件人与链接。
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEmailDeliveryAdapter implements EmailDeliveryPort {

  private final ApplicationEventPublisher events;

  /// 在调用方的事务里发布邮件，登记待外送。
  ///
  /// @param mail 要发的邮件
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void send(OutgoingMail mail) {
    events.publishEvent(mail);
    log.info("安全邮件已登记待外送: type={}, eventId={}", mail.getClass().getSimpleName(), mail.eventId());
  }
}
