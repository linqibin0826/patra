package dev.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.patra.identity.domain.account.policy.LoginThrottlePolicy;
import dev.patra.identity.domain.account.port.geolocation.IpGeolocationPort;
import dev.patra.identity.domain.account.port.password.CommonPasswordPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.useragent.UserAgentParsingPort;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.health.RefreshScopeHealthIndicator;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;

/// identity 配置的在线刷新（PAP-78）：改环境后发布 `EnvironmentChangeEvent`，即 Nacos 推送后
/// Spring Cloud 的 `ContextRefresher` 发出的事件，验证服务不重启就用上新值。
@DisplayName("identity 配置在线刷新集成测试")
class IdentityConfigRefreshIT {

  private static final String IDENTITY_LOGGER = "dev.patra.identity";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  RefreshAutoConfiguration.class,
                  ConfigurationPropertiesRebinderAutoConfiguration.class,
                  RefreshEndpointAutoConfiguration.class))
          .withUserConfiguration(IdentityConfiguration.class)
          .withBean(CommonPasswordPort.class, () -> key -> false)
          .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
          .withBean(Clock.class, Clock::systemUTC)
          .withBean(UserLoginRecordRepository.class, () -> mock(UserLoginRecordRepository.class))
          .withBean(SessionStorePort.class, () -> mock(SessionStorePort.class))
          .withBean(CurrentUserPort.class, () -> mock(CurrentUserPort.class))
          .withBean(EmailTokenRepository.class, () -> mock(EmailTokenRepository.class))
          .withBean(UserAgentParsingPort.class, () -> mock(UserAgentParsingPort.class))
          .withBean(IpGeolocationPort.class, () -> mock(IpGeolocationPort.class))
          .withPropertyValues(
              "patra.identity.session.lifetime.user.web.idle=30d",
              "patra.identity.session.lifetime.user.web.absolute=180d");

  @Test
  @DisplayName("登录失败上限改成 2：不重启，下一次取到的限制参数就是 2")
  void should_apply_new_login_throttle_without_restart() {
    runner.run(
        context -> {
          assertThat(throttlePolicy(context).maxFailures()).isEqualTo(5);

          change(context, Map.of("patra.identity.login-throttle.max-failures", "2"));

          assertThat(throttlePolicy(context).maxFailures()).isEqualTo(2);
        });
  }

  @Test
  @DisplayName("失败上限改成 0：刷新报错，健康检查转为 DOWN")
  void should_report_down_on_invalid_login_throttle() {
    runner.run(
        context -> {
          RefreshScopeHealthIndicator health = context.getBean(RefreshScopeHealthIndicator.class);
          assertThat(health.health().getStatus()).isEqualTo(Status.UP);

          assertThatThrownBy(
                  () -> change(context, Map.of("patra.identity.login-throttle.max-failures", "0")))
              .rootCause()
              .hasMessageContaining("maxFailures");

          assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        });
  }

  @Test
  @DisplayName("日志级别从 INFO 改成 DEBUG：不重启即生效")
  void should_apply_logging_level_without_restart() {
    // 日志级别是进程级状态，起始值取决于前面的测试和运行环境：先显式推一次 INFO，结束时还原
    LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());
    LoggerConfiguration before = logging.getLoggerConfiguration(IDENTITY_LOGGER);
    LogLevel original = before == null ? null : before.getConfiguredLevel();
    try {
      runner.run(
          context -> {
            change(context, Map.of("logging.level." + IDENTITY_LOGGER, "INFO"));
            assertThat(LoggerFactory.getLogger(IDENTITY_LOGGER).isDebugEnabled()).isFalse();

            change(context, Map.of("logging.level." + IDENTITY_LOGGER, "DEBUG"));

            assertThat(LoggerFactory.getLogger(IDENTITY_LOGGER).isDebugEnabled()).isTrue();
          });
    } finally {
      logging.setLogLevel(IDENTITY_LOGGER, original);
    }
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

  /// 当前的登录失败限制参数。
  ///
  /// @param context 应用上下文
  /// @return 参数
  private static LoginThrottlePolicy throttlePolicy(AssertableApplicationContext context) {
    ResolvableType type =
        ResolvableType.forClassWithGenerics(Supplier.class, LoginThrottlePolicy.class);
    Supplier<?> supplier = (Supplier<?>) context.getBeanProvider(type).getObject();
    return (LoginThrottlePolicy) supplier.get();
  }
}
