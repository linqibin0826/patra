package com.patra.ingest.app.usecase.execution.batch.planner;

import com.patra.common.enums.ProvenanceCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Registry for BatchPlanner implementations.
 *
 * <p>Responsibility: manage all BatchPlanner instances and route by provenanceCode.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Auto-registration via Spring constructor injection.
 *   <li>Thread-safe using ConcurrentHashMap.
 *   <li>Throws IllegalArgumentException when planner not found.
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@Slf4j
public class BatchPlannerRegistry {

  private final Map<String, BatchPlanner> planners = new ConcurrentHashMap<>();

  /**
   * Constructor: auto-register all BatchPlanner instances.
   *
   * @param plannerList all BatchPlanner instances injected by Spring
   */
  public BatchPlannerRegistry(List<BatchPlanner> plannerList) {
    for (BatchPlanner planner : plannerList) {
      ProvenanceCode provenanceCode = planner.getProvenanceCode();
      String code = provenanceCode.getCode();
      if (planners.containsKey(code)) {
        log.warn("duplicate batch planner for provenanceCode={}", code);
      }
      planners.put(code, planner);
      log.info(
          "registered batch planner provenanceCode={} class={}",
          code,
          planner.getClass().getSimpleName());
    }
  }

  /**
   * Gets the batch planner by provenance code.
   *
   * @param provenanceCode provenance code
   * @return batch planner
   * @throws IllegalArgumentException when planner is not found
   */
  public BatchPlanner get(String provenanceCode) {
    BatchPlanner planner = planners.get(provenanceCode);
    if (planner == null) {
      throw new IllegalArgumentException(
          "Batch planner not found for provenanceCode="
              + provenanceCode
              + "; available planners: "
              + planners.keySet());
    }
    return planner;
  }

  /** Checks whether a planner exists for the given provenanceCode. */
  public boolean contains(String provenanceCode) {
    return planners.containsKey(provenanceCode);
  }
}
