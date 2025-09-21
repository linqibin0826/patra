package com.patra.ingest.domain.model.vo;

import java.time.Instant;
import java.util.Map;

/** 切片边界规范（时间/ID/Token/预算等统一抽象）。 */
public record SliceSpec(
        Instant windowFrom,
        Instant windowTo,
        String idRangeFrom,
        String idRangeTo,
        Map<String, Object> extra
) {
    public SliceSpec {
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }
}
