package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Registry 资源冲突（Conflict）语义的基类异常。
 *
 * <p>表示因与现有资源或规则冲突（如重名、版本冲突）而无法完成操作。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryConflict extends RegistryException implements HasErrorTraits {
    
    /**
     * 使用消息构造异常。
     *
     * @param message 详情消息
     */
    protected RegistryConflict(String message) {
        super(message);
    }
    
    /**
     * 使用消息与原因构造异常。
     *
     * @param message 详情消息
     * @param cause 异常原因
     */
    protected RegistryConflict(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 返回该异常的错误特征集合（恒为 CONFLICT）。
     */
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.CONFLICT);
    }
}
