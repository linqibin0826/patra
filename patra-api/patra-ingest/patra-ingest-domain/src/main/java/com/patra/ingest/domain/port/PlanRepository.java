package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import java.util.Optional;

/**
 * Repository port for plan aggregates.
 *
 * <p>Persists, deduplicates, and retrieves ingestion plans to guarantee consistency during creation
 * and replay.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanRepository {

  /**
   * Persist or update a single plan aggregate.
   *
   * @param plan plan aggregate containing window, trigger, and slicing strategy
   * @return persisted aggregate
   */
  PlanAggregate save(PlanAggregate plan);

  /**
   * Retrieve a plan aggregate by {@code planKey}.
   *
   * @param planKey unique plan key derived from source, operation, window, etc.
   * @return matching plan or {@link Optional#empty()}
   */
  Optional<PlanAggregate> findByPlanKey(String planKey);

  /**
   * Check whether the given {@code planKey} already exists.
   *
   * @param planKey unique plan key
   * @return {@code true} when a plan exists
   */
  boolean existsByPlanKey(String planKey);

  /**
   * Retrieve a plan aggregate by identifier.
   *
   * @param planId plan identifier
   * @return plan aggregate or {@link Optional#empty()}
   */
  Optional<PlanAggregate> findById(Long planId);
}
