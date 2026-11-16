package dev.patra.notification.app.preference.listener;

import dev.patra.notification.domain.inbox.event.NotificationsCreatedEvent;
import dev.patra.notification.domain.inbox.model.read.CreatedNotification;
import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/// 提醒写入信箱时，为会收到邮件的收件人预建默认偏好行（两类都是即时，带新的退订令牌）。
///
/// 发信时要读收件人的档位和退订令牌，读端口不能写库（库表规范第 8 节），所以行要在这之前就有。会发邮件的只有回复、@ 两类
/// 提醒，它们都经信箱的写入发 `NotificationsCreatedEvent`，在这里预建就覆盖了所有发信对象。同步 `@EventListener`：
/// 与写提醒同一事务，偏好行和提醒一起提交或回滚；已有行时 `insertIfAbsent` 什么也不做。
@Component
@RequiredArgsConstructor
public class EmailPreferenceCreationListener {

  private final EmailPreferenceRepository preferences;
  private final SecureRandom random = new SecureRandom();

  /// 每个会收到邮件的收件人各建一次。
  ///
  /// @param event 提醒生成事件
  @EventListener
  public void on(NotificationsCreatedEvent event) {
    event.notifications().stream()
        .filter(created -> created.category().hasEmail())
        .map(CreatedNotification::recipientId)
        .distinct()
        .forEach(
            recipientId ->
                preferences.insertIfAbsent(
                    EmailPreference.createDefault(recipientId, UnsubscribeToken.generate(random))));
  }
}
