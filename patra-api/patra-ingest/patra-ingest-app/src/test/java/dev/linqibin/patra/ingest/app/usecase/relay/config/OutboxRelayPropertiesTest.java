package dev.linqibin.patra.ingest.app.usecase.relay.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/// OutboxRelayProperties 单元测试：中继参数可在线刷新（PAP-78），不合法的值在绑定时就被拒绝。
@DisplayName("OutboxRelayProperties 单元测试")
class OutboxRelayPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "patra.ingest.outbox-relay.batch-size=0",
        "patra.ingest.outbox-relay.max-attempts=0",
        "patra.ingest.outbox-relay.lease-duration=0s",
        "patra.ingest.outbox-relay.initial-backoff=0s",
        "patra.ingest.outbox-relay.max-backoff=0s",
        "patra.ingest.outbox-relay.backoff-multiplier=0.5"
      })
  @DisplayName("不合法的中继参数让绑定失败")
  void should_reject_invalid_values(String property) {
    runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
  }

  /// 只注册中继参数。
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(OutboxRelayProperties.class)
  static class PropertiesConfiguration {}
}
