package com.patra.starter.feign.error.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder;
import com.patra.starter.feign.error.interceptor.TraceIdRequestInterceptor;
import com.patra.starter.feign.error.observation.FeignErrorObservationRecorder;
import com.patra.starter.feign.error.observation.MicrometerFeignErrorObservationRecorder;
import feign.codec.ErrorDecoder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// Feign 错误处理自动配置:注册错误解码器、跟踪传播拦截器和可选的观察记录器
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(FeignErrorProperties.class)
@ConditionalOnClass(
    name = {"feign.Feign", "feign.codec.ErrorDecoder", "org.springframework.http.ProblemDetail"})
@ConditionalOnProperty(
    prefix = "patra.feign.problem",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class FeignErrorAutoConfiguration {

  /// 注册 Feign 错误观察记录器 Bean。
  ///
  /// 根据配置和 Micrometer 可用性选择合适的实现:
  ///
  /// - 观察功能禁用时返回 NO_OP 实现
  ///   - MeterRegistry 不可用时降级为 NO_OP 实现
  ///   - 其他情况返回基于 Micrometer 的实现
  ///
  /// @param properties Feign 错误配置属性
  /// @param meterRegistryProvider Micrometer 注册表提供者
  /// @return Feign 错误观察记录器实例
  @Bean
  @ConditionalOnMissingBean
  public FeignErrorObservationRecorder feignErrorObservationRecorder(
      FeignErrorProperties properties, ObjectProvider<MeterRegistry> meterRegistryProvider) {
    if (!properties.getObservation().isEnabled()) {
      log.info("Feign 错误观察已禁用,回退到 NO_OP 记录器");
      return FeignErrorObservationRecorder.NO_OP;
    }
    MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
    if (meterRegistry == null) {
      log.warn("Micrometer MeterRegistry 不可用,Feign 错误观察降级为 NO_OP");
      return FeignErrorObservationRecorder.NO_OP;
    }
    return new MicrometerFeignErrorObservationRecorder(meterRegistry, properties);
  }

  /// 注册 ProblemDetail 错误解码器 Bean。
  ///
  /// 创建支持 RFC 7807 ProblemDetail 格式的错误解码器,配置容错模式和观察记录。
  ///
  /// @param objectMapper JSON 序列化器
  /// @param properties Feign 错误配置属性
  /// @param observationRecorder 观察记录器
  /// @return Feign 错误解码器实例
  @Bean
  @ConditionalOnMissingBean(ErrorDecoder.class)
  public ErrorDecoder problemDetailErrorDecoder(
      ObjectMapper objectMapper,
      FeignErrorProperties properties,
      FeignErrorObservationRecorder observationRecorder) {
    log.info("配置 ProblemDetailErrorDecoder (容错模式已启用: {})", properties.isTolerant());
    return new ProblemDetailErrorDecoder(objectMapper, properties, observationRecorder);
  }

  /// 注册跟踪标识符请求拦截器 Bean。
  ///
  /// 创建 Feign 拦截器以在出站请求中传播当前跟踪标识符。
  ///
  /// @param traceProvider 跟踪标识符提供者 SPI
  /// @param tracingProperties 跟踪配置属性
  /// @return 跟踪标识符请求拦截器实例
  @Bean
  @ConditionalOnMissingBean(TraceIdRequestInterceptor.class)
  public TraceIdRequestInterceptor traceIdRequestInterceptor(
      TraceProvider traceProvider, TracingProperties tracingProperties) {
    log.info("配置 TraceIdRequestInterceptor,使用请求头 {}", tracingProperties.getHeaderNames());
    return new TraceIdRequestInterceptor(traceProvider, tracingProperties);
  }
}
