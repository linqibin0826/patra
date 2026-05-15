package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.interceptor.ObservationResolutionInterceptor;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/// 可观测性拦截器自动配置。
///
/// 根据类路径中是否存在对应的 Starter，自动注册相应的拦截器：
///
/// - ObservationResolutionInterceptor - 错误处理管道观测拦截器（patra-starter-core）
///
/// **注意**：
///
/// - Batch 可观测性由 patra-spring-boot-starter-batch 使用 Spring Batch 原生支持处理
/// - HTTP 客户端观测由 Spring Boot 3.x 内置的 `RestClient.Builder` 自动配置处理
///
/// @author Jobs
/// @since 1.0.0
/// @see com.patra.starter.core.error.pipeline.ResolutionInterceptor
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "patra.observability",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ObservationInterceptorsAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(ObservationInterceptorsAutoConfiguration.class);

  /// 构造函数。
  public ObservationInterceptorsAutoConfiguration() {
    log.info("初始化可观测性拦截器自动配置");
  }

  /// 注册错误解析可观测性拦截器。
  ///
  /// 仅在 patra-spring-boot-starter-core 存在时生效。
  ///
  /// @param observationRegistry Micrometer Observation 注册表
  /// @return 错误解析拦截器实例
  @Bean
  @ConditionalOnClass(name = "com.patra.starter.core.error.pipeline.ResolutionInterceptor")
  public ObservationResolutionInterceptor observationResolutionInterceptor(
      ObservationRegistry observationRegistry) {
    log.debug("注册错误解析可观测性拦截器");
    return new ObservationResolutionInterceptor(observationRegistry);
  }
}
