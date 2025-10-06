package com.patra.starter.provenance.common.config;

/**
 * Pagination configuration.
 *
 * <p>Field descriptions:
 * @param pageSizeValue default page size suggested to upstream APIs
 * @param maxPagesPerExecution maximum number of pages processed in one run
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PaginationConfig(
    Integer pageSizeValue,
    Integer maxPagesPerExecution
) {
}
