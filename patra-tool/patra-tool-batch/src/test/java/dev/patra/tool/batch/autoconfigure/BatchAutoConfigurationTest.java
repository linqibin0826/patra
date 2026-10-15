package dev.patra.tool.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.tool.batch.core.JobOperatorHelper;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BatchAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(JacksonAutoConfiguration.class, BatchAutoConfiguration.class))
          .withBean(
              "dataSource", DataSource.class, BatchAutoConfigurationTest::postgresLikeDataSource);

  /// 只回答元数据的假数据源：JobRepository 装配时要问数据库产品名，不真连库。
  private static DataSource postgresLikeDataSource() {
    DataSource dataSource = mock(DataSource.class, RETURNS_DEEP_STUBS);
    try {
      when(dataSource.getConnection().getMetaData().getDatabaseProductName())
          .thenReturn("PostgreSQL");
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
    return dataSource;
  }

  @Test
  void should_bind_defaults_and_register_job_operator_helper() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(JobOperatorHelper.class);
          BatchProperties props = ctx.getBean(BatchProperties.class);
          assertThat(props.tablePrefix()).isEqualTo("BATCH_");
          assertThat(props.chunk().defaultSize()).isEqualTo(5000);
          assertThat(props.datasource().configured()).isFalse();
          assertThat(props.importLimit().hasLimit()).isFalse();
        });
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("patra.batch.enabled=false")
        .run(
            ctx ->
                assertThat(ctx)
                    .doesNotHaveBean(BatchProperties.class)
                    .doesNotHaveBean(JobOperatorHelper.class));
  }

  @Test
  void should_report_datasource_configured_when_url_set() {
    runner
        .withPropertyValues("patra.batch.datasource.url=jdbc:postgresql://x/y")
        .run(
            ctx ->
                assertThat(ctx.getBean(BatchProperties.class).datasource().configured()).isTrue());
  }
}
