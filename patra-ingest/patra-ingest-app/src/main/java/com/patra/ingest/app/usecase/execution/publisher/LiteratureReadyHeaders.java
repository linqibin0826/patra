package com.patra.ingest.app.usecase.execution.publisher;

import com.patra.ingest.domain.outbox.OutboxHeaders;

/**
 * Outbox headers for literature ready events.
 *
 * @param provenanceCode provenance code
 * @param taskId task identifier
 * @param runId execution run identifier
 * @param storageKeyCount number of storage keys carried in the payload
 * @param occurredAt epochMillis when the event was generated
 */
public record LiteratureReadyHeaders(
    String provenanceCode, Long taskId, Long runId, Integer storageKeyCount, Long occurredAt)
    implements OutboxHeaders {}
