package com.patra.starter.core.xml.autoconfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.dataformat.xml.XmlMapper;

/// XML 自动配置类，当 Jackson XML 模块可用时提供项目级的 {@link XmlMapper}。
///
/// ### 配置内容
///
/// - {@link XmlMapper} - Jackson XML 映射器
///
/// ### 启用条件
///
/// - classpath 中存在 {@link XmlMapper}
/// - 不存在自定义的 {@link XmlMapper} Bean
///
/// ### 设计目标
///
/// - 为需要 XML 处理的模块提供单一的、集中配置的 XmlMapper
/// - 保持条件激活（不需要 XML 的服务不会产生硬依赖）
///
/// @author linqibin
/// @since 0.1.0
/// @see XmlMapper
/// @see com.patra.starter.core.json.autoconfig.JacksonAutoConfiguration
@Slf4j
@AutoConfiguration
@ConditionalOnClass(XmlMapper.class)
public class XmlAutoConfiguration {

  /// 注册单例 {@link XmlMapper}。
  ///
  /// @return 已配置的 XML 映射器实例
  @Bean
  @ConditionalOnMissingBean(XmlMapper.class)
  public XmlMapper xmlMapper() {
    log.debug("创建 XmlMapper");
    return XmlMapper.builder().build();
  }
}
