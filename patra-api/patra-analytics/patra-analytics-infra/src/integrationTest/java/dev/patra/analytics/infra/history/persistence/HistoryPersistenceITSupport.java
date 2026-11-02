package dev.patra.analytics.infra.history.persistence;

import dev.patra.analytics.infra.config.AnalyticsITPostgreSQLContainerInitializer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// history 持久化集成测试的公共底座：真实 PG，每例前清空三张表；写操作包在事务里，模拟 app 层。
@SpringBootTest(classes = HistoryPersistenceITBootstrap.class)
@ContextConfiguration(initializers = AnalyticsITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
abstract class HistoryPersistenceITSupport {

  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected TransactionTemplate transactions;

  /// 每例从空表开始。
  @BeforeEach
  void truncateHistoryTables() {
    jdbcTemplate.update(
        "TRUNCATE history.reading_history, history.recording_setting, history.deleted_entry");
  }

  /// 直接插一行历史。
  ///
  /// @param userId 用户
  /// @param articleId 文献
  /// @param at 最近打开时间
  /// @param fulltext 是否读过全文
  protected void insertHistory(long userId, long articleId, Instant at, boolean fulltext) {
    jdbcTemplate.update(
        "INSERT INTO history.reading_history (user_id, article_id, last_read_at, fulltext_read)"
            + " VALUES (?, ?, ?, ?)",
        userId,
        articleId,
        at.atOffset(ZoneOffset.UTC),
        fulltext);
  }

  /// 直接插一条删除记录。
  ///
  /// @param userId 用户
  /// @param articleId 文献
  /// @param at 删除时刻
  protected void insertDeletion(long userId, long articleId, Instant at) {
    jdbcTemplate.update(
        "INSERT INTO history.deleted_entry (user_id, article_id, deleted_at) VALUES (?, ?, ?)",
        userId,
        articleId,
        at.atOffset(ZoneOffset.UTC));
  }

  /// 直接插一行设置。
  ///
  /// @param userId 用户
  /// @param enabled 开关
  protected void insertSetting(long userId, boolean enabled) {
    jdbcTemplate.update(
        "INSERT INTO history.recording_setting (user_id, recording_enabled, updated_at)"
            + " VALUES (?, ?, now())",
        userId,
        enabled);
  }

  /// 一行历史的最近打开时间。
  ///
  /// @param userId 用户
  /// @param articleId 文献
  /// @return 时间
  protected Instant lastReadAt(long userId, long articleId) {
    return jdbcTemplate
        .queryForObject(
            "SELECT last_read_at FROM history.reading_history WHERE user_id = ? AND article_id = ?",
            OffsetDateTime.class,
            userId,
            articleId)
        .toInstant();
  }

  /// 表行数。
  ///
  /// @param sql 计数 SQL
  /// @param args 参数
  /// @return 行数
  protected long count(String sql, Object... args) {
    return jdbcTemplate.queryForObject(sql, Long.class, args);
  }
}
