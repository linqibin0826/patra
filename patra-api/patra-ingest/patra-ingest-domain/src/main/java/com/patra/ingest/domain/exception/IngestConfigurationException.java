package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Ingest 配置异常。
 *
 * <p>当从 Registry 获取配置失败、配置缺失或配置不符合预期时抛出此异常。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class IngestConfigurationException extends IngestException implements HasErrorTraits {

    private final String provenanceCode;
    private final String operationCode;
    private final String endpointName;

    /**
     * 构造配置异常。
     *
     * @param provenanceCode 来源代码
     * @param operationCode  操作代码
     * @param endpointName   端点名称
     * @param message        异常消息
     */
    public IngestConfigurationException(String provenanceCode, String operationCode, String endpointName, String message) {
        super(message);
        this.provenanceCode = provenanceCode;
        this.operationCode = operationCode;
        this.endpointName = endpointName;
    }

    /**
     * 构造配置异常。
     *
     * @param provenanceCode 来源代码
     * @param operationCode  操作代码
     * @param endpointName   端点名称
     * @param message        异常消息
     * @param cause          原因异常
     */
    public IngestConfigurationException(String provenanceCode, String operationCode, String endpointName, String message, Throwable cause) {
        super(message, cause);
        this.provenanceCode = provenanceCode;
        this.operationCode = operationCode;
        this.endpointName = endpointName;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.RULE_VIOLATION);
    }

    /**
     * 获取来源代码。
     *
     * @return 来源代码
     */
    public String getProvenanceCode() {
        return provenanceCode;
    }

    /**
     * 获取操作代码。
     *
     * @return 操作代码
     */
    public String getOperationCode() {
        return operationCode;
    }

    /**
     * 获取端点名称。
     *
     * @return 端点名称
     */
    public String getEndpointName() {
        return endpointName;
    }
}