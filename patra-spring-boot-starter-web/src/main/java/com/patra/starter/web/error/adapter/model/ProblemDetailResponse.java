package com.patra.starter.web.error.adapter.model;

import com.patra.starter.core.error.model.ErrorResolution;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** 已解析的 {@link ProblemDetail}、HTTP 状态和错误元数据的容器。 */
public record ProblemDetailResponse(
    ProblemDetail problemDetail, HttpStatus httpStatus, ErrorResolution errorResolution) {}
