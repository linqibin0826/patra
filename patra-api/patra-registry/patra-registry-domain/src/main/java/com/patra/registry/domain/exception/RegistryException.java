package com.patra.registry.domain.exception;

import com.patra.common.error.DomainException;

/**
 * Registry 领域异常基类。
 *
 * <p>用于承载 Registry 领域内的业务规则错误，供应用层统一处理。
 * 所有 Registry 领域异常应继承本类以保持一致性并便于统一处理。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryException extends DomainException {

    /**
     * 使用消息构造异常。
     *
     * @param message 详情消息
     */
    protected RegistryException(String message) {
        super(message);
    }

    /**
     * 使用消息与原因构造异常。
     *
     * @param message 详情消息
     * @param cause   异常原因
     */
    protected RegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
