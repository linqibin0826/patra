package dev.patra.identity.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.tool.test.schema.SchemaStandard;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// identity 库的结构检查：Flyway 跑完后，真实表结构符合库表规范、与 `db/tables.yml` 一一对应。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("identity 库表结构检查")
class IdentitySchemaStandardIT {

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("迁移后的表结构符合库表规范")
  void should_meet_database_standard() {
    assertThat(SchemaStandard.inspect(dataSource, "db/tables.yml").violations()).isEmpty();
  }
}
