package dev.linqibin.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.policy.SessionLifetime;
import dev.linqibin.patra.identity.domain.policy.SessionLifetimePolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.password.CommonPasswordPort;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
import dev.linqibin.patra.identity.domain.service.SessionIssuer;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/// IdentityConfiguration 单元测试。
@DisplayName("IdentityConfiguration 单元测试")
class IdentityConfigurationTest {

  private static final String[] WEB_LIFETIME = {
    "patra.identity.session.lifetime.user.web.idle=30d",
    "patra.identity.session.lifetime.user.web.absolute=180d"
  };

  private final ApplicationContextRunner runner = bare().withPropertyValues(WEB_LIFETIME);

  /// 只有 IdentityConfiguration 和它依赖的 Bean，没有会话有效期的配置。
  ///
  /// @return 运行器
  private static ApplicationContextRunner bare() {
    return new ApplicationContextRunner()
        .withUserConfiguration(IdentityConfiguration.class)
        .withBean(CommonPasswordPort.class, () -> key -> false)
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(UserLoginRecordRepository.class, () -> mock(UserLoginRecordRepository.class))
        .withBean(SessionStorePort.class, () -> mock(SessionStorePort.class))
        .withBean(CurrentUserPort.class, () -> mock(CurrentUserPort.class));
  }

  @Test
  @DisplayName("不配置时用默认值：5 次、15 分钟窗口、锁 15 分钟、在途 30 秒；每用户 10 条会话")
  void should_use_defaults() {
    runner.run(
        context -> {
          assertThat(context.getBean(LoginThrottlePolicy.class))
              .isEqualTo(
                  LoginThrottlePolicy.of(
                      5, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofSeconds(30)));
          assertThat(context).hasSingleBean(PasswordHashingPort.class);
          assertThat(context).hasSingleBean(PasswordPolicy.class);
          SessionLifetimePolicy policy = context.getBean(SessionLifetimePolicy.class);
          assertThat(policy.maxSessionsPerUser()).isEqualTo(10);
          assertThat(policy.lifetimeFor(ClientType.WEB))
              .isEqualTo(SessionLifetime.of(Duration.ofDays(30), Duration.ofDays(180)));
          assertThat(context).hasSingleBean(RedisSessionStore.class);
          assertThat(context).hasSingleBean(SessionIssuer.class);
        });
  }

  @Test
  @DisplayName("配置项能覆盖默认值")
  void should_bind_overrides() {
    runner
        .withPropertyValues(
            "patra.identity.login-throttle.max-failures=3",
            "patra.identity.login-throttle.lock-duration=2s",
            "patra.identity.session.max-sessions-per-user=3",
            "patra.identity.session.lifetime.user.web.idle=7d")
        .run(
            context -> {
              LoginThrottlePolicy throttle = context.getBean(LoginThrottlePolicy.class);
              assertThat(throttle.maxFailures()).isEqualTo(3);
              assertThat(throttle.lockDuration()).isEqualTo(Duration.ofSeconds(2));
              SessionLifetimePolicy session = context.getBean(SessionLifetimePolicy.class);
              assertThat(session.maxSessionsPerUser()).isEqualTo(3);
              assertThat(session.lifetimeFor(ClientType.WEB).idle()).isEqualTo(Duration.ofDays(7));
            });
  }

  @Test
  @DisplayName("非法的限流配置让应用启动失败")
  void should_fail_on_invalid_throttle_configuration() {
    runner
        .withPropertyValues("patra.identity.login-throttle.max-failures=0")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("缺 user.web 那一行：启动失败")
  void should_fail_without_web_lifetime() {
    bare().run(context -> assertThat(context).hasFailed());
    bare()
        .withPropertyValues("patra.identity.session.lifetime.user.web.idle=30d")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("不认识的账号类型或客户端类型：启动失败")
  void should_fail_on_unknown_keys() {
    runner
        .withPropertyValues(
            "patra.identity.session.lifetime.user.app.idle=1d",
            "patra.identity.session.lifetime.user.app.absolute=2d")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues(
            "patra.identity.session.lifetime.staff.web.idle=1d",
            "patra.identity.session.lifetime.staff.web.absolute=2d")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("不活跃过期长于绝对过期、上限小于 1：启动失败")
  void should_fail_on_invalid_session_values() {
    runner
        .withPropertyValues("patra.identity.session.lifetime.user.web.idle=200d")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("patra.identity.session.max-sessions-per-user=0")
        .run(context -> assertThat(context).hasFailed());
  }
}
