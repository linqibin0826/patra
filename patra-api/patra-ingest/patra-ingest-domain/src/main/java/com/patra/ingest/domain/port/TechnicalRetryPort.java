package com.patra.ingest.domain.port;

import java.util.Map;
import lombok.Builder;

/**
 * Technical retry port for persisting failed operations to Outbox.
 *
 * <p>Infrastructure adapters use this port to delegate retry logic instead of directly manipulating
 * OutboxMessageRepository. This ensures consistent handling of technical failures through the
 * Outbox pattern with unified metrics, logging, and batch processing.
 *
 * <h3>Design Rationale</h3>
 *
 * <ul>
 *   <li><b>Separation of Concerns</b>: Infrastructure layer focuses on technical operations (RPC,
 *       I/O), delegating retry orchestration to application layer
 *   <li><b>Framework Consistency</b>: All Outbox operations go through AbstractOutboxPublisher for
 *       uniform behavior
 *   <li><b>Dependency Inversion</b>: Infra depends on domain port, app implements port, maintaining
 *       correct dependency direction
 * </ul>
 *
 * <h3>Usage Example</h3>
 *
 * <pre>{@code
 * @Component
 * public class ExternalServiceAdapter {
 *   private final TechnicalRetryPort retryPort;
 *
 *   public void callExternalService(Request request) {
 *     try {
 *       externalClient.call(request);
 *     } catch (Exception e) {
 *       // Delegate to retry publisher instead of direct Outbox manipulation
 *       retryPort.publishRetry(
 *         RetryContext.builder()
 *           .operationType("EXTERNAL_SERVICE_CALL")
 *           .aggregateId(request.getId())
 *           .payload(serializeRequest(request))
 *           .metadata(Map.of("traceId", MDC.get("traceId")))
 *           .build()
 *       );
 *     }
 *   }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TechnicalRetryPort {

  /**
   * Publishes a technical retry request to the Outbox for asynchronous processing.
   *
   * <p>The retry request will be persisted and eventually processed by the Outbox relay mechanism.
   *
   * @param context retry context containing operation details
   */
  void publishRetry(RetryContext context);

  /**
   * Technical retry context encapsulating failed operation details.
   *
   * @param operationType operation type identifier (e.g., "METADATA_RECORD", "RPC_CALL")
   * @param aggregateId aggregate identifier for partitioning and correlation
   * @param payload serialized operation payload (JSON recommended)
   * @param metadata additional metadata for headers (traceId, provenanceCode, etc.)
   */
  @Builder
  record RetryContext(
      String operationType, Long aggregateId, String payload, Map<String, Object> metadata) {}
}
