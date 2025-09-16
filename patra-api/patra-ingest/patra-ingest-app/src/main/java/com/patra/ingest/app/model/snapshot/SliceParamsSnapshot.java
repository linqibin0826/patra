package com.patra.ingest.app.model.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.patra.common.enums.IngestDateType;
import lombok.Builder;
import lombok.Value;

/**
 * 计划切片策略参数快照（持久化到 Plan.sliceParams）。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "strategy", "primaryDateField", "overlapDays"
})
public class SliceParamsSnapshot {
    String strategy;          // TIME
    IngestDateType primaryDateField;  // PDAT 等
    Integer overlapDays;      // 重叠天数
}
