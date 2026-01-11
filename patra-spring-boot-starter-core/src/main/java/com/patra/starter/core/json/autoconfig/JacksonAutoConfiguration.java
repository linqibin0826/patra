package com.patra.starter.core.json.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.patra.starter.core.json.ObjectMapperProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/// Jackson 自动配置类,提供 Jackson 2.x {@link ObjectMapper} bean 和 {@link ObjectMapperProvider}。
///
/// ### Spring Boot 4.0 兼容性
///
/// Spring Boot 4.0 默认使用 Jackson 3.x (`tools.jackson.databind.json.JsonMapper`)，不再自动创建
/// Jackson 2.x 的 `com.fasterxml.jackson.databind.ObjectMapper` bean。但许多第三方库和现有代码
/// 仍然期望 Jackson 2.x 的 ObjectMapper bean。
///
/// 此配置类的职责：
///
/// 1. **创建 ObjectMapper bean**：如果容器中没有 ObjectMapper bean，使用 {@link JsonMapper#builder()}
///    创建一个带有合理默认配置的 ObjectMapper。
/// 2. **桥接到 JsonMapperHolder**：通过 {@link ObjectMapperProvider} 将 Spring 管理的 ObjectMapper
///    暴露给非 Spring 代码路径。
///
/// ### 配置内容
///
/// - {@link ObjectMapper} - Jackson 2.x ObjectMapper（如果不存在则自动创建）
/// - {@link ObjectMapperProvider} - 桥接 Spring 管理的 ObjectMapper 到 {@code JsonMapperHolder}
///
/// ### 使用指南
///
/// - 在 Spring 组件内部优先使用构造注入获取 `ObjectMapper`
/// - 非 Spring 代码使用 {@link com.patra.common.json.JsonMapperHolder#getObjectMapper()}
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@AutoConfiguration(
    after = org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class)
@ConditionalOnClass(ObjectMapper.class)
public class JacksonAutoConfiguration {

  /// 创建 Jackson 2.x {@link ObjectMapper} bean。
  ///
  /// Spring Boot 4.0 不再自动创建 Jackson 2.x ObjectMapper，此方法填补这一空白。
  /// 使用 {@link JsonMapper#builder()} 创建带有以下配置的 ObjectMapper：
  ///
  /// - 自动发现并注册所有 Jackson 模块（如 JavaTimeModule）
  ///
  /// @return 配置好的 ObjectMapper 实例
  @ConditionalOnMissingBean(ObjectMapper.class)
  @Bean
  public ObjectMapper objectMapper() {
    log.info("创建 Jackson 2.x ObjectMapper（Spring Boot 4.0 兼容性）");
    return JsonMapper.builder().findAndAddModules().build();
  }

  /// 注册 {@link ObjectMapperProvider},将 Spring 管理的 ObjectMapper 桥接到 {@code JsonMapperHolder}。
  ///
  /// @param objectMapper Spring 容器中的 ObjectMapper bean
  /// @return ObjectMapper 提供者实例
  @ConditionalOnMissingBean(ObjectMapperProvider.class)
  @Bean
  public ObjectMapperProvider jacksonProvider(ObjectMapper objectMapper) {
    log.debug("注册 ObjectMapperProvider，桥接到 JsonMapperHolder");
    return new ObjectMapperProvider(objectMapper);
  }
}
