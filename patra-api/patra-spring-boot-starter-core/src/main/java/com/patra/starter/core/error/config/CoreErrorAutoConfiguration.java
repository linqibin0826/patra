package com.patra.starter.core.error.config;

import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.core.error.engine.DefaultErrorResolutionEngine;
import com.patra.starter.core.error.engine.ErrorResolutionEngine;
import com.patra.starter.core.error.observation.ErrorObservationRecorder;
import com.patra.starter.core.error.observation.MicrometerErrorObservationRecorder;
import com.patra.starter.core.error.pipeline.ErrorResolutionPipeline;
import com.patra.starter.core.error.pipeline.ResolutionInterceptor;
import com.patra.starter.core.error.pipeline.interceptor.MetricsInterceptor;
import com.patra.starter.core.error.pipeline.interceptor.TracingInterceptor;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.core.error.trace.HeaderBasedTraceProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 平台错误处理自动配置类。
 *
 * <p>配置内容:
 *
 * <ul>
 *   <li>{@link ErrorResolutionEngine} - 错误解析引擎,负责异常到错误码的映射
 *   <li>{@link ErrorResolutionPipeline} - 错误解析管道,通过拦截器链处理异常
 *   <li>{@link TracingInterceptor} - 追踪上下文传播拦截器
 *   <li>{@link MetricsInterceptor} - 错误指标记录拦截器
 *   <li>{@link TraceProvider} - 追踪上下文提取器(默认基于 HTTP Header)
 *   <li>{@link ErrorObservationRecorder} - 错误观测记录器(基于 Micrometer)
 *   <li>{@link HttpStdErrors.Group} - 标准 HTTP 错误定义组
 * </ul>
 *
 * <p>启用条件:
 *
 * <ul>
 *   <li>{@code patra.error.enabled=true}(默认启用)
 * </ul>
 *
 * <p>设计原则: 提供可被应用程序覆盖的默认引擎、拦截器和观测 Bean。
 *
 * @author Patra Team
 * @since 2.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ErrorProperties.class, TracingProperties.class})
@ConditionalOnProperty(
    prefix = "patra.error",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CoreErrorAutoConfiguration {

  /**
   * 提供默认的基于 HTTP Header 的追踪上下文提取器。
   *
   * <p>从配置的 HTTP Header 中提取追踪 ID,支持多种 Header 名称(如 X-Trace-ID, X-B3-TraceId 等)。
   *
   * @param tracingProperties 追踪配置属性
   * @return 基于 Header 的追踪上下文提取器
   */
  @Bean
  @ConditionalOnMissingBean
  public TraceProvider defaultTraceProvider(TracingProperties tracingProperties) {
    log.debug("使用默认的基于 Header 的 TraceProvider,Header 名称: {}", tracingProperties.getHeaderNames());
    return new HeaderBasedTraceProvider(tracingProperties);
  }

  /**
   * 提供错误观测记录器,负责记录错误解析的指标和慢解析警告。
   *
   * <p>根据配置和 Micrometer 可用性决定使用何种实现:
   *
   * <ul>
   *   <li>观测已启用且 MeterRegistry 可用: 使用 {@link MicrometerErrorObservationRecorder}
   *   <li>观测已禁用或 MeterRegistry 不可用: 使用 NO_OP 实现(不记录任何指标)
   * </ul>
   *
   * @param errorProperties 错误配置属性
   * @param meterRegistryProvider Micrometer 指标注册表提供者
   * @return 错误观测记录器实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ErrorObservationRecorder errorObservationRecorder(
      ErrorProperties errorProperties, ObjectProvider<MeterRegistry> meterRegistryProvider) {
    if (!errorProperties.getObservation().isEnabled()) {
      log.info("错误观测已禁用,注入 NO_OP 记录器");
      return ErrorObservationRecorder.NO_OP;
    }
    MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
    if (meterRegistry == null) {
      log.warn("Micrometer MeterRegistry 不可用,降级为 NO_OP 观测");
      return ErrorObservationRecorder.NO_OP;
    }
    return new MicrometerErrorObservationRecorder(meterRegistry, errorProperties);
  }

  /**
   * 提供错误解析引擎,负责将异常映射为标准化的错误表示。
   *
   * <p>解析策略(按优先级):
   *
   * <ol>
   *   <li>SPI 贡献者映射: 使用 {@link ErrorMappingContributor} 提供的自定义映射
   *   <li>特征映射: 基于异常实现的接口(如 ErrorCodeLike)进行映射
   *   <li>类名启发式: 根据异常类名生成错误码(如 IllegalArgumentException → ILLEGAL_ARGUMENT)
   * </ol>
   *
   * @param errorProperties 错误配置属性
   * @param mappingContributors 错误映射贡献者列表(SPI 扩展点)
   * @return 错误解析引擎实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ErrorResolutionEngine errorResolutionEngine(
      ErrorProperties errorProperties, List<ErrorMappingContributor> mappingContributors) {
    if (errorProperties.getContextPrefix() == null
        || errorProperties.getContextPrefix().isBlank()) {
      log.warn("patra.error.context-prefix 未配置,使用 UNKNOWN 作为统一错误前缀");
    }
    return new DefaultErrorResolutionEngine(errorProperties, mappingContributors);
  }

  /**
   * 提供错误解析管道,通过拦截器责任链处理异常解析流程。
   *
   * <p>管道执行流程:
   *
   * <pre>
   * 异常抛出
   *   ↓
   * 拦截器链 (按 @Order 排序)
   *   ├─ TracingInterceptor (追踪传播)
   *   ├─ MetricsInterceptor (指标记录)
   *   ├─ CircuitBreakerInterceptor (熔断保护,可选)
   *   └─ ... (自定义拦截器)
   *   ↓
   * ErrorResolutionEngine (核心解析逻辑)
   *   ↓
   * ErrorResolution (标准化错误表示)
   * </pre>
   *
   * @param engine 错误解析引擎
   * @param interceptorsProvider 拦截器提供者,自动收集所有 {@link ResolutionInterceptor} Bean
   * @return 错误解析管道实例
   */
  @Bean
  public ErrorResolutionPipeline errorResolutionPipeline(
      ErrorResolutionEngine engine, ObjectProvider<ResolutionInterceptor> interceptorsProvider) {
    List<ResolutionInterceptor> interceptors = interceptorsProvider.orderedStream().toList();
    log.info("构建错误解析管道,包含 {} 个拦截器", interceptors.size());
    return new ErrorResolutionPipeline(engine, interceptors);
  }

  /**
   * 提供指标拦截器,负责记录错误解析的指标和慢解析警告。
   *
   * <p>启用条件: {@code patra.error.observation.enabled=true}(默认启用)
   *
   * @param observationRecorder 错误观测记录器
   * @param errorProperties 错误配置属性
   * @return 指标拦截器实例
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "patra.error.observation",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public MetricsInterceptor metricsInterceptor(
      ErrorObservationRecorder observationRecorder, ErrorProperties errorProperties) {
    return new MetricsInterceptor(observationRecorder, errorProperties.getObservation());
  }

  /**
   * 提供追踪拦截器,负责传播追踪上下文到错误解析流程中。
   *
   * <p>将追踪 ID 注入到错误解析结果中,支持分布式追踪系统(如 SkyWalking)关联错误日志。
   *
   * @param traceProvider 追踪上下文提取器
   * @return 追踪拦截器实例
   */
  @Bean
  @ConditionalOnMissingBean
  public TracingInterceptor tracingInterceptor(TraceProvider traceProvider) {
    return new TracingInterceptor(traceProvider);
  }

  /**
   * 提供标准 HTTP 错误定义组,用于统一的错误码定义。
   *
   * <p>使用配置的 context-prefix 作为错误码前缀,例如配置为 "INGEST" 时, 错误码为 "INGEST:BAD_REQUEST", "INGEST:NOT_FOUND"
   * 等。
   *
   * @param errorProperties 错误配置属性
   * @return HTTP 标准错误定义组
   */
  @Bean
  @ConditionalOnMissingBean(HttpStdErrors.Group.class)
  public HttpStdErrors.Group httpStdErrorsGroup(ErrorProperties errorProperties) {
    String prefix = errorProperties.getContextPrefix();
    if (prefix == null || prefix.isBlank()) {
      prefix = "UNKNOWN";
    }
    return HttpStdErrors.of(prefix);
  }
}
