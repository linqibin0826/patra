package com.patra.registry.domain.exception.provenance;

import com.patra.registry.domain.exception.RegistryNotFound;

/**
 * 来源（Provenance）未找到异常。
 *
 * <p>当请求的来源不存在，或该来源在当前上下文不可用时抛出，
 * 由平台统一错误解析映射为 404（REG-0404）。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class ProvenanceNotFoundException extends RegistryNotFound {

    /**
     * 使用详情消息构造异常。
     *
     * @param message 详情消息
     */
    public ProvenanceNotFoundException(String message) {
        super(message);
    }

    /**
     * 使用详情消息与原因构造异常。
     *
     * @param message 详情消息
     * @param cause   异常原因
     */
    public ProvenanceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
