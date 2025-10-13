package com.patra.ingest.adapter.inbound.scheduler.param;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Common param model for provenance-based scheduled jobs, aligned with the JSON structure passed by
 * XXL-Job.
 *
 * <p>All fields are optional:
 *
 * <ul>
 *   <li>windowFrom/windowTo: time window boundaries in ISO-8601 Instant format
 *   <li>priority: scheduling priority, case-insensitive enum name
 *   <li>step: slice step as ISO-8601 Duration string
 *   <li>schedulerLogId: scheduler log id, defaults to 0
 *   <li>triggeredAt: trigger timestamp in ISO-8601 Instant format
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProvenanceScheduleJobParam(
    String windowFrom,
    String windowTo,
    String priority,
    String step,
    String schedulerLogId,
    String triggeredAt) {

  /**
   * Creates an empty param instance for unified fallback handling.
   *
   * @return empty param instance
   */
  public static ProvenanceScheduleJobParam empty() {
    return new ProvenanceScheduleJobParam(null, null, null, null, null, null);
  }
}
