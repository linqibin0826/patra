package dev.linqibin.starter.web.error.adapter;

import dev.linqibin.starter.core.error.model.ErrorResolution;
import dev.linqibin.starter.core.error.pipeline.ErrorResolutionPipeline;
import dev.linqibin.starter.web.error.adapter.model.ProblemDetailResponse;
import dev.linqibin.starter.web.error.builder.ProblemDetailBuilder;
import dev.linqibin.starter.web.error.util.HttpStatusConverter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/// 由核心错误解析管道支持的默认 {@link ProblemDetailAdapter}。
@Slf4j
public class DefaultProblemDetailAdapter implements ProblemDetailAdapter {

  private final ErrorResolutionPipeline pipeline;
  private final ProblemDetailBuilder problemDetailBuilder;

  /// 构造默认问题详情适配器实例。
  ///
  /// @param pipeline 错误解析管道
  /// @param problemDetailBuilder 问题详情构建器
  public DefaultProblemDetailAdapter(
      ErrorResolutionPipeline pipeline, ProblemDetailBuilder problemDetailBuilder) {
    this.pipeline = pipeline;
    this.problemDetailBuilder = problemDetailBuilder;
  }

  /// 将异常转换为符合 RFC 7807 标准的问题详情响应。
  ///
  /// @param exception 待适配的异常
  /// @param request HTTP 请求上下文
  /// @return 包含问题详情、HTTP 状态和错误解析信息的响应
  @Override
  public ProblemDetailResponse adapt(Throwable exception, HttpServletRequest request) {
    ErrorResolution resolution = pipeline.resolve(exception);
    HttpStatus httpStatus = HttpStatusConverter.toHttpStatus(resolution.httpStatus());

    log.debug(
        "Adapted exception [{}] to ProblemDetail with HTTP status {} and error code [{}]",
        exception == null ? "null" : exception.getClass().getSimpleName(),
        httpStatus.value(),
        resolution.errorCode().code());

    return new ProblemDetailResponse(
        problemDetailBuilder.build(resolution, exception, request), httpStatus, resolution);
  }
}
