package com.patra.ingest.domain.model.vo;

/**
 * Expression compilation request.
 * <p>
 * Contains the minimal information needed for expression compilation:
 * <ul>
 *   <li>provenanceCode - Data source identifier (e.g., PUBMED, EPMC)</li>
 *   <li>operationCode - Operation identifier (e.g., SEARCH, DETAIL)</li>
 *   <li>rawExpression - JSON expression snapshot to compile</li>
 * </ul>
 * </p>
 *
 * @param provenanceCode data source code
 * @param operationCode operation code
 * @param rawExpression raw expression JSON string
 * @author linqibin
 * @since 0.1.0
 */
public record ExprCompilationRequest(
        String provenanceCode,
        String operationCode,
        String rawExpression
) {
}
