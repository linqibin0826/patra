package dev.patra.interaction.infra.reaction.persistence;

import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// reaction 持久化集成测试的公共底座：真实 PG，每例前清空三张表；写操作包在事务里，模拟 app 层。
@SpringBootTest(classes = ReactionPersistenceITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
abstract class ReactionPersistenceITSupport {

  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected TransactionTemplate transactions;

  /// 每例从空表开始。
  @BeforeEach
  void truncateReactionTables() {
    jdbcTemplate.update("TRUNCATE reaction.helpful, reaction.target_stats, reaction.user_stats");
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
