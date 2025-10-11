package com.patra.ingest.app.usecase.plan.dto;

import java.util.List;

/**
 * Result summary returned to the scheduler after a plan is triggered.
 *
 * @param scheduleInstanceId scheduler instance identifier
 * @param planId             persisted plan identifier
 * @param sliceIds           identifiers for created slices
 * @param taskCount          number of tasks generated for this execution
 * @param finalStatus        human-readable status summary
 */
public record PlanIngestionResult(Long scheduleInstanceId,
                                  Long planId,
                                  List<Long> sliceIds,
                                  int taskCount,
                                  String finalStatus) {
}
