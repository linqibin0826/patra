package com.patra.ingest.domain.port;

import com.patra.common.model.StandardLiterature;
import java.util.List;
import lombok.Builder;

/**
 * Output port responsible for publishing standardized literature payloads outside the ingest
 * bounded context.
 *
 * <p>Infrastructure adapters implement this interface to serialize and persist literature records
 * (object storage, MQ, etc.). Application layer orchestrators remain unaware of the concrete
 * transport and storage details.
 */
public interface LiteraturePublisherPort {

  /**
   * Publishes a batch of standardized literature.
   *
   * @param literature domain-normalized literature list
   * @param context execution context metadata for traceability
   * @return publish result containing storage hints
   */
  PublishResult publish(List<StandardLiterature> literature, PublishContext context);

  /**
   * Publish result.
   *
   * @param storageKey object storage identifier or similar opaque handle
   * @param publishedCount number of literature items persisted
   */
  @Builder
  record PublishResult(String storageKey, int publishedCount) {}

  /**
   * Publish contextual metadata provided by the application layer.
   *
   * @param runId task run identifier
   * @param batchNo execution batch number
   * @param provenanceCode normalized source identifier
   */
  @Builder
  record PublishContext(Long runId, int batchNo, String provenanceCode) {}
}
