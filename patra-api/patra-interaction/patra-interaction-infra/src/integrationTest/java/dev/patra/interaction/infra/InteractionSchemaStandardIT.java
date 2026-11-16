package dev.patra.interaction.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import dev.patra.tool.test.schema.SchemaStandard;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// interaction 库的结构检查：Flyway 跑完后，真实表结构符合库表规范、与 `db/tables.yml` 一一对应。
@SpringBootTest(classes = InteractionITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("interaction 库表结构检查")
class InteractionSchemaStandardIT {

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("迁移后的表结构符合库表规范")
  void should_meet_database_standard() {
    assertThat(SchemaStandard.inspect(dataSource, "db/tables.yml").violations()).isEmpty();
  }
}
