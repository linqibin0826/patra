package com.patra.starter.core.xml.autoconfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.dataformat.xml.XmlMapper;

/// XML 自动配置类,当 Jackson XML 模块可用时提供项目级的 {@link XmlMapper}。
///
/// 配置内容:
///
/// - {@link XmlMapper} - Jackson 3 的 XML 映射器
///
/// 启用条件:
///
/// - classpath 中存在 {@link XmlMapper}
/// - 不存在自定义的 {@link XmlMapper} Bean
///
/// 设计目标:
///
/// - 为需要 XML 处理的模块提供单一的、集中配置的 XmlMapper
/// - 使用 Jackson 3 原生 API（Spring Boot 4.0 不再提供 Jackson2ObjectMapperBuilder）
/// - 保持条件激活(不需要 XML 的服务不会产生硬依赖)
///
/// ### Jackson 版本混用说明（重要）
///
/// 本项目在 Spring Boot 4.0 下同时使用两个 Jackson 版本：
///
/// | 用途 | Jackson 版本 | 包名 | 原因 |
/// |------|-------------|------|------|
/// | **XML 处理** | Jackson 3.x | `tools.jackson` | Spring Boot 4.0 默认，无第三方库兼容问题 |
/// | **JSON 处理** | Jackson 2.x | `com.fasterxml.jackson` | 第三方库（如 Spring Security、Feign）依赖 |
///
/// **这是有意为之的设计决策**，而非遗漏：
///
/// - JSON 使用 Jackson 2.x 是因为众多第三方库尚未迁移到 Jackson 3.x
/// - XML 使用 Jackson 3.x 是因为 XML 处理相对独立，无第三方库依赖约束
/// - 两个版本在运行时可共存，无冲突（不同的包名和 Maven 坐标）
///
/// 参见 {@link com.patra.starter.core.json.autoconfig.JacksonAutoConfiguration} 了解 JSON 配置。
///
/// ### Spring Boot 4.0 迁移说明
///
/// Spring Boot 4.0 的 JacksonAutoConfiguration 仅为 JsonMapper 提供自动配置，
/// 不再支持通过 Jackson2ObjectMapperBuilder 创建 XmlMapper。
/// 本配置类使用 Jackson 3 的 XmlMapper.builder() 直接创建实例。
///
/// @author linqibin
/// @since 0.1.0
/// @see XmlMapper
@Slf4j
@AutoConfiguration
@ConditionalOnClass(XmlMapper.class)
public class XmlAutoConfiguration {

  /// 注册单例 {@link XmlMapper}。
  ///
  /// 使用 Jackson 3 的 XmlMapper.builder() 创建实例，
  /// 配置与 Spring Boot 4 默认 JsonMapper 对齐的选项。
  ///
  /// @return 已配置的 XML 映射器实例
  @Bean
  @ConditionalOnMissingBean(XmlMapper.class)
  public XmlMapper xmlMapper() {
    log.debug("已加载 XmlAutoConfiguration.xmlMapper() - 使用 Jackson 3 XmlMapper.builder()");
    return XmlMapper.builder()
        // Jackson 3 默认使用 ISO-8601 日期格式，与 Spring Boot 4 JsonMapper 对齐
        .build();
  }
}
