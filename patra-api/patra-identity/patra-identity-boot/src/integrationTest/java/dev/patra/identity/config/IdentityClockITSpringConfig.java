package dev.patra.identity.config;

import dev.patra.tool.test.clock.MutableClock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/// 给端到端测试一个可拨动的时钟：启动器的 `Clock` 是 `@ConditionalOnMissingBean`，这里声明了它就让位。
/// Redis 的 TTL 走真实时间，拨时钟只影响 identity 自己算的时间（改名冷却、保留期、注销到期）。
@TestConfiguration(proxyBeanMethods = false)
public class IdentityClockITSpringConfig {

  /// 起始时刻。
  public static final Instant START = Instant.parse("2026-10-20T08:00:00Z");

  /// 从固定时刻开始的时钟。
  ///
  /// @return 时钟
  @Bean
  @Primary
  public MutableClock clock() {
    return MutableClock.startingAt(START);
  }
}
