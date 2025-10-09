package com.patra.ingest.domain.model.vo;

/**
 * Expression compilation request.
 * <p>
 * Contains the minimal information needed for expression compilation:
 * <ul>
 *   <li>provenanceCode - Data source identifier (e.g., PUBMED, EPMC)</li>
 *   <li>endpointName - Endpoint name (e.g., SEARCH, DETAIL). Optional, can be null.</li>
 *   <li>rawExpression - JSON expression snapshot to compile</li>
 * </ul>
 * </p>
 *
 * @param provenanceCode data source code (required)
 * @param endpointName endpoint name (optional, can be null)
 * @param rawExpression raw expression JSON string (required)
 * @author linqibin
 * @since 0.1.0
 */
public record ExprCompilationRequest(
        String provenanceCode,
        String endpointName,
        String rawExpression
) {
    /**
     * Convenience constructor without endpointName (defaults to null).
     *
     * @param provenanceCode data source code
     * @param rawExpression raw expression JSON string
     */
    public ExprCompilationRequest(String provenanceCode, String rawExpression) {
        this(provenanceCode, null, rawExpression);
    }
}
