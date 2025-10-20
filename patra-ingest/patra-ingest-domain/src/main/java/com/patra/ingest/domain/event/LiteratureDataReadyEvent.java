package com.patra.ingest.domain.event;

import java.util.List;
import lombok.Builder;

/**
 * Domain event signalling that a task has produced literature data ready for downstream catalog
 * ingestion.
 *
 * <p>The event is persisted through the outbox and relayed to patra-catalog. The payload only
 * contains metadata; the actual literature documents are stored externally (e.g., object storage)
 * and referenced by {@link #storageKeys()}.
 */
@Builder
public record LiteratureDataReadyEvent(
    Long taskId,
    Long runId,
    String provenanceCode,
    List<String> storageKeys,
    Integer totalLiteratureCount,
    Integer successBatchCount,
    Integer failedBatchCount,
    Long timestamp) {}
