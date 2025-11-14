package com.patra.ingest.domain.model.vo.relay;

import com.patra.common.enums.ProvenanceCode;
import java.util.List;

/**
 * [待翻译] * Message payload for the {@code INGEST_LITERATURE_DATA_READY} channel.
 *
 * @param payload business payload
 * @param header message header metadata
 */
public record LiteratureReadyMessage(Payload payload, Header header) {

  /** Business payload delivered to catalog service. */
  public record Payload(
      Long taskId,
      Long runId,
      ProvenanceCode provenanceCode,
      List<String> storageKeys,
      Integer totalLiteratureCount,
      Integer successBatchCount,
      Integer failedBatchCount,
      Long timestamp) {}

  /** Header metadata for tracing. */
  public record Header(
      ProvenanceCode provenanceCode,
      Long taskId,
      Long runId,
      Integer storageKeyCount,
      Long occurredAt) {}
}
