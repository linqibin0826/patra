package com.patra.starter.provenance.common.exception;

/**
 * Provenance Client exception base class
 *
 * @author linqibin
 * @since 0.1.0
 */
public class ProvenanceClientException extends RuntimeException {

    private final String provenanceCode;
    private final String apiName;

    public ProvenanceClientException(String provenanceCode, String apiName, String message) {
        super(String.format("[%s][%s] %s", provenanceCode, apiName, message));
        this.provenanceCode = provenanceCode;
        this.apiName = apiName;
    }

    public ProvenanceClientException(String provenanceCode, String apiName, String message, Throwable cause) {
        super(String.format("[%s][%s] %s", provenanceCode, apiName, message), cause);
        this.provenanceCode = provenanceCode;
        this.apiName = apiName;
    }

    public String getProvenanceCode() {
        return provenanceCode;
    }

    public String getApiName() {
        return apiName;
    }
}
