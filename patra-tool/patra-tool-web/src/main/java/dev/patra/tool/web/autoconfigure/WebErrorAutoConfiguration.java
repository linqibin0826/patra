package dev.patra.tool.web.autoconfigure;

import dev.patra.tool.core.error.codes.HttpStdErrors;
import dev.patra.tool.web.error.adapter.DefaultProblemDetailAdapter;
import dev.patra.tool.web.error.adapter.ProblemDetailAdapter;
import dev.patra.tool.web.error.builder.ProblemDetailBuilder;
import dev.patra.tool.web.error.engine.DefaultErrorResolutionEngine;
import dev.patra.tool.web.error.engine.ErrorResolutionEngine;
import dev.patra.tool.web.error.formatter.DefaultValidationErrorsFormatter;
import dev.patra.tool.web.error.handler.GlobalRestExceptionHandler;
import dev.patra.tool.web.error.handler.WebMvcErrorMappingContributor;
import dev.patra.tool.web.error.pipeline.ErrorResolutionPipeline;
import dev.patra.tool.web.error.pipeline.ResolutionInterceptor;
import dev.patra.tool.web.error.spi.ErrorMappingContributor;
import dev.patra.tool.web.error.spi.ProblemFieldContributor;
import dev.patra.tool.web.error.spi.TraceProvider;
import dev.patra.tool.web.error.spi.ValidationErrorsFormatter;
import dev.patra.tool.web.error.spi.WebProblemFieldContributor;
import dev.patra.tool.web.error.trace.HeaderBasedTraceProvider;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// 错误处理自动配置：解析引擎、管道、追踪 ID、ProblemDetail 构建与全局异常处理器。
///
/// 只在 Servlet Web 应用里生效；`patra.web.enabled` 或 `patra.web.error.enabled` 任一为 false 就整体不注册。
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "patra.web",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnProperty(
    prefix = "patra.web.error",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(WebProperties.class)
public final class WebErrorAutoConfiguration {

  /// 默认从 MDC 取追踪 ID。
  ///
  /// @param properties Web 配置
  /// @return 追踪 ID 提供者
  @Bean
  @ConditionalOnMissingBean
  TraceProvider defaultTraceProvider(WebProperties properties) {
    List<String> headerNames = properties.error().trace().headerNames();
    log.debug("使用默认的基于 Header 的 TraceProvider,Header 名称: {}", headerNames);
    return new HeaderBasedTraceProvider(headerNames);
  }

  /// 错误解析引擎，收集全部映射贡献者。
  ///
  /// @param properties Web 配置
  /// @param mappingContributors 容器里的错误映射贡献者
  /// @return 解析引擎
  @Bean
  @ConditionalOnMissingBean
  ErrorResolutionEngine errorResolutionEngine(
      WebProperties properties, List<ErrorMappingContributor> mappingContributors) {
    WebProperties.Error error = properties.error();
    if (error.contextPrefix() == null || error.contextPrefix().isBlank()) {
      log.warn("patra.web.error.context-prefix 未配置,使用 UNKNOWN 作为统一错误前缀");
    }
    return new DefaultErrorResolutionEngine(error, mappingContributors);
  }

  /// 错误解析管道，按顺序串起容器里的解析拦截器。
  ///
  /// @param engine 解析引擎
  /// @param interceptorsProvider 解析拦截器
  /// @return 解析管道
  @Bean
  @ConditionalOnMissingBean
  ErrorResolutionPipeline errorResolutionPipeline(
      ErrorResolutionEngine engine, ObjectProvider<ResolutionInterceptor> interceptorsProvider) {
    List<ResolutionInterceptor> interceptors = interceptorsProvider.orderedStream().toList();
    log.info("构建错误解析管道,包含 {} 个拦截器", interceptors.size());
    return new ErrorResolutionPipeline(engine, interceptors);
  }

  /// 带服务前缀的标准 HTTP 错误码组。
  ///
  /// @param properties Web 配置
  /// @return 标准错误码组
  @Bean
  @ConditionalOnMissingBean(HttpStdErrors.Group.class)
  HttpStdErrors.Group httpStdErrorsGroup(WebProperties properties) {
    String prefix = properties.error().contextPrefix();
    if (prefix == null || prefix.isBlank()) {
      prefix = "UNKNOWN";
    }
    return HttpStdErrors.of(prefix);
  }

  /// 默认的验证错误格式化器，掩码敏感字段。
  ///
  /// @return 验证错误格式化器
  @Bean
  @ConditionalOnMissingBean
  ValidationErrorsFormatter defaultValidationErrorsFormatter() {
    log.debug("正在注册默认的验证错误格式化器(ValidationErrorsFormatter),用于掩码敏感字段");
    return new DefaultValidationErrorsFormatter();
  }

  /// ProblemDetail 构建器。
  ///
  /// @param properties Web 配置
  /// @param traceProvider 追踪 ID 提供者
  /// @param coreFieldContributors 核心字段贡献者
  /// @param webFieldContributors Web 字段贡献者
  /// @return 构建器
  @Bean
  @ConditionalOnMissingBean
  ProblemDetailBuilder problemDetailBuilder(
      WebProperties properties,
      TraceProvider traceProvider,
      List<ProblemFieldContributor> coreFieldContributors,
      List<WebProblemFieldContributor> webFieldContributors) {
    log.debug(
        "正在注册问题详情构建器(ProblemDetailBuilder),包含 {} 个核心贡献者和 {} 个 Web 贡献者",
        coreFieldContributors.size(),
        webFieldContributors.size());
    return new ProblemDetailBuilder(
        properties.error(), traceProvider, coreFieldContributors, webFieldContributors);
  }

  /// 异常到 ProblemDetail 的适配器。
  ///
  /// @param pipeline 解析管道
  /// @param problemDetailBuilder 构建器
  /// @return 适配器
  @Bean
  @ConditionalOnMissingBean
  ProblemDetailAdapter problemDetailAdapter(
      ErrorResolutionPipeline pipeline, ProblemDetailBuilder problemDetailBuilder) {
    log.debug("正在注册默认的问题详情适配器(ProblemDetailAdapter),用于异常到 ProblemDetail 的转换");
    return new DefaultProblemDetailAdapter(pipeline, problemDetailBuilder);
  }

  /// Spring MVC 自带异常的错误映射：未匹配路径 → 404。
  ///
  /// @param http 标准错误码组
  /// @return 映射贡献者
  @Bean
  @ConditionalOnMissingBean
  WebMvcErrorMappingContributor webMvcErrorMappingContributor(HttpStdErrors.Group http) {
    log.debug("正在注册 Spring MVC 自带异常的错误映射(WebMvcErrorMappingContributor)：未匹配路径 -> 404");
    return new WebMvcErrorMappingContributor(http);
  }

  /// 全局异常处理器，返回 RFC 7807 的 ProblemDetail。
  ///
  /// @param problemDetailAdapter 适配器
  /// @param validationErrorsFormatter 验证错误格式化器
  /// @return 全局异常处理器
  @Bean
  @ConditionalOnMissingBean
  GlobalRestExceptionHandler globalRestExceptionHandler(
      ProblemDetailAdapter problemDetailAdapter,
      ValidationErrorsFormatter validationErrorsFormatter) {
    log.debug("正在注册全局异常处理器(GlobalRestExceptionHandler),返回符合 RFC 7807 的 ProblemDetail 错误响应");
    return new GlobalRestExceptionHandler(problemDetailAdapter, validationErrorsFormatter);
  }
}
