package dev.patra.tool.jpa.autoconfig;

import dev.patra.tool.core.error.codes.HttpStdErrors;
import dev.patra.tool.jpa.error.contributor.JpaErrorMappingContributor;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;
import tools.jackson.databind.json.JsonMapper;

/// JPA 自动配置类。
///
/// **功能说明**：
///
/// - 配置 Hibernate 7.1 批量写入优化
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
public class JpaAutoConfiguration {

  /// 注册 JPA 异常映射贡献器。
  ///
  /// @param http 标准 HTTP 错误定义组
  /// @return JPA 异常映射贡献器
  @Bean
  @ConditionalOnMissingBean
  public JpaErrorMappingContributor jpaErrorMappingContributor(HttpStdErrors.Group http) {
    return new JpaErrorMappingContributor(http);
  }

  /// 注册 JSON 列转换器，供 JPA 映射器注入。
  ///
  /// `@DataJpaTest` 切片不加载本配置，需要它的切片测试显式 `@Import(JsonColumnCodec.class)`。
  ///
  /// @param jsonMapper 容器里的 `JsonMapper`
  /// @return JSON 列转换器
  @Bean
  @ConditionalOnMissingBean
  public JsonColumnCodec jsonColumnCodec(JsonMapper jsonMapper) {
    return new JsonColumnCodec(jsonMapper);
  }
}
