package com.patra.starter.web.error.adapter;

import com.patra.starter.web.error.adapter.model.ProblemDetailResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * ProblemDetail 适配器接口，用于将异常转换为统一的 ProblemDetail 响应。
 */
public interface ProblemDetailAdapter {

    /**
     * 将异常适配为 ProblemDetail 响应。
     *
     * @param exception 当前异常
     * @param request HTTP 请求，允许为空（例如非 Servlet 场景）
     * @return ProblemDetail 适配结果
     */
    ProblemDetailResponse adapt(Throwable exception, HttpServletRequest request);
}
