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

    try {
      ProvenanceConfigResp resp = callRegistry(provenanceCode, operationType);
      return convertAndValidateResponse(resp, code);
    } catch (RemoteCallException ex) {
      return handleRemoteException(ex, code, operationType);
    } catch (Exception ex) {
      throw handleUnexpectedException(ex, code, operationType);
    }
  }

  /** Calls the registry service to retrieve configuration. */
  private ProvenanceConfigResp callRegistry(ProvenanceCode provenanceCode, String operationType) {
    long startTime = System.currentTimeMillis();
    ProvenanceConfigResp resp =
        provenanceClient.getConfiguration(provenanceCode, operationType, Instant.now());
    long duration = System.currentTimeMillis() - startTime;
    if (log.isDebugEnabled()) {
      log.debug(
          "Loaded provenance config for code [{}] operation [{}] in {}ms",
          provenanceCode.getCode(),
          operationType,
          duration);
    }
    return resp;
  }

  /** Converts and validates the registry response. */
  private ProvenanceConfigSnapshot convertAndValidateResponse(
      ProvenanceConfigResp resp, String code) {
    if (resp == null) {
      log.warn("Registry returned empty config, code={}", code);
      return createMinimalSnapshot(code);
    }

    return converter.convert(resp);
  }

  /** Handles unexpected exceptions during configuration retrieval. */
  private IngestConfigurationException handleUnexpectedException(
      Exception ex, String code, String operationType) {
    String msg =
        String.format(
            "Unexpected error when fetching config, code=%s, operationType=%s",
            code, operationType);
    log.error(msg, ex);
    return new IngestConfigurationException(code, operationType, msg, ex);
  }

  /** Handles remote ProblemDetail exceptions. */
  private ProvenanceConfigSnapshot handleRemoteException(
      RemoteCallException ex, String code, String operationType) {
    if (RemoteErrorHelper.isNotFound(ex)) {
      throw createConfigNotFoundException(ex, code, operationType);
    }
    if (RemoteErrorHelper.isServerError(ex) || RemoteErrorHelper.isRetryable(ex)) {
      return handleRegistryUnavailable(ex, code);
    }
    throw createClientErrorException(ex, code, operationType);
  }

  /** Creates exception for configuration not found errors. */
  private IngestConfigurationException createConfigNotFoundException(
      RemoteCallException ex, String code, String operationType) {
    String msg =
        String.format(
            "Provenance config not found, code=%s, operationType=%s", code, operationType);
    log.warn(
        "{} (remoteCode={}, status={}, traceId={})",
        msg,
        ex.getErrorCode(),
        ex.getHttpStatus(),
        ex.getTraceId());
    return new IngestConfigurationException(code, operationType, msg, ex);
  }

  /** Handles registry unavailability by returning minimal snapshot. */
  private ProvenanceConfigSnapshot handleRegistryUnavailable(RemoteCallException ex, String code) {
    log.warn(
        "Registry unavailable, fallback to minimal snapshot, code={}, status={}, traceId={}",
        code,
        ex.getHttpStatus(),
        ex.getTraceId());
    return createMinimalSnapshot(code);
  }

  /** Creates exception for client errors. */
  private IngestConfigurationException createClientErrorException(
      RemoteCallException ex, String code, String operationType) {
    String msg =
        String.format(
            "Registry client error for provenance [%s] operation [%s]: httpStatus=%d, errorCode=%s, traceId=%s",
            code, operationType, ex.getHttpStatus(), ex.getErrorCode(), ex.getTraceId());
    log.error(msg, ex);
    return new IngestConfigurationException(code, operationType, msg, ex);
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
