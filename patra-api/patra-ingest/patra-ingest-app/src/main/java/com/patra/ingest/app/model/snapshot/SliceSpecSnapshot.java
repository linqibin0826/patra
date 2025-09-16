package com.patra.ingest.app.model.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.patra.common.enums.IngestDateType;
import lombok.Builder;
import lombok.Value;

/**
 * 切片边界规范快照（用于稳定 JSON 序列化与签名计算）。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "kind", "dateField", "fromInclusive", "toExclusive", "timezone", "overlapDays"
})
public class SliceSpecSnapshot {
    String kind;              // time / id_range ...
    IngestDateType dateField;         // 如 PDAT
    String fromInclusive;     // ISO-8601 文本
    String toExclusive;       // ISO-8601 文本
    String timezone;          // 时区标识
    Integer overlapDays;      // 窗口重叠天数
}
