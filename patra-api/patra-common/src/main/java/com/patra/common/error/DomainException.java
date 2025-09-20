package com.patra.common.error;

/**
 * 领域异常基类。
 *
 * <p>为领域层异常提供与框架无关的基类，不依赖 Spring 等框架。</p>
 * <p>领域异常应继承本类，以保持领域逻辑与技术细节的清晰分离。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class DomainException extends RuntimeException {
    
    /** 构造函数（消息）。 */
    protected DomainException(String message) {
        super(message);
    }
    
    /** 构造函数（消息 + 原因）。 */
    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
