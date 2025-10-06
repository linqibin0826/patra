package com.patra.starter.provenance.common.config;

/**
 * Pagination configuration
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PaginationConfig(
    Integer pageSizeValue,
    Integer maxPagesPerExecution
) {
}
