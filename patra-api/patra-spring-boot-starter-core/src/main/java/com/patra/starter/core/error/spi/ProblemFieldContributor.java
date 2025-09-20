package com.patra.starter.core.error.spi;

import java.util.Map;

/**
 * 为 {@link org.springframework.http.ProblemDetail} 响应扩展自定义字段的 SPI 接口。
 *
 * <p>核心版不依赖 Web 环境，实现类不应依赖 HttpServletRequest 等 Servlet API。
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.core.error.service.ErrorResolutionService
 */
public interface ProblemFieldContributor {
    
    /**
     * 向 ProblemDetail 响应添加自定义扩展字段。
     *
     * @param fields 可变字典，用于填充扩展字段，不能为空
     * @param exception 正在处理的异常，不能为空
     */
    void contribute(Map<String, Object> fields, Throwable exception);
}
