package dev.patra.tool.web.error.adapter.model;

import dev.patra.tool.web.error.model.ErrorResolution;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/// 已解析的 {@link ProblemDetail}、HTTP 状态和错误元数据的容器。
public record ProblemDetailResponse(
    ProblemDetail problemDetail, HttpStatus httpStatus, ErrorResolution errorResolution) {}
