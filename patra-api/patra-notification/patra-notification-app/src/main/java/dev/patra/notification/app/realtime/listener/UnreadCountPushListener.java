package dev.patra.notification.app.realtime.listener;

import dev.patra.notification.app.realtime.service.UnreadCountStreamRegistry;
import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/// 未读数变了就推（PAP-145，决策 I）：信箱写入的事务**提交后**才推，回滚时 Spring 不会调用这里。
///
/// 换到 Boot 的 `applicationTaskExecutor` 线程上推（`@Async`，boot 层开启）：提交事务的是 RabbitMQ 消费线程或「标已读」的
/// 请求线程，推送的慢和错不能落到它们头上；提交后的回调里事务资源还挂在原线程上，换线程读库是干净的上下文。
/// 只读不写，所以不开新事务（未读数的读端口不要求事务）。
///
/// 普通的 `@TransactionalEventListener` 不进 Modulith 登记表：登记表只认 `@ApplicationModuleListener`
/// （`patra-tool-events` 的默认值），推送不会在重启后被补推。
@Component
@RequiredArgsConstructor
public class UnreadCountPushListener {

  private final UnreadCountStreamRegistry registry;

  /// 给未读数变了的人推一次当前值。
  ///
  /// @param event 未读数变化
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(UnreadCountsChangedEvent event) {
    registry.push(event.recipientIds());
  }
}
