package dev.patra.analytics.infra.insight.persistence;

import dev.patra.analytics.infra.config.AnalyticsITPostgreSQLContainerInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// insight 持久化集成测试的公共底座：真实 PG，每例前清空三张表；写操作包在事务里，模拟 app 层。
@SpringBootTest(classes = InsightPersistenceITBootstrap.class)
@ContextConfiguration(initializers = AnalyticsITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
abstract class InsightPersistenceITSupport {

  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected TransactionTemplate transactions;

  /// 每例从空表开始。
  @BeforeEach
  void truncateInsightTables() {
    jdbcTemplate.update("TRUNCATE insight.hot_reading, insight.co_reading, insight.snapshot_run");
  }
}
