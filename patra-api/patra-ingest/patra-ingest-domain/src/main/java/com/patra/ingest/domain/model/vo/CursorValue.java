package com.patra.ingest.domain.model.vo;

import com.patra.ingest.domain.model.enums.CursorType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 归一化游标值（三态：时间 / 数值 / Token）。
 * <p>封装原始字符串与便于比较/排序的解析形式。</p>
 * <ul>
 *   <li>type：游标类型 {@link com.patra.ingest.domain.model.enums.CursorType}</li>
 *   <li>raw：原始值（标准化大小写/格式后）</li>
 *   <li>instant：时间型游标解析结果</li>
 *   <li>numeric：数值型游标解析结果</li>
 * </ul>
 */
public record CursorValue(CursorType type, String raw, Instant instant, BigDecimal numeric) {
    /**
     * 构建时间游标值
     */
    public static CursorValue time(Instant v) {
        return new CursorValue(CursorType.TIME, v.toString(), v, null);
    }

    /**
     * 构建数值（ID）游标值
     */
    public static CursorValue id(BigDecimal v) {
        return new CursorValue(CursorType.ID, v.toPlainString(), null, v);
    }

    /**
     * 构建字符串 Token 游标值
     */
    public static CursorValue token(String token) {
        return new CursorValue(CursorType.TOKEN, token, null, null);
    }
}
