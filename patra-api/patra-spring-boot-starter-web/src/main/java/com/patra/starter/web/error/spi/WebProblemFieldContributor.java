package com.patra.starter.web.error.spi;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Web 端为 {@link org.springframework.http.ProblemDetail} 提供扩展字段的 SPI 接口。
 *
 * <p>提供 {@link jakarta.servlet.http.HttpServletRequest}，便于提取与请求相关的上下文字段。</p>
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.web.error.builder.ProblemDetailBuilder
 */
public interface WebProblemFieldContributor {
    
    /**
     * 贡献 ProblemDetail 扩展字段（包含 Web 上下文）。
     *
     * @param fields 可变字典
     * @param exception 当前异常
     * @param request HTTP 请求
     */
    void contribute(Map<String, Object> fields, Throwable exception, HttpServletRequest request);
}
