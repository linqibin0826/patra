package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Exception thrown when ingestion configuration is invalid.
 *
 * <p>Trigger: raised when provenance or operation metadata cannot be loaded from Registry/config center, or the
 * payload violates schema constraints. Compared with {@link PlanValidationException}, this focuses on platform
 * configuration defects rather than runtime parameters.</p>
 * <p>Handling guidance:
 * <ul>
 *   <li>Temporary fetch failures (network/timeouts): retry a limited number of times at the application layer.</li>
 *   <li>Missing configuration: log at ERROR level, raise alerts, and request the data to be populated.</li>
 *   <li>Malformed configuration: stop execution and describe the offending field path and expected format.</li>
 * </ul>
 * </p>
 * <p>Observability: include {@code provenanceCode} / {@code endpointName} in logs to aid aggregation.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class IngestConfigurationException extends IngestException implements HasErrorTraits {

    /** Provenance code that identifies the upstream data source. */
    private final String provenanceCode;
    /** Operation code representing the business action or task type. */
    private final String operationCode;

    /**
     * Construct the exception without an underlying cause.
     * <p>Use when the configuration defect is detected immediately.</p>
     *
     * @param provenanceCode provenance code
     * @param operationCode  operation code
     * @param message        error message detailing the missing or invalid fields
     */
    public IngestConfigurationException(String provenanceCode, String operationCode, String message) {
        super(message);
        this.provenanceCode = provenanceCode;
        this.operationCode = operationCode;
    }

    /**
     * Construct the exception with an underlying cause.
     * <p>Use to wrap remote call failures, JSON parsing problems, or mapping conversions.</p>
     *
     * @param provenanceCode provenance code
     * @param operationCode  operation code
     * @param message        error message
     * @param cause          root cause
     */
    public IngestConfigurationException(String provenanceCode, String operationCode, String message, Throwable cause) {
        super(message, cause);
        this.provenanceCode = provenanceCode;
        this.operationCode = operationCode;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.RULE_VIOLATION);
    }

    /**
     * Expose the provenance code.
     * @return provenance code
     */
    public String getProvenanceCode() {
        return provenanceCode;
    }

    /**
     * Expose the operation code.
     * @return operation code
     */
    public String getOperationCode() {
        return operationCode;
    }
}
