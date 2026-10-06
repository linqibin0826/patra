package dev.linqibin.starter.web.error.builder;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.commons.error.field.HasFieldViolations;
import dev.linqibin.commons.error.problem.ErrorKeys;
import dev.linqibin.commons.error.retry.HasRetryAfter;
import dev.linqibin.commons.error.trait.ErrorTrait;
import dev.linqibin.commons.error.trait.HasErrorTraits;
import dev.linqibin.starter.core.error.config.ErrorProperties;
import dev.linqibin.starter.core.error.model.ErrorResolution;
import dev.linqibin.starter.core.error.spi.ProblemFieldContributor;
import dev.linqibin.starter.core.error.spi.TraceProvider;
import dev.linqibin.starter.web.error.config.WebErrorProperties;
import dev.linqibin.starter.web.error.model.ValidationError;
import dev.linqibin.starter.web.error.spi.WebProblemFieldContributor;
import dev.linqibin.starter.web.error.util.HttpStatusConverter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * RFC 7807 {@link org.springframework.http.ProblemDetail} 响应的构建器。
 *
 * <p>功能包括:
 *
 * <ul>
 *   <li>在信息离开服务之前对敏感信息进行掩码。
 *   <li>代理感知的路径提取(Forwarded / X-Forwarded-* 头)。
 *   <li>支持核心和 Web 特定的扩展字段贡献器。
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 * @see dev.linqibin.starter.web.error.config.WebErrorAutoConfiguration
 */
@Slf4j
public class ProblemDetailBuilder {

  @SuppressWarnings("unused")
  private final ErrorProperties errorProperties;

  private final WebErrorProperties webProperties;
  private final TraceProvider traceProvider;
  private final List<ProblemFieldContributor> coreFieldContributors;
  private final List<WebProblemFieldContributor> webFieldContributors;

  /**
   * 构造函数,初始化 ProblemDetail 构建器。
   *
   * @param errorProperties 错误配置属性
   * @param webProperties Web 错误配置属性
   * @param traceProvider 跟踪 ID 提供者
   * @param coreFieldContributors 核心字段贡献器列表
   * @param webFieldContributors Web 字段贡献器列表
   */
  public ProblemDetailBuilder(
      ErrorProperties errorProperties,
      WebErrorProperties webProperties,
      TraceProvider traceProvider,
      List<ProblemFieldContributor> coreFieldContributors,
      List<WebProblemFieldContributor> webFieldContributors) {
    this.errorProperties = errorProperties;
    this.webProperties = webProperties;
    this.traceProvider = traceProvider;
    this.coreFieldContributors = coreFieldContributors;
    this.webFieldContributors = webFieldContributors;
  }

  /**
   * 从已解析的错误元数据和 HTTP 上下文构建 {@link ProblemDetail} 实例。
   *
   * @param resolution 包含错误码和状态的错误解析结果
   * @param exception 正在处理的异常
   * @param request 与失败关联的 HTTP 请求
   * @return 完全填充的 {@link ProblemDetail}
   */
  public ProblemDetail build(
      ErrorResolution resolution, Throwable exception, HttpServletRequest request) {
    log.debug(
        "为错误 [{}] 构建 ProblemDetail,HTTP 状态 {}",
        resolution.errorCode().code(),
        resolution.httpStatus());

    HttpStatus httpStatus = HttpStatusConverter.toHttpStatus(resolution.httpStatus());
    ProblemDetail problemDetail = ProblemDetail.forStatus(httpStatus);

    setupStandardFields(problemDetail, resolution, exception, request);
    addFieldViolationsIfPresent(problemDetail, exception);
    addRetryAfterIfPresent(problemDetail, exception);
    addTraceIdIfAvailable(problemDetail);
    contributeCoreProblemFields(problemDetail, exception);
    contributeWebProblemFields(problemDetail, exception, request);

    log.debug(
        "ProblemDetail 构建成功: type={}, code={}",
        problemDetail.getType(),
        resolution.errorCode().code());

    return problemDetail;
  }

  /**
   * 填充标准 RFC 7807 字段和通用扩展属性。
   *
   * @param problemDetail 目标问题详情实例
   * @param resolution 错误解析元数据
   * @param exception 源异常
   * @param request HTTP 请求上下文
   */
  private void setupStandardFields(
      ProblemDetail problemDetail,
      ErrorResolution resolution,
      Throwable exception,
      HttpServletRequest request) {
    problemDetail.setType(buildTypeUri(resolution.errorCode()));
    problemDetail.setTitle(resolution.errorCode().code());
    problemDetail.setDetail(maskSensitiveData(exception.getMessage()));

    problemDetail.setProperty(ErrorKeys.CODE, resolution.errorCode().code());
    problemDetail.setProperty(ErrorKeys.PATH, extractPath(request));
    problemDetail.setProperty(
        ErrorKeys.TIMESTAMP, Instant.now().atOffset(ZoneOffset.UTC).toString());

    // 输出错误语义特征（用于服务间错误传播）
    addErrorTraitsIfPresent(problemDetail, exception);
  }

  /// 当异常实现 HasErrorTraits 接口时,将错误特征添加到 ProblemDetail 中。
  ///
  /// 这使得下游服务能够识别上游错误的语义类型（如 NOT_FOUND、CONFLICT 等）,
  /// 而无需仅依赖 HTTP 状态码判断。
  ///
  /// @param problemDetail 目标问题详情实例
  /// @param exception 源异常
  private void addErrorTraitsIfPresent(ProblemDetail problemDetail, Throwable exception) {
    if (exception instanceof HasErrorTraits hasTraits) {
      Set<ErrorTrait> traits = hasTraits.getErrorTraits();
      if (traits != null && !traits.isEmpty()) {
        List<String> traitNames = traits.stream().map(ErrorTrait::name).toList();
        problemDetail.setProperty(ErrorKeys.TRAITS, traitNames);
        log.debug("已将错误特征 {} 添加到 ProblemDetail", traitNames);
      }
    }
  }

  /// 异常带字段错误时，输出 `errors[]`。领域层报的错误不回显用户填的值。
  ///
  /// @param problemDetail 目标问题详情实例
  /// @param exception 源异常
  private void addFieldViolationsIfPresent(ProblemDetail problemDetail, Throwable exception) {
    if (exception instanceof HasFieldViolations hasViolations) {
      List<ValidationError> errors =
          hasViolations.getFieldViolations().stream().map(this::toValidationError).toList();
      problemDetail.setProperty(ErrorKeys.ERRORS, errors);
    }
  }

  /// 把领域层的字段错误转成响应里的验证错误条目。
  ///
  /// @param violation 字段错误
  /// @return 验证错误条目，`rejectedValue` 为 `null`
  private ValidationError toValidationError(FieldViolation violation) {
    return new ValidationError(violation.field(), violation.code(), null, violation.message());
  }

  /// 异常带剩余等待时间时，输出 `retryAfterSeconds`。
  ///
  /// 存成 `Integer`：starter-core 的 Jackson 配置把 `Long` 一律序列化成字符串（防雪花 ID 丢精度），
  /// 这个字段要以 JSON 数字输出。超过 `Integer.MAX_VALUE` 秒时取上限。
  ///
  /// @param problemDetail 目标问题详情实例
  /// @param exception 源异常
  private void addRetryAfterIfPresent(ProblemDetail problemDetail, Throwable exception) {
    if (exception instanceof HasRetryAfter hasRetryAfter) {
      long seconds = hasRetryAfter.getRetryAfterSeconds();
      problemDetail.setProperty(
          ErrorKeys.RETRY_AFTER_SECONDS, (int) Math.min(seconds, Integer.MAX_VALUE));
    }
  }

  /**
   * 当跟踪提供者可用时,将跟踪 ID 添加到问题详情中。
   *
   * @param problemDetail 目标问题详情实例
   */
  private void addTraceIdIfAvailable(ProblemDetail problemDetail) {
    traceProvider
        .getCurrentTraceId()
        .ifPresent(
            traceId -> {
              log.debug("将分布式跟踪 ID [{}] 添加到 ProblemDetail", traceId);
              problemDetail.setProperty(ErrorKeys.TRACE_ID, traceId);
            });
  }

  /**
   * 调用核心字段贡献器以添加平台范围的扩展字段。
   *
   * @param problemDetail 目标问题详情实例
   * @param exception 源异常
   */
  private void contributeCoreProblemFields(ProblemDetail problemDetail, Throwable exception) {
    Map<String, Object> coreFields = new HashMap<>();
    coreFieldContributors.forEach(
        contributor -> {
          try {
            contributor.contribute(coreFields, exception);
          } catch (Exception e) {
            log.warn(
                "核心字段贡献器 [{}] 失败,错误: {}", contributor.getClass().getSimpleName(), e.getMessage());
          }
        });
    coreFields.forEach(problemDetail::setProperty);
  }

  /**
   * 调用 Web 特定的字段贡献器以添加 HTTP 上下文扩展字段。
   *
   * @param problemDetail 目标问题详情实例
   * @param exception 源异常
   * @param request HTTP 请求上下文
   */
  private void contributeWebProblemFields(
      ProblemDetail problemDetail, Throwable exception, HttpServletRequest request) {
    Map<String, Object> webFields = new HashMap<>();
    webFieldContributors.forEach(
        contributor -> {
          try {
            contributor.contribute(webFields, exception, request);
          } catch (Exception e) {
            log.warn(
                "Web 字段贡献器 [{}] 失败,错误: {}", contributor.getClass().getSimpleName(), e.getMessage());
          }
        });
    webFields.forEach(problemDetail::setProperty);
  }

  /**
   * 以代理感知方式提取请求路径,优先级为: Forwarded 头, X-Forwarded-* 头,然后是 requestURI。
   *
   * @param request HTTP 请求上下文
   * @return 解析的路径值
   */
  private String extractPath(HttpServletRequest request) {
    String forwarded = request.getHeader("Forwarded");
    if (forwarded != null && !forwarded.isEmpty()) {
      String path = parseForwardedPath(forwarded);
      if (path != null) {
        return path;
      }
    }

    String forwardedPath = request.getHeader("X-Forwarded-Path");
    if (forwardedPath != null && !forwardedPath.isEmpty()) {
      return forwardedPath;
    }

    String forwardedUri = request.getHeader("X-Forwarded-Uri");
    if (forwardedUri != null && !forwardedUri.isEmpty()) {
      return forwardedUri;
    }

    String requestUri = request.getRequestURI();
    if (log.isDebugEnabled()) {
      log.debug("解析的请求路径: {}", requestUri);
    }
    return requestUri;
  }

  /**
   * 从 RFC 7239 Forwarded 头值中解析路径属性。
   *
   * @param forwarded 头值
   * @return 提取的路径,不存在时返回 {@code null}
   */
  private String parseForwardedPath(String forwarded) {
    String[] parts = forwarded.split(";");
    for (String part : parts) {
      String trimmed = part.trim();
      if (trimmed.startsWith("path=")) {
        return trimmed.substring(5).replaceAll("^\"|\"$", "");
      }
    }
    return null;
  }

  /**
   * 对错误消息中的敏感键值对应用轻量级掩码。
   *
   * @param message 原始详细消息
   * @return 存在敏感令牌时的已掩码消息
   */
  private String maskSensitiveData(String message) {
    if (message == null) {
      return null;
    }

    return message
        .replaceAll("(?i)(password|token|secret|key)=[^\\s,}]+", "$1=***")
        .replaceAll("(?i)(password|token|secret|key)\":\\s*\"[^\"]+\"", "$1\":\"***\"");
  }

  /**
   * 从逻辑错误码构建 ProblemDetail 类型 URI。
   *
   * @param errorCode 平台错误码抽象
   * @return 完全限定的类型 URI
   */
  private URI buildTypeUri(ErrorCodeLike errorCode) {
    String baseUrl = webProperties.getTypeBaseUrl();
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    return URI.create(baseUrl + errorCode.code().toLowerCase(Locale.ROOT));
  }
}
