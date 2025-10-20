package com.patra.ingest.app.usecase.execution.complete.publisher;

import com.patra.ingest.domain.outbox.OutboxPayload;
import java.util.List;
import java.util.Objects;

/**
 * Outbox payload for literature ready events.
 *
 * @param taskId task identifier
 * @param runId execution run identifier
 * @param provenanceCode provenance code (PUBMED/EPMC/etc.)
 * @param storageKeys storage locations in object storage
 * @param totalLiteratureCount total literature items persisted
 * @param successBatchCount number of succeeded batches
 * @param failedBatchCount number of failed batches
 * @param timestamp event creation epochMillis
 */
public record LiteratureReadyPayload(
    Long taskId,
    Long runId,
    String provenanceCode,
    List<String> storageKeys,
    Integer totalLiteratureCount,
    Integer successBatchCount,
    Integer failedBatchCount,
    Long timestamp)
    implements OutboxPayload {

  public LiteratureReadyPayload {
    Objects.requireNonNull(taskId, "taskId must not be null");
    Objects.requireNonNull(runId, "runId must not be null");
    storageKeys = storageKeys == null ? List.of() : List.copyOf(storageKeys);
  }
}
