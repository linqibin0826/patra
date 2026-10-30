package dev.patra.tool.events.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.patra.tool.core.event.ProcessedEventPort;
import dev.patra.tool.events.consumer.JdbcProcessedEventAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;

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
  @DisplayName("没有 JdbcClient 时不注册")
  void should_back_off_without_jdbc_client() {
    runner.run(context -> assertThat(context).doesNotHaveBean(ProcessedEventPort.class));
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
