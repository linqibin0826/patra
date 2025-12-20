package com.patra.starter.jpa.autoconfig;

import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.jpa.error.contributor.JpaErrorMappingContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;

/// Patra JPA 自动配置类。
///
/// **功能说明**：
///
/// - 配置 Hibernate 6.6 批量写入优化
/// - 注册 JPA 异常映射贡献器
/// - 导入审计配置（自动填充 createdAt/updatedAt/createdBy/updatedBy）
///
/// **扫描机制**：
///
/// 依赖 Spring Boot 默认扫描（`@SpringBootApplication` 所在包及其子包），
/// 无需显式配置 `@EnableJpaRepositories` 或 `@EntityScan`。
///
/// @author linqibin
/// @since 0.1.0
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({JpaRepository.class})
@Import({JpaAuditingConfig.class, HibernatePropertiesCustomizer.class})
public class PatraJpaAutoConfiguration {

  /// 注册 JPA 异常映射贡献器。
  ///
  /// @param http 标准 HTTP 错误定义组
  /// @return JPA 异常映射贡献器
  @Bean
  @ConditionalOnMissingBean
  public JpaErrorMappingContributor jpaErrorMappingContributor(HttpStdErrors.Group http) {
    return new JpaErrorMappingContributor(http);
  }
}
