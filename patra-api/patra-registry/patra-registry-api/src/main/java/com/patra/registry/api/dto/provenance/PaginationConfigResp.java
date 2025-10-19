package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/**
 * Response DTO describing pagination and cursor traversal configuration.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>id - primary identifier of the pagination configuration row
 *   <li>provenanceId - provenance owning the configuration
 *   <li>operationType - operation discriminator (e.g., HARVEST/UPDATE)
 *   <li>effectiveFrom - timestamp from which the configuration becomes effective
 *   <li>effectiveTo - timestamp until which the configuration remains effective
 *   <li>paginationModeCode - pagination mode (PAGE_NUMBER/CURSOR/TOKEN/SCROLL)
 *   <li>pageSizeValue - default page size requested per call
 *   <li>maxPagesPerExecution - safety cap on pages fetched per execution
 *   <li>sortFieldParamName - request parameter name carrying sort field
 *   <li>sortingDirection - sorting direction indicator (1=ASC, 0=DESC)
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
    Integer sortingDirection) {}
