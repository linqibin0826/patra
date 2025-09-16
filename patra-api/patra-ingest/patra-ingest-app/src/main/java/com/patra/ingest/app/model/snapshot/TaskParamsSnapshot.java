package com.patra.ingest.app.model.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Value;

/**
 * 任务参数快照（稳定 JSON → 幂等键辅助、观测）。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "planId", "sliceId", "sliceNo", "provenance", "operation", "exprHash", "sliceSpec"
})
public class TaskParamsSnapshot {
    Long planId;
    Long sliceId;
    Integer sliceNo;
    String provenance;
    String operation;
    String exprHash;
    String sliceSpec; // 直接内嵌切片 spec 的 JSON 文本（无需再次解析）
}
