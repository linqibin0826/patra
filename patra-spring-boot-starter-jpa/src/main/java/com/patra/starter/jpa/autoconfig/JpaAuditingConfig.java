package com.patra.starter.jpa.autoconfig;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
/// - 应用可以自定义 `AuditorAware<Long>` Bean 来提供真实的用户 ID（如从 SecurityContext 获取）
/// - 应用可以自定义 `Clock` Bean 来控制时间（用于测试场景）
///
/// @author linqibin
/// @since 0.1.0
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "dateTimeProvider")
public class JpaAuditingConfig {

  /// 默认的审计用户提供者。
  ///
  /// 返回空 Optional，表示系统操作（无用户上下文）。
  /// 应用应该覆盖此 Bean 以从安全上下文获取实际用户 ID。
  ///
  /// @return 审计用户提供者
  @Bean
  @ConditionalOnMissingBean
  public AuditorAware<Long> auditorAware() {
    // TODO: 从安全上下文获取当前用户 ID
    // 示例: return () -> Optional.ofNullable(SecurityContextHolder.getContext())
    //           .map(SecurityContext::getAuthentication)
    //           .filter(Authentication::isAuthenticated)
    //           .map(auth -> ((UserDetails) auth.getPrincipal()).getId());
    return () -> Optional.empty();
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
