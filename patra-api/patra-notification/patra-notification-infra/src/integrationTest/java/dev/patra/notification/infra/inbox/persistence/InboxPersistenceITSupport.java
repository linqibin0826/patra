package dev.patra.notification.infra.inbox.persistence;

import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.inbox.model.vo.NotificationDraft;
import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// inbox 持久化集成测试的公共底座：真实 PG（16 个哈希分区），每例前清空 inbox 的三张表。
@SpringBootTest(classes = InboxPersistenceITBootstrap.class)
@ContextConfiguration(initializers = NotificationITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
abstract class InboxPersistenceITSupport {

  /// 测试里的「现在」。
  static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

  @Autowired protected JdbcTemplate jdbcTemplate;

  /// 每例从空表开始。
  @BeforeEach
  void truncateInboxTables() {
    jdbcTemplate.update(
        "TRUNCATE inbox.helpful_actor, inbox.notification, inbox.comment, inbox.subject_tombstone");
  }

  /// 一条回复或 @ 提醒的草稿：触发人 9、对象 40、排序时间与写入时刻都是 {@link #NOW}。
  ///
  /// @param recipientId 收件人
  /// @param type 类型
  /// @param commentId 评论
  /// @return 草稿
  static NotificationDraft draft(long recipientId, NotificationType type, long commentId) {
    return draft(recipientId, type, commentId, NOW);
  }

  /// 指定排序时间的草稿。
  ///
  /// @param recipientId 收件人
  /// @param type 类型
  /// @param commentId 评论
  /// @param occurredAt 排序时间
  /// @return 草稿
  static NotificationDraft draft(
      long recipientId, NotificationType type, long commentId, Instant occurredAt) {
    return NotificationDraft.builder()
        .recipientId(recipientId)
        .type(type)
        .actorId(9L)
        .actorAnonymous(false)
        .subjectId(40L)
        .commentId(commentId)
        .occurredAt(occurredAt)
        .createdAt(NOW)
        .build();
  }

  /// 提醒表的行数。
  ///
  /// @return 行数
  protected long notificationRows() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM inbox.notification", Long.class);
  }
}
