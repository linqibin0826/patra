package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;

/**
 * Repository port for schedule instances.
 *
 * <p>Persists contextual snapshots for each scheduler trigger to support replay, audit, and task
 * linkage.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ScheduleInstanceRepository {

  /**
   * Persist or update a schedule instance aggregate.
   *
   * @param instance aggregate containing scheduler metadata, trigger timestamp, and parameters
   * @return persisted schedule instance
   */
  ScheduleInstanceAggregate saveOrUpdateInstance(ScheduleInstanceAggregate instance);
}
