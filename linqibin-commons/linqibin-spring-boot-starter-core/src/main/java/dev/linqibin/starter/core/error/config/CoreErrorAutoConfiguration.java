package dev.linqibin.starter.core.error.config;

import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.starter.core.error.engine.DefaultErrorResolutionEngine;
import dev.linqibin.starter.core.error.engine.ErrorResolutionEngine;
import dev.linqibin.starter.core.error.pipeline.ErrorResolutionPipeline;
import dev.linqibin.starter.core.error.pipeline.ResolutionInterceptor;
import dev.linqibin.starter.core.error.spi.ErrorMappingContributor;
import dev.linqibin.starter.core.error.spi.TraceProvider;
import dev.linqibin.starter.core.error.trace.HeaderBasedTraceProvider;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 平台错误处理自动配置类。
///
/// 配置内容:
///
/// - {@link ErrorResolutionEngine} - 错误解析引擎,负责异常到错误码的映射
///   - {@link ErrorResolutionPipeline} - 错误解析管道,通过拦截器链处理异常
///   - {@link TraceProvider} - 追踪上下文提取器(默认基于 HTTP Header)
///   - {@link HttpStdErrors.Group} - 标准 HTTP 错误定义组
///
/// 启用条件:
///
/// - `linqibin.starter.core.error.enabled=true`(默认启用)
///
/// 设计原则: 提供可被应用程序覆盖的默认引擎和核心 Bean。
/// 可观测性功能(追踪、指标)由 patra-spring-boot-starter-observability 通过 ResolutionInterceptor 扩展点提供。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ErrorProperties.class, TracingProperties.class})
@ConditionalOnProperty(
    prefix = "linqibin.starter.core.error",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CoreErrorAutoConfiguration {

  /// 提供默认的基于 HTTP Header 的追踪上下文提取器。
  ///
  /// 从配置的 HTTP Header 中提取追踪 ID,支持多种 Header 名称(如 X-Trace-ID, X-B3-TraceId 等)。
  ///
  /// @param tracingProperties 追踪配置属性
  /// @return 基于 Header 的追踪上下文提取器

  @Bean
  @ConditionalOnMissingBean
  public TraceProvider defaultTraceProvider(TracingProperties tracingProperties) {
    log.debug("使用默认的基于 Header 的 TraceProvider,Header 名称: {}", tracingProperties.getHeaderNames());
    return new HeaderBasedTraceProvider(tracingProperties);
  }

  /// 提供错误解析引擎,负责将异常映射为标准化的错误表示。
  ///
  /// 解析策略(按优先级):
  ///
  /// @param errorProperties 错误配置属性
  /// @param mappingContributors 错误映射贡献者列表(SPI 扩展点)
  /// @return 错误解析引擎实例

  @Bean
  @ConditionalOnMissingBean
  public ErrorResolutionEngine errorResolutionEngine(
      ErrorProperties errorProperties, List<ErrorMappingContributor> mappingContributors) {
    if (errorProperties.getContextPrefix() == null
        || errorProperties.getContextPrefix().isBlank()) {
      log.warn("linqibin.starter.core.error.context-prefix 未配置,使用 UNKNOWN 作为统一错误前缀");
    }
    return new DefaultErrorResolutionEngine(errorProperties, mappingContributors);
  }

  /// 提供错误解析管道,通过拦截器责任链处理异常解析流程。
  ///
  /// 管道执行流程:
  ///
  /// ```
  ///
  /// 异常抛出
  ///   ↓
  /// 拦截器链 (按 @Order 排序)
  ///   ├─ CircuitBreakerInterceptor (熔断保护,可选)
  ///   └─ ... (自定义拦截器,如 observability starter 提供的追踪和指标拦截器)
  ///   ↓
  /// ErrorResolutionEngine (核心解析逻辑)
  ///   ↓
  /// ErrorResolution (标准化错误表示)
  ///
  /// ```
  ///
  /// @param engine 错误解析引擎
  /// @param interceptorsProvider 拦截器提供者,自动收集所有 {@link ResolutionInterceptor} Bean
  /// @return 错误解析管道实例

  @Bean
  public ErrorResolutionPipeline errorResolutionPipeline(
      ErrorResolutionEngine engine, ObjectProvider<ResolutionInterceptor> interceptorsProvider) {
    List<ResolutionInterceptor> interceptors = interceptorsProvider.orderedStream().toList();
    log.info("构建错误解析管道,包含 {} 个拦截器", interceptors.size());
    return new ErrorResolutionPipeline(engine, interceptors);
  }

  /// 提供标准 HTTP 错误定义组,用于统一的错误码定义。
  ///
  /// 使用配置的 context-prefix 作为错误码前缀,例如配置为 "INGEST" 时, 错误码为 "INGEST:BAD_REQUEST", "INGEST:NOT_FOUND"
  /// 等。
  ///
  /// @param errorProperties 错误配置属性
  /// @return HTTP 标准错误定义组
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
