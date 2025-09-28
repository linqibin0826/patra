package com.patra.starter.web.error.adapter.model;

import com.patra.starter.core.error.model.ErrorResolution;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * ProblemDetail 适配结果，封装解析后的错误信息与 HTTP 状态。
 */
public record ProblemDetailResponse(
        ProblemDetail problemDetail,
        HttpStatus httpStatus,
        ErrorResolution errorResolution
) {
}
