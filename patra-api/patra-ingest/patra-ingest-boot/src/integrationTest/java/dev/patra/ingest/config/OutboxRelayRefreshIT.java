package dev.patra.ingest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import dev.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import dev.patra.ingest.app.usecase.relay.planner.RelayPlanBuilder;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.health.RefreshScopeHealthIndicator;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

/// Outbox 中继参数的在线刷新（PAP-78）：改环境后发布 `EnvironmentChangeEvent`，即 Nacos 推送后
/// Spring Cloud 的 `ContextRefresher` 发出的事件，验证下一次构建中继计划就用上新值。
@DisplayName("Outbox 中继参数在线刷新集成测试")
class OutboxRelayRefreshIT {

  private static final OutboxRelayCommand DEFAULT_INSTRUCTION =
      new OutboxRelayCommand(null, null, null, null, null, null, null);

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  RefreshAutoConfiguration.class,
                  ConfigurationPropertiesRebinderAutoConfiguration.class,
                  RefreshEndpointAutoConfiguration.class))
          .withBean(Clock.class, Clock::systemUTC)
          .withUserConfiguration(RelayConfiguration.class);

  @Test
  @DisplayName("中继批大小改成 50：不重启，下一次构建的中继计划就用 50")
  void should_apply_new_batch_size_without_restart() {
    runner.run(
        context -> {
          RelayPlanBuilder builder = context.getBean(RelayPlanBuilder.class);
          assertThat(builder.build(DEFAULT_INSTRUCTION).batchSize()).isEqualTo(200);

          change(context, Map.of("patra.ingest.outbox-relay.batch-size", "50"));

          assertThat(builder.build(DEFAULT_INSTRUCTION).batchSize()).isEqualTo(50);
        });
  }

  @Test
  @DisplayName("中继批大小改成 0：刷新报错，健康检查转为 DOWN")
  void should_report_down_on_invalid_batch_size() {
    runner.run(
        context -> {
          RefreshScopeHealthIndicator health = context.getBean(RefreshScopeHealthIndicator.class);
          assertThat(health.health().getStatus()).isEqualTo(Status.UP);

          assertThatThrownBy(
                  () -> change(context, Map.of("patra.ingest.outbox-relay.batch-size", "0")))
              .rootCause()
              .hasMessageContaining("batchSize");

          assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        });
  }

  /// 模拟一次 Nacos 推送：把新值放到最高优先级，再发布配置变更事件。
  ///
  /// @param context 应用上下文
  /// @param changes 变更的键值
  private static void change(AssertableApplicationContext context, Map<String, Object> changes) {
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(new MapPropertySource("nacos-change-" + System.nanoTime(), changes));
    // ConfigurationPropertiesRebinder 只认事件源是它所在的上下文，测试包装过的上下文不行
    context.publishEvent(
        new EnvironmentChangeEvent(context.getSourceApplicationContext(), changes.keySet()));
  }

  /// 中继参数和读取它的中继计划构建器，按应用里的方式绑定配置。
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(OutboxRelayProperties.class)
  @Import(RelayPlanBuilder.class)
  static class RelayConfiguration {}
}
