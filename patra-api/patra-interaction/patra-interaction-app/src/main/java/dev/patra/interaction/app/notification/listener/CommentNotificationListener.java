package dev.patra.interaction.app.notification.listener;

import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.model.vo.NotificationDraft;
import dev.patra.interaction.domain.notification.model.vo.Recipient;
import dev.patra.interaction.domain.notification.policy.ExcerptRules;
import dev.patra.interaction.domain.notification.policy.NotificationRules;
import dev.patra.interaction.domain.notification.port.excerpt.PlainTextPort;
import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import dev.patra.tool.core.error.DomainException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/// 订阅讨论的三个事件，在评论写入的同一事务里生成、更新提醒（决策 A，spec 6）。
///
/// 同步 `@EventListener`：与发布方同一线程、同一事务；抛出异常时评论、发表计数与提醒一起回滚。
/// 不加 `@Transactional`，也不能用 `@TransactionalEventListener`（提交后才执行，提醒会丢）。
@Component
@RequiredArgsConstructor
public class CommentNotificationListener {

  private final NotificationRepository notifications;
  private final PlainTextPort plainText;

  /// 评论已发表：按规则给被回复的人、经验作者、@ 到的人写提醒。
  ///
  /// @param event 事件
  @EventListener
  public void on(CommentPublishedEvent event) {
    guard(
        () -> {
          List<Recipient> recipients = NotificationRules.forPublished(event);
          if (recipients.isEmpty()) {
            return;
          }
          ExcerptText excerpt = excerptOf(event.body(), event.mentionedUserIds());
          notifications.insertAll(
              recipients.stream()
                  .map(
                      r ->
                          NotificationDraft.builder()
                              .recipientId(r.userId())
                              .type(r.type())
                              .actorId(event.authorId())
                              .actorAnonymous(event.authorAnonymous())
                              .subjectId(event.subjectId())
                              .commentId(event.commentId())
                              .excerpt(excerpt)
                              .createdAt(event.publishedAt())
                              .build())
                  .toList());
        });
  }

  /// 评论已编辑：刷新这条评论已有提醒的摘录，再给新增的 @ 写提醒。
  ///
  /// @param event 事件
  @EventListener
  public void on(CommentEditedEvent event) {
    guard(
        () -> {
          ExcerptText excerpt = excerptOf(event.body(), event.mentionedUserIds());
          notifications.refreshExcerpt(event.commentId(), excerpt);
          List<Long> added = NotificationRules.forEdited(event);
          if (added.isEmpty()) {
            return;
          }
          notifications.insertAll(
              added.stream()
                  .map(
                      userId ->
                          NotificationDraft.builder()
                              .recipientId(userId)
                              .type(NotificationType.MENTION)
                              .actorId(event.authorId())
                              .actorAnonymous(event.authorAnonymous())
                              .subjectId(event.subjectId())
                              .commentId(event.commentId())
                              .excerpt(excerpt)
                              .createdAt(event.editedAt())
                              .build())
                  .toList());
        });
  }

  /// 评论已删除：删掉它的全部提醒。
  ///
  /// @param event 事件
  @EventListener
  public void on(CommentDeletedEvent event) {
    guard(() -> notifications.deleteByComment(event.commentId()));
  }

  /// 评论正文转成存储用的摘录。
  ///
  /// @param body 正文
  /// @param mentionedUserIds 提及名单
  /// @return 截到存储上限的摘录
  private ExcerptText excerptOf(String body, List<Long> mentionedUserIds) {
    return plainText
        .toExcerpt(body, Set.copyOf(mentionedUserIds))
        .cut(ExcerptRules.MAX_STORED_UNITS);
  }

  /// 领域异常原样传播，其余运行时异常包成 500。
  ///
  /// @param action 写提醒的动作
  private static void guard(Runnable action) {
    try {
      action.run();
    } catch (DomainException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new NotificationWriteFailedException(ex);
    }
  }
}
