package com.patra.starter.web.error.adapter;

import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.pipeline.ErrorResolutionPipeline;
import com.patra.starter.web.error.adapter.model.ProblemDetailResponse;
import com.patra.starter.web.error.builder.ProblemDetailBuilder;
import com.patra.starter.web.error.util.HttpStatusConverter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/** 由核心错误解析管道支持的默认 {@link ProblemDetailAdapter}。 */
@Slf4j
public class DefaultProblemDetailAdapter implements ProblemDetailAdapter {

  private final ErrorResolutionPipeline pipeline;
  private final ProblemDetailBuilder problemDetailBuilder;

  public DefaultProblemDetailAdapter(
      ErrorResolutionPipeline pipeline, ProblemDetailBuilder problemDetailBuilder) {
    this.pipeline = pipeline;
    this.problemDetailBuilder = problemDetailBuilder;
  }

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
