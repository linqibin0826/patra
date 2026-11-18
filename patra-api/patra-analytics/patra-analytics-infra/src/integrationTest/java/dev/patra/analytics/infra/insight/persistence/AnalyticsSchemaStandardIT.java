package dev.patra.analytics.infra.insight.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.infra.config.AnalyticsITPostgreSQLContainerInitializer;
import dev.patra.tool.test.schema.SchemaStandard;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// analytics 的 PostgreSQL 库结构检查：Flyway 跑完后，真实表结构符合库表规范、与 `db/tables.yml` 一一对应。
///
/// 借用 insight 持久化测试的启动类：它装配了数据源与 Flyway，检查本身不依赖哪个子域。
@SpringBootTest(classes = InsightPersistenceITBootstrap.class)
@ContextConfiguration(initializers = AnalyticsITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("analytics 库表结构检查")
class AnalyticsSchemaStandardIT {

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("迁移后的表结构符合库表规范")
  void should_meet_database_standard() {
    assertThat(SchemaStandard.inspect(dataSource, "db/tables.yml").violations()).isEmpty();
  }
}
