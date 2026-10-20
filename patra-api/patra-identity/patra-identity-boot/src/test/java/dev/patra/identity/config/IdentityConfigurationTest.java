package dev.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.patra.identity.domain.policy.DisplayNamePolicy;
import dev.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.patra.identity.domain.policy.PasswordPolicy;
import dev.patra.identity.domain.policy.SessionLifetime;
import dev.patra.identity.domain.policy.SessionLifetimePolicy;
import dev.patra.identity.domain.policy.UsernameChangePolicy;
import dev.patra.identity.domain.policy.UsernamePolicy;
import dev.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.port.password.CommonPasswordPort;
import dev.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.port.session.SessionStorePort;
import dev.patra.identity.domain.service.SessionIssuer;
import dev.patra.identity.session.RedisSessionStore;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ResolvableType;
import org.springframework.data.redis.core.StringRedisTemplate;

/// IdentityConfiguration 单元测试。
///
/// 每次启动上下文都会构造密码哈希适配器，构造时算一次 Argon2（19 MiB、2 轮）。全类第一条用例赶上 JIT
/// 未预热，CI 两核机器又在并行编译和起容器时，两次冷启动的 Argon2 会超过单元测试默认的 15 秒，
/// 所以本类放宽到 60 秒；这里验证的是装配，不是哈希速度。
@DisplayName("IdentityConfiguration 单元测试")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
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
          assertThat(throttlePolicy(context))
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
              LoginThrottlePolicy throttle = throttlePolicy(context);
              assertThat(throttle.maxFailures()).isEqualTo(3);
              assertThat(throttle.lockDuration()).isEqualTo(Duration.ofSeconds(2));
              SessionLifetimePolicy session = context.getBean(SessionLifetimePolicy.class);
              assertThat(session.maxSessionsPerUser()).isEqualTo(3);
              assertThat(session.lifetimeFor(ClientType.WEB).idle()).isEqualTo(Duration.ofDays(7));
            });
  }

  @Test
  @DisplayName("限流配置对象被重新绑定后，下一次取到的参数就是新值（配置刷新走的就是这条路）")
  void should_follow_throttle_property_changes() {
    runner.run(
        context -> {
          LoginThrottleProperties properties = context.getBean(LoginThrottleProperties.class);
          properties.setMaxFailures(3);
          properties.setLockDuration(Duration.ofMinutes(1));

          LoginThrottlePolicy throttle = throttlePolicy(context);
          assertThat(throttle.maxFailures()).isEqualTo(3);
          assertThat(throttle.lockDuration()).isEqualTo(Duration.ofMinutes(1));
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
  @DisplayName("用户名规则、昵称规则、改名策略都是 Bean；改名策略默认 30 天冷却、90 天保留")
  void should_provide_username_policies_with_defaults() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(UsernamePolicy.class);
          assertThat(context).hasSingleBean(DisplayNamePolicy.class);
          UsernameChangePolicy policy = context.getBean(UsernameChangePolicy.class);
          assertThat(policy.cooldown()).isEqualTo(Duration.ofDays(30));
          assertThat(policy.reservationPeriod()).isEqualTo(Duration.ofDays(90));
        });
  }

  @Test
  @DisplayName("改名策略能覆盖；冷却或保留期不是正数时启动失败")
  void should_bind_and_validate_username_change_policy() {
    runner
        .withPropertyValues(
            "patra.identity.username.change-cooldown=1m",
            "patra.identity.username.reservation-period=2m")
        .run(
            context -> {
              UsernameChangePolicy policy = context.getBean(UsernameChangePolicy.class);
              assertThat(policy.cooldown()).isEqualTo(Duration.ofMinutes(1));
              assertThat(policy.reservationPeriod()).isEqualTo(Duration.ofMinutes(2));
            });
    runner
        .withPropertyValues("patra.identity.username.change-cooldown=0s")
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
