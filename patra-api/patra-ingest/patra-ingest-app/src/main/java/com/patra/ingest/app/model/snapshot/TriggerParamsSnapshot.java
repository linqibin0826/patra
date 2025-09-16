package com.patra.ingest.app.model.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Value;

/**
 * 调度触发参数快照。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "triggerSource", "schedulerJobId", "windowFromInclusive", "windowToExclusive", "cfgSnapshotHash"
})
public class TriggerParamsSnapshot {
    String triggerSource;
    String schedulerJobId;
    String windowFromInclusive; // ISO-8601 文本
    String windowToExclusive;   // ISO-8601 文本
    String cfgSnapshotHash;
}
