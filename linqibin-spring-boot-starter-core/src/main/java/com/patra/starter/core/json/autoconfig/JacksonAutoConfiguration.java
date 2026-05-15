package com.patra.starter.core.json.autoconfig;

import com.patra.starter.core.json.ObjectMapperProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/// Jackson 自动配置类，提供 Jackson 3.x {@link ObjectMapper} bean 和 {@link ObjectMapperProvider}。
///
/// ### 配置职责
///
/// 1. **Long → String 序列化**：通过 {@link JsonMapperBuilderCustomizer} 注册 `Long.class`
///    自定义序列化器，防止前端 JavaScript 解析超过 `Number.MAX_SAFE_INTEGER`（2^53-1）
///    的雪花 ID 时精度丢失。
/// 2. **创建 ObjectMapper bean**：如果容器中没有 ObjectMapper bean，使用 {@link JsonMapper#builder()}
///    创建一个带有合理默认配置的 ObjectMapper。
/// 3. **桥接到 JsonMapperHolder**：通过 {@link ObjectMapperProvider} 将 Spring 管理的 ObjectMapper
///    暴露给非 Spring 代码路径。
///
/// ### 配置内容
///
/// - {@link JsonMapperBuilderCustomizer} - Long → String 序列化定制器
/// - {@link ObjectMapper} - Jackson 3.x ObjectMapper（如果不存在则自动创建）
/// - {@link ObjectMapperProvider} - 桥接 Spring 管理的 ObjectMapper 到 {@code JsonMapperHolder}
///
/// ### 使用指南
///
/// - 在 Spring 组件内部优先使用构造注入获取 `ObjectMapper`
/// - 非 Spring 代码使用 {@link dev.linqibin.commons.json.JsonMapperHolder#getObjectMapper()}
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@AutoConfiguration(
    after = org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class)
@ConditionalOnClass(ObjectMapper.class)
public class JacksonAutoConfiguration {

  /// 注册 Long → String 序列化定制器。
  ///
  /// 通过 Spring Boot 4.x 的 {@link JsonMapperBuilderCustomizer} 机制，
  /// 将 `Long.class`（包装类型）序列化为 JSON 字符串，避免前端 JavaScript 解析
  /// 超过 `Number.MAX_SAFE_INTEGER`（9007199254740991）的雪花 ID 时精度丢失。
  ///
  /// 仅影响 `Long`（包装类型），不影响 `long`（原始类型），
  /// 因此 `PageResult.total` 等原始类型字段不受影响。
  ///
  /// @return Long → String 序列化定制器
  @Bean
  public JsonMapperBuilderCustomizer longToStringCustomizer() {
    return builder -> {
      var module = new SimpleModule("LongToStringModule");
      module.addSerializer(Long.class, ToStringSerializer.instance);
      builder.addModule(module);
    };
  }

  /// 创建 Jackson 3.x {@link ObjectMapper} bean。
  ///
  /// 使用 {@link JsonMapper#builder()} 创建带有以下配置的 ObjectMapper：
  ///
  /// - 自动发现并注册所有 Jackson 模块（如 JavaTimeModule）
  ///
  /// @return 配置好的 ObjectMapper 实例
  @ConditionalOnMissingBean(ObjectMapper.class)
  @Bean
  public ObjectMapper objectMapper() {
    log.info("创建 Jackson 3.x ObjectMapper");
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
