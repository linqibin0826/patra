package com.patra.ingest.api.error;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Catalog of platform error codes for the ingest module.
 *
 * <p>All codes follow the {@code ING-NNNN} convention (ING context prefix). The 0xxx
 * range is reserved for HTTP-aligned errors via {@code HttpStdErrors.of("ING").*};
 * business-specific errors reside in the 1xxx range.</p>
 */
public enum IngestErrorCode implements ErrorCodeLike {

    // Note: HTTP-aligned 0xxx codes should be provided through HttpStdErrors.of("ING").*
    // factory methods and are intentionally omitted here.

    /** Registry configuration is missing or not registered. */
    ING_1201("ING-1201", 404),
    /** Registry returned invalid configuration data. */
    ING_1202("ING-1202", 422),
    /** Registry service unavailable; configuration fell back to degraded mode. */
    ING_1203("ING-1203", 503),
    /** Failed to persist outbox message. */
    ING_1301("ING-1301", 500),
    /** Failed to update outbox status. */
    ING_1302("ING-1302", 500),
    /** Failed to mark outbox message as dead-letter. */
    ING_1303("ING-1303", 500),
    /** Scheduler job parameters failed validation/parsing. */
    ING_1401("ING-1401", 422),
    /** Scheduler job execution failed. */
    ING_1402("ING-1402", 500),
    /** Plan assembly pre-validation failed. */
    ING_1403("ING-1403", 422),
    /** Failed to parse checkpoint payload. */
    ING_1501("ING-1501", 422),
    /** Failed to serialize checkpoint payload. */
    ING_1502("ING-1502", 422),
    /** Failed to persist plan and tasks. */
    ING_1503("ING-1503", 500),
    /** Plan assembly failed (slices/tasks were not produced). */
    ING_1601("ING-1601", 500);

    private final String code;
    private final int httpStatus;

    IngestErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String toString() {
        return code;
    }
}
