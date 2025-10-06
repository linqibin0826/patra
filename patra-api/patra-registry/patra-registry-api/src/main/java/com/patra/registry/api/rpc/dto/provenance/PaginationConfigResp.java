package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * Response DTO describing pagination and cursor traversal configuration.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>id - primary identifier of the pagination configuration row</li>
 *   <li>provenanceId - provenance owning the configuration</li>
 *   <li>operationType - operation discriminator (e.g., HARVEST/UPDATE)</li>
 *   <li>effectiveFrom - timestamp from which the configuration becomes effective</li>
 *   <li>effectiveTo - timestamp until which the configuration remains effective</li>
 *   <li>paginationModeCode - pagination mode (PAGE_NUMBER/CURSOR/TOKEN/SCROLL)</li>
 *   <li>pageSizeValue - default page size requested per call</li>
 *   <li>maxPagesPerExecution - safety cap on pages fetched per execution</li>
 *   <li>sortFieldParamName - request parameter name carrying sort field</li>
 *   <li>sortingDirection - sorting direction indicator (1=ASC, 0=DESC)</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PaginationConfigResp(
        Long id,
        Long provenanceId,
        String operationType,
        Instant effectiveFrom,
        Instant effectiveTo,
        String paginationModeCode,
        Integer pageSizeValue,
        Integer maxPagesPerExecution,
        String sortFieldParamName,
        Integer sortingDirection
) {
}
