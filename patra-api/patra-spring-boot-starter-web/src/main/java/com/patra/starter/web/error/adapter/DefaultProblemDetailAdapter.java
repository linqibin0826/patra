package com.patra.starter.web.error.adapter;

import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.pipeline.ErrorResolutionPipeline;
import com.patra.starter.web.error.adapter.model.ProblemDetailResponse;
import com.patra.starter.web.error.builder.ProblemDetailBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/**
 * 默认 ProblemDetail 适配器，基于核心错误解析管线输出统一错误响应。
 */
@Slf4j
public class DefaultProblemDetailAdapter implements ProblemDetailAdapter {

    private final ErrorResolutionPipeline pipeline;
    private final ProblemDetailBuilder problemDetailBuilder;

    public DefaultProblemDetailAdapter(ErrorResolutionPipeline pipeline,
                                       ProblemDetailBuilder problemDetailBuilder) {
        this.pipeline = pipeline;
        this.problemDetailBuilder = problemDetailBuilder;
    }

    @Override
    public ProblemDetailResponse adapt(Throwable exception, HttpServletRequest request) {
        ErrorResolution resolution = pipeline.resolve(exception);
        HttpStatus httpStatus = safeHttpStatus(resolution.httpStatus());
        log.debug("ProblemDetail 适配：exception={} status={} errorCode={}",
                exception == null ? "null" : exception.getClass().getSimpleName(),
                httpStatus.value(), resolution.errorCode().code());

        return new ProblemDetailResponse(
                problemDetailBuilder.build(resolution, exception, request),
                httpStatus,
                resolution
        );
    }

    private HttpStatus safeHttpStatus(int status) {
        try {
            return HttpStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            log.warn("解析得到的 HTTP 状态码非法: {}，回退 500", status);
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
