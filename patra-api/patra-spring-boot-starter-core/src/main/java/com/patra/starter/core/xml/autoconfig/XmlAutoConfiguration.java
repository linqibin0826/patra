package com.patra.starter.core.xml.autoconfig;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/// XML 自动配置类,当 Jackson XML 模块可用时提供项目级的 {@link XmlMapper}。
///
/// 配置内容:
///
/// - {@link XmlMapper} - 共享 Jackson 全局配置的 XML 映射器
///
/// 启用条件:
///
/// - classpath 中存在 {@link XmlMapper}
///   - 存在 {@link Jackson2ObjectMapperBuilder} Bean
///   - 不存在自定义的 {@link XmlMapper} Bean
///
/// 设计目标:
///
/// - 为需要 XML 处理的模块提供单一的、集中配置的 XmlMapper
///   - 复用 Spring Boot 的 {@link Jackson2ObjectMapperBuilder},使全局 Jackson 自定义配置生效
///   - 保持条件激活(不需要 XML 的服务不会产生硬依赖)
///
/// @author Patra Team
/// @since 2.0
@Slf4j
@AutoConfiguration(
    after = org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class)
@ConditionalOnClass(XmlMapper.class)
public class XmlAutoConfiguration {

  /// 注册由共享的 Jackson 构建器创建的单例 {@link XmlMapper}。
  ///
  /// JSON 自定义配置(模块、日期时间、命名策略)也会通过共享的构建器配置应用到 XML 映射。
  ///
  /// @param builder 共享的 Jackson ObjectMapper 构建器
  /// @return 已配置的 XML 映射器实例
  @Bean
  @ConditionalOnBean(Jackson2ObjectMapperBuilder.class)
  @ConditionalOnMissingBean(XmlMapper.class)
  public XmlMapper xmlMapper(Jackson2ObjectMapperBuilder builder) {
    log.debug("已加载 XmlAutoConfiguration.xmlMapper()");
    return builder.createXmlMapper(true).build();
  }
}
