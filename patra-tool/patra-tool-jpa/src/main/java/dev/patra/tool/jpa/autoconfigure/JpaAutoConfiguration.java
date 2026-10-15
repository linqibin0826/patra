package dev.patra.tool.jpa.autoconfigure;

import dev.patra.tool.core.error.codes.HttpStdErrors;
import dev.patra.tool.jpa.error.contributor.JpaErrorMappingContributor;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
@AutoConfiguration(
    after = HibernateJpaAutoConfiguration.class,
    afterName = "dev.patra.tool.web.autoconfigure.WebErrorAutoConfiguration")
@ConditionalOnClass({JpaRepository.class})
@ConditionalOnProperty(
    prefix = "patra.jpa",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(JpaProperties.class)
@Import({JpaAuditingConfiguration.class, PatraHibernatePropertiesCustomizer.class})
public final class JpaAutoConfiguration {

  /// JPA / Hibernate / SQL 异常到错误码的映射，只在类路径上有 patra-tool-web、且 web 的错误引擎已注册错误码组时注册
  /// （非 Web 应用里 web 的自动配置不生效，这里也跟着不注册）。
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "dev.patra.tool.web.error.spi.ErrorMappingContributor")
  @ConditionalOnBean(HttpStdErrors.Group.class)
  static class JpaErrorMappingConfiguration {

    /// 注册映射贡献者；顺序由类上的 `@Order` 决定，先于服务自己的贡献者。
    ///
    /// @param http 标准 HTTP 错误定义组
    /// @return JPA 异常映射贡献者
    @Bean
    @ConditionalOnMissingBean
    JpaErrorMappingContributor jpaErrorMappingContributor(HttpStdErrors.Group http) {
      return new JpaErrorMappingContributor(http);
    }
  }

  /// 注册 JSON 列转换器，供 JPA 映射器注入。
  ///
  /// `@DataJpaTest` 切片不加载本配置，需要它的切片测试显式 `@Import(JsonColumnCodec.class)`。
  ///
  /// @param jsonMapper 容器里的 `JsonMapper`
  /// @return JSON 列转换器
  @Bean
  @ConditionalOnMissingBean
  JsonColumnCodec jsonColumnCodec(JsonMapper jsonMapper) {
    return new JsonColumnCodec(jsonMapper);
  }
}
