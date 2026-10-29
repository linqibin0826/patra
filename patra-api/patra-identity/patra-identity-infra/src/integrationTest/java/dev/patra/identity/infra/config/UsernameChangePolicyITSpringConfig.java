package dev.patra.identity.infra.config;

import dev.patra.identity.domain.account.policy.UsernameChangePolicy;
import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/// 切片测试里给读适配器一个改名策略：boot 的 `IdentityConfiguration` 不在 `@DataJpaTest` 的上下文里。
@TestConfiguration(proxyBeanMethods = false)
public class UsernameChangePolicyITSpringConfig {

  /// 30 天冷却、90 天保留，和生产默认值一样。
  ///
  /// @return 策略
  @Bean
  public UsernameChangePolicy usernameChangePolicy() {
    return UsernameChangePolicy.of(Duration.ofDays(30), Duration.ofDays(90));
  }
}
