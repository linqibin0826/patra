package dev.patra.notification.app.inbox.service;

import dev.patra.notification.domain.inbox.event.NotificationsCreatedEvent;
import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.inbox.model.read.CreatedNotification;
import dev.patra.notification.domain.inbox.model.vo.NotificationDraft;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/// 写提醒并发领域事件（评论发表、编辑共用，PAP-144 的有帮助也用）。必须在调用方的事务里调：事件随之发布，回滚就没有。
@Component
@RequiredArgsConstructor
public class NotificationWriter {

  private final NotificationRepository notifications;
  private final ApplicationEventPublisher events;

  /// 写入一批提醒；真写入了就发「提醒生成」与「未读数变了」。
  ///
  /// @param drafts 草稿，可空列表
  /// @return 真写入的提醒
  public List<CreatedNotification> write(List<NotificationDraft> drafts) {
    if (drafts.isEmpty()) {
      return List.of();
    }
    List<CreatedNotification> created = notifications.insertAll(drafts);
    if (!created.isEmpty()) {
      events.publishEvent(NotificationsCreatedEvent.of(created));
      events.publishEvent(
          UnreadCountsChangedEvent.of(
              created.stream().map(CreatedNotification::recipientId).toList()));
    }
    return created;
  }
}
