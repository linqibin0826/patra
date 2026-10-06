package dev.linqibin.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.password.CommonPasswordPort;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/// IdentityConfiguration 单元测试。
@DisplayName("IdentityConfiguration 单元测试")
class IdentityConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(IdentityConfiguration.class)
          .withBean(CommonPasswordPort.class, () -> key -> false);

  @Test
  @DisplayName("不配置时用默认值：5 次、15 分钟窗口、锁 15 分钟、在途 30 秒")
  void should_use_defaults() {
    runner.run(
        context -> {
          assertThat(context.getBean(LoginThrottlePolicy.class))
              .isEqualTo(
                  LoginThrottlePolicy.of(
                      5, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofSeconds(30)));
          assertThat(context).hasSingleBean(PasswordHashingPort.class);
          assertThat(context).hasSingleBean(PasswordPolicy.class);
        });
  }

  @Test
  @DisplayName("配置项能覆盖默认值")
  void should_bind_overrides() {
    runner
        .withPropertyValues(
            "patra.identity.login-throttle.max-failures=3",
            "patra.identity.login-throttle.lock-duration=2s")
        .run(
            context -> {
              LoginThrottlePolicy policy = context.getBean(LoginThrottlePolicy.class);
              assertThat(policy.maxFailures()).isEqualTo(3);
              assertThat(policy.lockDuration()).isEqualTo(Duration.ofSeconds(2));
            });
  }

  @Test
  @DisplayName("非法配置让应用启动失败")
  void should_fail_on_invalid_configuration() {
    runner
        .withPropertyValues("patra.identity.login-throttle.max-failures=0")
        .run(context -> assertThat(context).hasFailed());
  }
}
