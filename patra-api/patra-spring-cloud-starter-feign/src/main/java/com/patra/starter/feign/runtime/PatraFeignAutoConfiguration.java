package com.patra.starter.feign.runtime;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/// Patra Feign Starter 自动配置入口点
///
/// 为 Feign 客户端注册横切关注点组件,包括负责传播共享请求头(如调用者服务标识符)的 {@link PatraFeignRequestInterceptor}。
///
/// **基于约定的 Feign 客户端扫描:** 自动启用对 `com.patra` 包下所有标注了 `@FeignClient` 的接口的扫描。按照约定,标准的
/// RPC 客户端应放置在 `com.patra.{module`.api.rpc.client} 包中。
///
/// **注意:** 如果需要自定义扫描配置,专用的基础设施客户端可以在其特定的 Starter 中 定义自己的 `@EnableFeignClients`。
@AutoConfiguration
@EnableConfigurationProperties(PatraFeignProperties.class)
@EnableFeignClients(basePackages = "com.patra")
@ConditionalOnClass(
    name = {
      "feign.Feign",
    })
@ConditionalOnProperty(
    prefix = "patra.feign",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PatraFeignAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public PatraFeignRequestInterceptor patraFeignRequestInterceptor(
      PatraFeignProperties props, Environment env) {
    return new PatraFeignRequestInterceptor(props, env);
  }
}
