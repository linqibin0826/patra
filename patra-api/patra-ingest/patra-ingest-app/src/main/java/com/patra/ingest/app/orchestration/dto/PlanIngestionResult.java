package com.patra.ingest.app.orchestration.dto;

import java.util.List;

/**
 * 计划触发结果。
 */
public record PlanIngestionResult(Long scheduleInstanceId,
                                  Long planId,
                                  List<Long> sliceIds,
                                  int taskCount,
                                  String finalStatus) {
}
