package com.patra.ingest.app.dto;

import java.util.List;

/**
 * 计划触发结果。
 */
public record PlanTriggerResult(Long scheduleInstanceId,
                                Long planId,
                                List<Long> sliceIds,
                                int taskCount,
                                String finalStatus) {
}
