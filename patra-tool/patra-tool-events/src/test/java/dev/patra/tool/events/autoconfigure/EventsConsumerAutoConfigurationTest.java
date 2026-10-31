package dev.patra.tool.events.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.tool.core.event.ProcessedEventPort;
import dev.patra.tool.events.consumer.JdbcProcessedEventAdapter;
import dev.patra.tool.events.consumer.ProcessedEventPurgeJob;
import dev.patra.tool.events.consumer.ProcessedEventPurger;
import dev.patra.tool.test.clock.MutableClock;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

@DisplayName("EventsConsumerAutoConfiguration 契约")
class EventsConsumerAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(EventsConsumerAutoConfiguration.class));

  @Test
  @DisplayName("有 JdbcClient 时注册 JDBC 去重端口")
  void should_register_processed_event_port_with_jdbc_client() {
    runner
        .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
        .run(
            context ->
                assertThat(context.getBean(ProcessedEventPort.class))
                    .isInstanceOf(JdbcProcessedEventAdapter.class));
  }

  @Test
  @DisplayName("有 JdbcClient 时注册已处理事件的清理器与 XXL-Job 任务，保留期默认 30 天")
  void should_register_purger_and_job_with_default_retention() {
    runner
        .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(ProcessedEventPurger.class);
              assertThat(context).hasSingleBean(ProcessedEventPurgeJob.class);
              assertThat(context.getBean(EventsProperties.class).consumer().processedRetention())
                  .isEqualTo(Duration.ofDays(30));
            });
  }

  @Test
  @DisplayName("没有 XXL-Job 时只注册清理器，不注册任务")
  void should_skip_purge_job_without_xxl_job() {
    runner
        .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
        .withClassLoader(new FilteredClassLoader(XxlJob.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(ProcessedEventPurger.class);
              assertThat(context).doesNotHaveBean(ProcessedEventPurgeJob.class);
            });
  }

  @Test
  @DisplayName("保留期不写单位时按天解析")
  void should_bind_retention_without_unit_as_days() {
    runner
        .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
        .withPropertyValues("patra.events.consumer.processed-retention=7")
        .run(
            context ->
                assertThat(context.getBean(EventsProperties.class).consumer().processedRetention())
                    .isEqualTo(Duration.ofDays(7)));
  }

  @Test
  @DisplayName("保留期为 0 或负数时启动失败：否则每天把去重表删光")
  void should_fail_to_start_with_non_positive_retention() {
    runner
        .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
        .withPropertyValues("patra.events.consumer.processed-retention=0d")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("patra.events.consumer.processed-retention"));
  }

  @Test
  @DisplayName("配置的保留期传到清理器：删的是「现在减配置值」之前的行")
  void should_pass_configured_retention_to_purger() {
    Instant now = Instant.parse("2026-10-31T00:00:00Z");
    JdbcClient jdbcClient = mock(JdbcClient.class);
    StatementSpec statement = mock(StatementSpec.class);
    when(jdbcClient.sql(anyString())).thenReturn(statement);
    when(statement.param(any())).thenReturn(statement);
    when(statement.update()).thenReturn(0);
    runner
        .withBean(JdbcClient.class, () -> jdbcClient)
        .withBean(Clock.class, () -> MutableClock.startingAt(now))
        .withPropertyValues("patra.events.consumer.processed-retention=7d")
        .run(
            context -> {
              context.getBean(ProcessedEventPurger.class).purge();
              verify(statement).param(Timestamp.from(now.minus(Duration.ofDays(7))));
            });
  }

  @Test
  @DisplayName("没有 JdbcClient 时不注册")
  void should_back_off_without_jdbc_client() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(ProcessedEventPort.class);
          assertThat(context).doesNotHaveBean(ProcessedEventPurger.class);
          assertThat(context).doesNotHaveBean(ProcessedEventPurgeJob.class);
        });
  }

  @Test
  @DisplayName("服务自己定义了去重端口时让位")
  void should_back_off_when_service_defines_its_own_port() {
    ProcessedEventPort own = (consumer, eventId) -> true;
    runner
        .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
        .withBean(ProcessedEventPort.class, () -> own)
        .run(context -> assertThat(context.getBean(ProcessedEventPort.class)).isSameAs(own));
  }
}
