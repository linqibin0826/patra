package dev.linqibin.starter.jpa.autoconfig;

import dev.linqibin.starter.jpa.audit.CurrentAuditorProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/// JPA 审计配置类。
///
/// **功能说明**：
///
/// - 启用 Spring Data JPA 审计功能（`@EnableJpaAuditing`）
/// - 配置 `AuditorAware` 用于填充 `@CreatedBy` 和 `@LastModifiedBy` 字段
/// - 配置 `DateTimeProvider` 用于填充 `@CreatedDate` 和 `@LastModifiedDate` 字段
///
/// **扩展点**：
///
/// - 提供一个 {@link CurrentAuditorProvider} Bean 来告诉审计「当前操作人是谁」
///   （安全 starter 已经内置了实现）
/// - 应用可以自定义 `Clock` Bean 来控制时间（用于测试场景）
///
/// @author linqibin
/// @since 0.1.0
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "dateTimeProvider")
public class JpaAuditingConfig {

  /// 默认的审计用户提供者。
  ///
  /// 容器里有 {@link CurrentAuditorProvider} 的实现就问它；没有就返回空，表示系统操作。
  /// 用注入来接入，而不是让别的模块提供同名 Bean 来替换，所以不受配置类加载顺序影响。
  ///
  /// @param currentAuditorProvider 当前操作人提供者（可能不存在）
  /// @return 审计用户提供者
  @Bean
  @ConditionalOnMissingBean
  public AuditorAware<Long> auditorAware(
      ObjectProvider<CurrentAuditorProvider> currentAuditorProvider) {
    CurrentAuditorProvider provider = currentAuditorProvider.getIfAvailable();
    if (provider == null) {
      return Optional::empty;
    }
    return provider::currentAuditorId;
  }

  /// 日期时间提供者。
  ///
  /// 使用注入的 `Clock` 实例获取当前时间，便于测试时控制时间。
  /// 如果未配置 Clock Bean，则使用系统默认时钟。
  ///
  /// @param clock 时钟实例（可选）
  /// @return 日期时间提供者
  @Bean
  @ConditionalOnMissingBean
  public DateTimeProvider dateTimeProvider(Optional<Clock> clock) {
    return () -> Optional.of(Instant.now(clock.orElse(Clock.systemDefaultZone())));
  }

  /// 默认时钟 Bean。
  ///
  /// 应用可以覆盖此 Bean 提供固定时钟用于测试。
  ///
  /// @return 系统默认时钟
  @Bean
  @ConditionalOnMissingBean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
