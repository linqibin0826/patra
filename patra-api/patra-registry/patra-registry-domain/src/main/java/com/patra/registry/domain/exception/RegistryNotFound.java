package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Registry 领域“未找到”语义的基类异常。
 *
 * <p>表示请求的资源（命名空间、目录、类型等）不存在，或因业务规则不可访问。
 * 所有“未找到”类异常应继承本类，以确保一致的错误特征（trait）归类与处理。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryNotFound extends RegistryException implements HasErrorTraits {
    
    /**
     * 使用消息构造异常。
     *
     * @param message 详情消息
     */
    protected RegistryNotFound(String message) {
        super(message);
    }
    
    /**
     * 使用消息与原因构造异常。
     *
     * @param message 详情消息
     * @param cause 异常原因
     */
    protected RegistryNotFound(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 返回该异常的错误特征集合（恒为 NOT_FOUND）。
     */
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.NOT_FOUND);
    }
}
