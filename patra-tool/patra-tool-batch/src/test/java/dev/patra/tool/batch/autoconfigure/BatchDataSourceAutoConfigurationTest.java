package dev.patra.tool.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BatchDataSourceAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(BatchDataSourceAutoConfiguration.class));

  @Test
  void should_not_register_batch_datasource_without_url() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean("batchDataSource"));
  }

  @Test
  void should_not_register_batch_datasource_when_module_disabled() {
    runner
        .withPropertyValues(
            "patra.batch.enabled=false", "patra.batch.datasource.url=jdbc:postgresql://x/y")
        .run(ctx -> assertThat(ctx).doesNotHaveBean("batchDataSource"));
  }
}
