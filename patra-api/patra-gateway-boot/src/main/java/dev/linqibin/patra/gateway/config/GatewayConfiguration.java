package dev.linqibin.patra.gateway.config;

import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.gateway.error.GatewayErrorMappingContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 网关自己的装配：目前只有错误映射。
@Configuration(proxyBeanMethods = false)
public class GatewayConfiguration {

  /// 到不了下游与无实例的错误映射，由 starter-core 的错误引擎收集。
  ///
  /// @param http 按 `linqibin.starter.core.error.context-prefix` 生成错误码的组
  /// @return contributor
  @Bean
  public GatewayErrorMappingContributor gatewayErrorMappingContributor(HttpStdErrors.Group http) {
    return new GatewayErrorMappingContributor(http);
  }
}
