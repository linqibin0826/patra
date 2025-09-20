package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Registry 配额/限流/容量超限语义的基类异常。
 *
 * <p>表示操作因超过配额、速率限制或容量约束而失败（如数量/大小超限）。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryQuotaExceeded extends RegistryException implements HasErrorTraits {
    
    /**
     * 使用消息构造异常。
     *
     * @param message 详情消息
     */
    protected RegistryQuotaExceeded(String message) {
        super(message);
    }
    
    /**
     * 使用消息与原因构造异常。
     *
     * @param message 详情消息
     * @param cause 异常原因
     */
    protected RegistryQuotaExceeded(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 返回该异常的错误特征集合（恒为 QUOTA_EXCEEDED）。
     */
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.QUOTA_EXCEEDED);
    }
}
