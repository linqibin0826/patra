package com.patra.ingest.domain.model.vo;

import com.patra.ingest.domain.model.enums.CursorType;
import java.math.BigDecimal;
import java.time.Instant;

/** 归一化游标值。 */
public record CursorValue(CursorType type, String raw, Instant instant, BigDecimal numeric) {
    public static CursorValue time(Instant v) { return new CursorValue(CursorType.TIME, v.toString(), v, null); }
    public static CursorValue id(BigDecimal v) { return new CursorValue(CursorType.ID, v.toPlainString(), null, v); }
    public static CursorValue token(String token) { return new CursorValue(CursorType.TOKEN, token, null, null); }
}
