package com.patra.ingest.domain.model.vo.relay;

import com.patra.common.enums.ProvenanceCode;
import java.util.List;

/// [待翻译] * Message payload for the `INGEST_PUBLICATION_DATA_READY` channel.
///
/// @param payload business payload
/// @param header message header metadata
public record PublicationReadyMessage(Payload payload, Header header) {

  /// Business payload delivered to downstream services.
  public record Payload(
      Long taskId,
      Long runId,
      ProvenanceCode provenanceCode,
      List<String> storageKeys,
      Integer totalPublicationCount,
      Integer successBatchCount,
      Integer failedBatchCount,
      Long timestamp) {}

  /// Header metadata for tracing.
  public record Header(
      ProvenanceCode provenanceCode,
      Long taskId,
      Long runId,
      Integer storageKeyCount,
      Long occurredAt) {}
}
