package com.patra.starter.provenance.common.exception;

import java.util.Objects;

/**
 * Provenance client exception base class.
 *
 * <p>Wraps downstream errors occurring while calling provenance data sources
 * through the egress gateway. Additional metadata (HTTP status, traceId,
 * response body) is captured to assist troubleshooting.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class ProvenanceClientException extends RuntimeException {

    private final String provenanceCode;
    private final String apiName;
    private final Integer statusCode;
    private final String traceId;
    private final String responseBody;

    public ProvenanceClientException(String provenanceCode, String apiName, String message) {
        this(provenanceCode, apiName, null, null, null, message, null);
    }

    public ProvenanceClientException(String provenanceCode, String apiName, String message, Throwable cause) {
        this(provenanceCode, apiName, null, null, null, message, cause);
    }

    public ProvenanceClientException(
        String provenanceCode,
        String apiName,
        Integer statusCode,
        String traceId,
        String responseBody,
        String message,
        Throwable cause
    ) {
        super(formatMessage(provenanceCode, apiName, statusCode, traceId, message), cause);
        this.provenanceCode = Objects.requireNonNull(provenanceCode, "provenanceCode cannot be null");
        this.apiName = Objects.requireNonNull(apiName, "apiName cannot be null");
        this.statusCode = statusCode;
        this.traceId = traceId;
        this.responseBody = responseBody;
    }

    private static String formatMessage(String provenanceCode, String apiName, Integer statusCode, String traceId, String message) {
        StringBuilder builder = new StringBuilder("[")
            .append(provenanceCode)
            .append("][")
            .append(apiName)
            .append("] ");
        if (statusCode != null) {
            builder.append("status=").append(statusCode).append(' ');
        }
        if (traceId != null) {
            builder.append("traceId=").append(traceId).append(' ');
        }
        builder.append(message);
        return builder.toString();
    }

    public String getProvenanceCode() {
        return provenanceCode;
    }

    public String getApiName() {
        return apiName;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
