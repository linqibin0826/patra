package com.patra.ingest.app.usecase.execution.batch.executor;

import com.patra.common.enums.ProvenanceCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Registry for BatchExecutor implementations.
 *
 * <p>Responsibility: manage all BatchExecutor instances and route by provenanceCode.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Auto-registration via Spring constructor injection.
 *   <li>Thread-safe using ConcurrentHashMap.
 *   <li>Throws IllegalArgumentException when executor not found.
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@Slf4j
public class BatchExecutorRegistry {

  private final Map<String, BatchExecutor> executors = new ConcurrentHashMap<>();

  /**
   * Constructor: auto-register all BatchExecutor instances.
   *
   * @param executorList all BatchExecutor instances injected by Spring
   */
  public BatchExecutorRegistry(List<BatchExecutor> executorList) {
    for (BatchExecutor executor : executorList) {
      ProvenanceCode provenanceCode = executor.getProvenanceCode();
      String code = provenanceCode.getCode();
      if (executors.containsKey(code)) {
        log.warn("duplicate batch executor for provenanceCode={}", code);
      }
      executors.put(code, executor);
      log.info(
          "registered batch executor provenanceCode={} class={}",
          code,
          executor.getClass().getSimpleName());
    }
  }

  /**
   * Gets the batch executor by provenance code.
   *
   * @param provenanceCode provenance code
   * @return batch executor
   * @throws IllegalArgumentException when executor is not found
   */
  public BatchExecutor get(String provenanceCode) {
    BatchExecutor executor = executors.get(provenanceCode);
    if (executor == null) {
      throw new IllegalArgumentException(
          "Batch executor not found for provenanceCode="
              + provenanceCode
              + "; available executors: "
              + executors.keySet());
    }
    return executor;
  }

  /** Checks whether an executor exists for the given provenanceCode. */
  public boolean contains(String provenanceCode) {
    return executors.containsKey(provenanceCode);
  }
}
