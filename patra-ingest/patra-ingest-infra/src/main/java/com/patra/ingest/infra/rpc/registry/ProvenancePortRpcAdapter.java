package com.patra.ingest.infra.rpc.registry;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.IngestConfigurationException;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.port.PatraRegistryPort;
import com.patra.ingest.infra.rpc.registry.converter.ProvenanceConfigSnapshotConverter;
import com.patra.registry.api.client.ProvenanceClient;
import com.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import com.patra.starter.feign.error.exception.RemoteCallException;
import com.patra.starter.feign.error.util.RemoteErrorHelper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbound adapter that calls patra-registry to fetch provenance configuration.
 *
 * <p>Follows hexagonal architecture: infrastructure component implementing an application port.
 * Retrieves a complete Provenance configuration snapshot from the Registry service with robust
 * error handling.
 *
 * <p>Highlights: - Comprehensive error handling and logging - Type-safe conversion using MapStruct
 * - Graceful degradation by returning a minimal usable snapshot
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvenancePortRpcAdapter implements PatraRegistryPort {

  /** Registry RPC client. */
  private final ProvenanceClient provenanceClient;

  /** Converter for configuration snapshots. */
  private final ProvenanceConfigSnapshotConverter converter;

  /** Calls the Registry to fetch provenance configuration. */
  @Override
  public ProvenanceConfigSnapshot fetchConfig(
      ProvenanceCode provenanceCode, OperationCode operationCode) {
    String code = provenanceCode.getCode();
    String operationType = operationCode.name();
    Instant queryTime = Instant.now();

    log.debug(
        "Requesting provenance config, code={}, operationType={}, at={}",
        code,
        operationType,
        queryTime);

    try {
      ProvenanceConfigResp resp =
          provenanceClient.getConfiguration(provenanceCode, operationType, queryTime);

      if (resp == null) {
        log.warn(
            "Registry returned empty config, code={}, operationType={}",
            code,
            operationType);
        return createMinimalSnapshot(code);
      }

      ProvenanceConfigSnapshot snapshot = converter.convert(resp);

      // Log hash info of conversion result for diagnosis (snapshot itself includes toString)
      log.debug("Provenance config loaded, code={}, snapshot={}", code, snapshot);
      return snapshot;

    } catch (RemoteCallException ex) {
      return handleRemoteException(ex, code, operationType);
    } catch (Exception ex) {
      String msg =
          String.format(
              "Unexpected error when fetching config, code=%s, operationType=%s",
              code, operationType);
      log.error("" + msg, ex);
      throw new IngestConfigurationException(code, operationType, msg, ex);
    }
  }

  /** Handles remote ProblemDetail exceptions. */
  private ProvenanceConfigSnapshot handleRemoteException(
      RemoteCallException ex, String code, String operationType) {
    if (RemoteErrorHelper.isNotFound(ex)) {
      String msg =
          String.format(
              "Provenance config not found, code=%s, operationType=%s", code, operationType);
      log.warn(
          "{} (remoteCode={}, status={}, traceId={})",
          msg,
          ex.getErrorCode(),
          ex.getHttpStatus(),
          ex.getTraceId());
      throw new IngestConfigurationException(code, operationType, msg, ex);
    }

    if (RemoteErrorHelper.isServerError(ex) || RemoteErrorHelper.isRetryable(ex)) {
      log.warn(
          "Registry unavailable, fallback to minimal snapshot, code={}, status={}, traceId={}",
          code,
          ex.getHttpStatus(),
          ex.getTraceId());
      return createMinimalSnapshot(code);
    }

    String msg =
        "Registry client error"
            + String.format(
                ", code=%s, status=%d, remoteCode=%s, traceId=%s, detail=%s",
                code, ex.getHttpStatus(), ex.getErrorCode(), ex.getTraceId(), ex.getMessage());
    log.error("" + msg);
    throw new IngestConfigurationException(code, operationType, msg, ex);
  }

  /** Creates a minimal usable configuration snapshot. */
  private ProvenanceConfigSnapshot createMinimalSnapshot(String provenanceCode) {
    log.info("Creating minimal provenance snapshot, code={}", provenanceCode);

    // Create a minimal ProvenanceInfo
    ProvenanceConfigSnapshot.ProvenanceInfo minimalProvenance =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            null, // id
            provenanceCode, // code
            null, // name
            null, // baseUrlDefault
            null, // timezoneDefault
            null, // docsUrl
            true, // active
            null // lifecycleStatusCode
            );

    return new ProvenanceConfigSnapshot(
        minimalProvenance,
        null, // windowOffset
        null, // pagination
        null, // http
        null, // batching
        null, // retry
        null // rateLimit
        );
  }
}
