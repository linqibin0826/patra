package dev.patra.tool.jpa.autoconfigure;

import dev.patra.tool.jpa.audit.CurrentAuditorProvider;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/// JPA 审计配置：给主表实体的 `@CreatedBy` / `@LastModifiedBy` 填当前操作人。
///
/// 只管操作人。`created_at` / `updated_at` 由数据库触发器填（库表规范第 4 节），实体不映射，这里也就没有时间提供者。
///
/// 拿不到当前用户时 Spring Data 不动 `updated_by`，由主表基类上的 `UpdatedByResetListener` 在更新前先清空，系统写入才会留空。
///
/// 扩展点：提供一个 {@link CurrentAuditorProvider} Bean 告诉审计「当前操作人是谁」（安全 starter 已经内置了实现）。
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfiguration {

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
}
