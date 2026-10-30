package dev.patra.notification.infra.preference.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/// preference 持久化集成测试的启动类：只扫描本包（两个适配器），给一个固定时钟。
@SpringBootApplication
class PreferencePersistenceITBootstrap {

  /// 测试里的「现在」。
  static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

  /// 固定时钟：读适配器建行时用它写时间。
  ///
  /// @return 时钟
  @Bean
  Clock clock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }
}
