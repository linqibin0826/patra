package dev.patra.tool.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.patra.tool.batch.schema.BatchSchemaInitializer;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BatchSchemaAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(BatchSchemaAutoConfiguration.class))
          .withBean("dataSource", DataSource.class, () -> mock(DataSource.class));

  @Test
  void should_register_schema_initializer_by_default() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(BatchSchemaInitializer.class));
  }

  @Test
  void should_skip_schema_initializer_when_switched_off() {
    runner
        .withPropertyValues("patra.batch.schema.initialize=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(BatchSchemaInitializer.class));
  }

  @Test
  void should_skip_schema_initializer_when_module_disabled() {
    runner
        .withPropertyValues("patra.batch.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(BatchSchemaInitializer.class));
  }
}
