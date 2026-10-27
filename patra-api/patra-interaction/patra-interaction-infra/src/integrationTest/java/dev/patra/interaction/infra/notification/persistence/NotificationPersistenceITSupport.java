package dev.patra.interaction.infra.notification.persistence;

import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.model.vo.NotificationDraft;
import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// notification 持久化集成测试的底座：真实 PG，每例清空提醒表。
@SpringBootTest(classes = NotificationPersistenceITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
abstract class NotificationPersistenceITSupport {

  /// 收件人 A。
  protected static final long A = 1001L;

  /// 收件人 B。
  protected static final long B = 1002L;

  /// 触发人。
  protected static final long ACTOR = 2001L;

  /// 时刻。
  protected static final Instant T0 = Instant.parse("2026-10-27T01:00:00Z");

  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected NotificationRepository notifications;

  /// 每例从空表开始。
  @BeforeEach
  void truncateNotifications() {
    jdbcTemplate.update("TRUNCATE notification.notification");
  }

  /// 一条提醒：文献 100 下、摘录「正文」、生成于 T0。
  ///
  /// @param recipient 收件人
  /// @param type 类型
  /// @param commentId 评论
  /// @return 待写入的提醒
  protected static NotificationDraft draft(long recipient, NotificationType type, long commentId) {
    return draft(recipient, type, commentId, T0);
  }

  /// 一条提醒，指定生成时刻。
  ///
  /// @param recipient 收件人
  /// @param type 类型
  /// @param commentId 评论
  /// @param createdAt 生成时刻
  /// @return 待写入的提醒
  protected static NotificationDraft draft(
      long recipient, NotificationType type, long commentId, Instant createdAt) {
    return NotificationDraft.builder()
        .recipientId(recipient)
        .type(type)
        .actorId(ACTOR)
        .actorAnonymous(false)
        .subjectId(SubjectId.of(100L))
        .commentId(commentId)
        .excerpt(ExcerptText.builder().literal("正文").build())
        .createdAt(createdAt)
        .build();
  }

  /// 某收件人的行数。
  ///
  /// @param recipient 收件人
  /// @return 行数
  protected long countOf(long recipient) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM notification.notification WHERE recipient_id = ?",
        Long.class,
        recipient);
  }
}
