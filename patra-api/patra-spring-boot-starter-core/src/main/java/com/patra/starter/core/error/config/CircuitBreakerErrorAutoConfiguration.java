package com.patra.starter.core.error.config;

import com.patra.starter.core.error.pipeline.interceptor.CircuitBreakerInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// 错误处理熔断器自动配置类。
///
/// 配置内容:
///
/// - {@link CircuitBreaker} - 错误解析专用熔断器,保护错误处理管道
///   - {@link CircuitBreakerInterceptor} - 熔断器拦截器,在管道中执行熔断保护
///
/// 启用条件:
///
/// - classpath 中存在 Resilience4j CircuitBreaker
///   - `patra.error.circuit-breaker.enabled=true`(默认启用)
///
/// 设计说明: 此配置与 {@link CoreErrorAutoConfiguration} 分离,避免 Resilience4j 不存在时 出现
/// ClassNotFoundException(可选依赖)。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@AutoConfiguration(after = CoreErrorAutoConfiguration.class)
@ConditionalOnClass(CircuitBreaker.class)
@EnableConfigurationProperties(ErrorProperties.class)
public class CircuitBreakerErrorAutoConfiguration {

  /// 创建错误解析专用熔断器,保护错误处理管道免受级联故障影响。
  ///
  /// 熔断器配置:
  ///
  /// - 失败率阈值: 达到此阈值时触发断路器打开
  ///   - 滑动窗口大小: 计算失败率的样本窗口
  ///   - 最小调用次数: 触发断路器所需的最小调用数
  ///   - 半开状态允许调用数: 断路器半开时允许的探测调用数
  ///   - 断路器打开等待时长: 断路器打开后等待多久进入半开状态
  ///
  /// @param errorProperties 错误配置属性
  /// @return 错误解析熔断器实例

  @Bean(name = "errorResolutionCircuitBreaker")
  @ConditionalOnProperty(
      prefix = "patra.error.circuit-breaker",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CircuitBreaker errorResolutionCircuitBreaker(ErrorProperties errorProperties) {
    ErrorProperties.CircuitBreakerProperties cb = errorProperties.getCircuitBreaker();
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(cb.getFailureRateThreshold())
            .minimumNumberOfCalls(cb.getMinimumNumberOfCalls())
            .slidingWindowSize(cb.getSlidingWindowSize())
            .permittedNumberOfCallsInHalfOpenState(cb.getPermittedCallsInHalfOpenState())
            .waitDurationInOpenState(cb.getWaitDurationInOpenState())
            .build();
    log.info(
        "创建错误解析熔断器: 失败率阈值={} 滑动窗口大小={}", cb.getFailureRateThreshold(), cb.getSlidingWindowSize());
    return CircuitBreaker.of("patra-error-resolution", config);
  }

  /// 创建熔断器拦截器,在错误解析管道中执行熔断保护。
  ///
  /// 启用条件: 存在名为 "errorResolutionCircuitBreaker" 的 Bean
  ///
  /// @param circuitBreaker 错误解析熔断器
  /// @param errorProperties 错误配置属性
  /// @return 熔断器拦截器实例

  @Bean
  @ConditionalOnBean(name = "errorResolutionCircuitBreaker")
  public CircuitBreakerInterceptor circuitBreakerInterceptor(
      @Qualifier("errorResolutionCircuitBreaker") CircuitBreaker circuitBreaker,
      ErrorProperties errorProperties) {
    return new CircuitBreakerInterceptor(circuitBreaker, errorProperties);
  }
}
