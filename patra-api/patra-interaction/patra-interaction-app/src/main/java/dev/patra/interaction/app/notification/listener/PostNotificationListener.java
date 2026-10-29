package dev.patra.interaction.app.notification.listener;

import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import dev.patra.interaction.domain.post.event.PostDeletedEvent;
import dev.patra.tool.core.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/// 订阅投稿经验的删除事件，在删除的同一事务里删掉这条经验下的全部提醒（PAP-127）。
///
/// 同步 `@EventListener`：与发布方同一线程、同一事务；抛出异常时经验删除一起回滚。不加 `@Transactional`，也不能用
/// `@TransactionalEventListener`（提交后才执行，提醒会留下）。
@Component
@RequiredArgsConstructor
public class PostNotificationListener {

  private final NotificationRepository notifications;

  /// 经验已删除：删掉它下面的全部提醒。
  ///
  /// @param event 事件
  @EventListener
  public void on(PostDeletedEvent event) {
    try {
      notifications.deleteBySubject(event.subjectId());
    } catch (DomainException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new NotificationWriteFailedException(ex);
    }
  }
}
