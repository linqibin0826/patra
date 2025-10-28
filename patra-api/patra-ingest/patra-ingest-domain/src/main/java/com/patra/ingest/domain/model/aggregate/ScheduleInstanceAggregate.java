package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Aggregate root representing a scheduling trigger together with its initial snapshot.
 *
 * @author linqibin
 * @since 0.1.0
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ScheduleInstanceAggregate extends AggregateRoot<Long> {

  /** Scheduler type. */
  private final Scheduler scheduler;

  /** Scheduler job identifier. */
  private final String schedulerJobId;

  /** Scheduler log identifier. */
  private final String schedulerLogId;

  /** Trigger type. */
  private final TriggerType triggerType;

  /** Trigger timestamp. */
  private final Instant triggeredAt;

  /** Provenance/source code. */
  private final String provenanceCode;

  /** Trigger parameters delivered by the scheduler. */
  private final Map<String, Object> triggerParams;

  private ScheduleInstanceAggregate(
      Long id,
      Scheduler scheduler,
      String schedulerJobId,
      String schedulerLogId,
      TriggerType triggerType,
      Instant triggeredAt,
      Map<String, Object> triggerParams,
      String provenanceCode) {
    super(id);
    this.scheduler = Objects.requireNonNull(scheduler, "schedulerCode must not be null");
    this.schedulerJobId = schedulerJobId;
    this.schedulerLogId = schedulerLogId;
    this.triggerType = Objects.requireNonNull(triggerType, "triggerType must not be null");
    this.triggeredAt = triggeredAt == null ? Instant.now() : triggeredAt;
    this.triggerParams = triggerParams;
    this.provenanceCode = provenanceCode;
    // Expression prototypes are no longer stored at the scheduling layer; plan aggregates keep them
    // instead.
  }

  public static ScheduleInstanceAggregate start(
      Scheduler scheduler,
      String schedulerJobId,
      String schedulerLogId,
      TriggerType triggerType,
      Instant triggeredAt,
      Map<String, Object> triggerParams,
      String provenanceCode) {
    return new ScheduleInstanceAggregate(
        null,
        scheduler,
        schedulerJobId,
        schedulerLogId,
        triggerType,
        triggeredAt,
        triggerParams,
        provenanceCode);
  }

  public static ScheduleInstanceAggregate restore(
      Long id,
      Scheduler scheduler,
      String schedulerJobId,
      String schedulerLogId,
      TriggerType triggerType,
      Instant triggeredAt,
      Map<String, Object> triggerParams,
      String provenanceCode,
      long version) {
    ScheduleInstanceAggregate aggregate =
        new ScheduleInstanceAggregate(
            id,
            scheduler,
            schedulerJobId,
            schedulerLogId,
            triggerType,
            triggeredAt,
            triggerParams,
            provenanceCode);
    aggregate.assignVersion(version);
    return aggregate;
  }

  /**
   * Records additional snapshots for this schedule instance.
   *
   * <p>Placeholder for future snapshot recording requirements.
   */
  public void recordSnapshots() {}
}
