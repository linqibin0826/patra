package com.patra.starter.core.error.spi;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * 错误码到 HTTP 状态码的映射策略 SPI 接口。
 *
 * <p>为避免在 core 模块引入 spring-web 依赖，此处返回 {@code int} 而非 {@code HttpStatus}。
 *
 * <p>典型实现：
 * - {@link com.patra.starter.core.error.strategy.SuffixHeuristicStatusMappingStrategy 基于后缀的启发式映射}
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.core.error.service.ErrorResolutionService 错误解析服务
 */
public interface StatusMappingStrategy {
    
    /**
     * 将给定错误码和异常映射为 HTTP 状态码。
     *
     * @param errorCode 业务错误码，不能为空
     * @param exception 触发错误的异常对象，可为空
     * @return HTTP 状态码（如 404、500）
     */
    int mapToHttpStatus(ErrorCodeLike errorCode, Throwable exception);
}
