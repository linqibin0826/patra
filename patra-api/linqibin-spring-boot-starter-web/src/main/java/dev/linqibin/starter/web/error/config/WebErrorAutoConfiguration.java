package dev.linqibin.starter.web.error.config;

import dev.linqibin.starter.core.error.config.ErrorProperties;
import dev.linqibin.starter.core.error.pipeline.ErrorResolutionPipeline;
import dev.linqibin.starter.core.error.spi.ProblemFieldContributor;
import dev.linqibin.starter.core.error.spi.TraceProvider;
import dev.linqibin.starter.web.error.adapter.DefaultProblemDetailAdapter;
import dev.linqibin.starter.web.error.adapter.ProblemDetailAdapter;
import dev.linqibin.starter.web.error.builder.ProblemDetailBuilder;
import dev.linqibin.starter.web.error.formatter.DefaultValidationErrorsFormatter;
import dev.linqibin.starter.web.error.handler.GlobalRestExceptionHandler;
import dev.linqibin.starter.web.error.spi.ValidationErrorsFormatter;
import dev.linqibin.starter.web.error.spi.WebProblemFieldContributor;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// Web 层错误处理组件的自动配置。
///
/// 此配置类负责:
///
/// - 创建验证错误格式化器 {@link ValidationErrorsFormatter},用于掩码敏感字段
///   - 创建问题详情构建器 {@link ProblemDetailBuilder},将异常转换为 RFC 7807 ProblemDetail
///   - 创建问题详情适配器 {@link ProblemDetailAdapter},集成错误解析管道
///   - 创建全局异常处理器 {@link GlobalRestExceptionHandler},统一处理 REST API 异常
///
/// **激活条件:**
///
/// - Servlet Web 应用环境
///   - `patra.web.problem.enabled=true`(默认启用)
///
/// **配置示例:**
///
/// ```java
/// patra:
///   web:
///     problem:
///       enabled: true
///       include-stack-trace: false
/// ```
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "patra.web.problem",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(WebErrorProperties.class)
public class WebErrorAutoConfiguration {

  /// 创建默认的验证错误格式化器,负责掩码敏感字段(如密码、令牌)。
  ///
  /// @return 验证错误格式化器
  @Bean
  @ConditionalOnMissingBean
  public ValidationErrorsFormatter defaultValidationErrorsFormatter() {
    log.debug("正在注册默认的验证错误格式化器(ValidationErrorsFormatter),用于掩码敏感字段");
    return new DefaultValidationErrorsFormatter();
  }

  /// 创建问题详情构建器,将异常转换为符合 RFC 7807 标准的 ProblemDetail。
  ///
  /// 集成核心字段贡献者和 Web 字段贡献者,动态添加元数据字段。
  ///
  /// @param errorProperties 错误配置属性
  /// @param webProperties Web 错误配置属性
  /// @param traceProvider 追踪信息提供者
  /// @param coreFieldContributors 核心字段贡献者列表
  /// @param webFieldContributors Web 字段贡献者列表
  /// @return 问题详情构建器
  @Bean
  @ConditionalOnMissingBean
  public ProblemDetailBuilder problemDetailBuilder(
      ErrorProperties errorProperties,
      WebErrorProperties webProperties,
      TraceProvider traceProvider,
      List<ProblemFieldContributor> coreFieldContributors,
      List<WebProblemFieldContributor> webFieldContributors) {
    log.debug(
        "正在注册问题详情构建器(ProblemDetailBuilder),包含 {} 个核心贡献者和 {} 个 Web 贡献者",
        coreFieldContributors.size(),
        webFieldContributors.size());
    return new ProblemDetailBuilder(
        errorProperties, webProperties, traceProvider, coreFieldContributors, webFieldContributors);
  }

  /// 创建问题详情适配器,连接错误解析管道和问题详情构建器。
  ///
  /// @param pipeline 错误解析管道
  /// @param problemDetailBuilder 问题详情构建器
  /// @return 问题详情适配器
  @Bean
  @ConditionalOnMissingBean
  public ProblemDetailAdapter problemDetailAdapter(
      ErrorResolutionPipeline pipeline, ProblemDetailBuilder problemDetailBuilder) {
    log.debug("正在注册默认的问题详情适配器(ProblemDetailAdapter),用于异常到 ProblemDetail 的转换");
    return new DefaultProblemDetailAdapter(pipeline, problemDetailBuilder);
  }

  /// 创建全局 REST 异常处理器,统一处理所有 REST API 异常。
  ///
  /// 使用 `@RestControllerAdvice` 拦截异常并返回 RFC 7807 ProblemDetail 响应。
  ///
  /// @param problemDetailAdapter 问题详情适配器
  /// @param validationErrorsFormatter 验证错误格式化器
  /// @return 全局异常处理器
  @Bean
  @ConditionalOnMissingBean
  public GlobalRestExceptionHandler globalRestExceptionHandler(
      ProblemDetailAdapter problemDetailAdapter,
      ValidationErrorsFormatter validationErrorsFormatter) {
    log.debug("正在注册全局异常处理器(GlobalRestExceptionHandler),返回符合 RFC 7807 的 ProblemDetail 错误响应");
    return new GlobalRestExceptionHandler(problemDetailAdapter, validationErrorsFormatter);
  }
}
