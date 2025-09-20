package com.patra.starter.core.error.spi;

import com.patra.common.error.codes.ErrorCodeLike;

import java.util.Optional;

/**
 * 提供细粒度错误码映射的 SPI 接口。
 *
 * <p>允许业务服务对特定异常覆盖默认的错误解析逻辑。
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.core.error.service.ErrorResolutionService
 */
public interface ErrorMappingContributor {
    
    /**
     * 为给定异常提供特定错误码映射。
     *
     * @param exception 待映射的异常，不能为空
     * @return 若本贡献者可处理该异常则返回对应错误码，否则返回空
     */
    Optional<ErrorCodeLike> mapException(Throwable exception);
}
