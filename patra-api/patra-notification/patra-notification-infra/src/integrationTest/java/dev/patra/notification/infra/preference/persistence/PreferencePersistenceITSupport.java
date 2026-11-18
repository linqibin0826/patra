package dev.patra.notification.infra.preference.persistence;

import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// preference 持久化集成测试的公共底座：真实 PG，每例前清空偏好表；写操作包在事务里，模拟 app 层。
@SpringBootTest(classes = PreferencePersistenceITBootstrap.class)
@ContextConfiguration(initializers = NotificationITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
abstract class PreferencePersistenceITSupport {

  /// 合法的令牌原文（43 个 base64url 字符）。
  static final String TOKEN_A = "A".repeat(43);

  /// 另一个合法的令牌原文。
  static final String TOKEN_B = "B".repeat(43);

  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected TransactionTemplate transactions;

  /// 每例从空表开始。
  @BeforeEach
  void truncatePreferenceTables() {
    jdbcTemplate.update("TRUNCATE ntf_email_preference");
  }

  /// 直接插一行。
  ///
  /// @param userId 用户 ID
  /// @param reply 「回复我的」档位
  /// @param mention 「@ 我的」档位
  /// @param token 令牌原文
  protected void insertRow(long userId, String reply, String mention, String token) {
    jdbcTemplate.update(
        "INSERT INTO ntf_email_preference (id, user_id, reply_mode, mention_mode,"
            + " unsubscribe_token) VALUES (?, ?, ?, ?, ?)",
        userId,
        userId,
        reply,
        mention,
        token);
  }

  /// 表行数。
  ///
  /// @return 行数
  protected long rows() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM ntf_email_preference", Long.class);
  }
}
