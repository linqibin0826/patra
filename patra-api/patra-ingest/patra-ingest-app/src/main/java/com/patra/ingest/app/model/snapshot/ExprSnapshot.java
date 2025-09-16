package com.patra.ingest.app.model.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.patra.common.enums.IngestDateType;
import lombok.Builder;
import lombok.Value;

/**
 * 局部化表达式快照（仅用于存档与哈希）。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type", "dateField", "fromInclusive", "toExclusive", "includeFrom", "includeTo", "hasAtom", "baseProto"
})
public class ExprSnapshot {
    String type;              // rangeDateTime
    IngestDateType dateField;         // PDAT
    String fromInclusive;     // ISO-8601 文本
    String toExclusive;       // ISO-8601 文本
    Boolean includeFrom;      // true
    Boolean includeTo;        // false
    Boolean hasAtom;          // 诊断
    String baseProto;         // 原始 proto JSON
}
