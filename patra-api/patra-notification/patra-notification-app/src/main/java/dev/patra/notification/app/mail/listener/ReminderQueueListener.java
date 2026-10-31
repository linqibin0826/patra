package dev.patra.notification.app.mail.listener;

import dev.patra.notification.domain.inbox.event.NotificationsCreatedEvent;
import dev.patra.notification.domain.mail.model.vo.QueuedReminder;
import dev.patra.notification.domain.mail.port.repository.ReminderQueueRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/// 提醒写入信箱时排进离站邮件的队（spec 2.2、5.1）。
///
/// 同步 `@EventListener`：与信箱写入同一线程、同一事务，排队行和提醒一起提交或一起回滚；不能用提交后的监听，
/// 提交与入队之间进程一挂，这封邮件就永远丢了。不加 `@Transactional`，仓储的 `enqueue` 要求调用方已有事务。
/// 只查这一条是哪一类、写入时刻是多少，偏好与收件人到点时再查。
@Component
@RequiredArgsConstructor
public class ReminderQueueListener {

  private final ReminderQueueRepository queue;

  /// 回复与 @ 入队，按写入信箱的时刻起算 10 分钟；有帮助没有邮件。
  ///
  /// @param event 提醒生成事件
  @EventListener
  public void on(NotificationsCreatedEvent event) {
    List<QueuedReminder> rows =
        event.notifications().stream()
            .filter(created -> created.category().hasEmail())
            .map(
                created ->
                    QueuedReminder.queue(
                        created.notificationId(),
                        created.recipientId(),
                        created.category(),
                        created.createdAt()))
            .toList();
    if (!rows.isEmpty()) {
      queue.enqueue(rows);
    }
  }
}
