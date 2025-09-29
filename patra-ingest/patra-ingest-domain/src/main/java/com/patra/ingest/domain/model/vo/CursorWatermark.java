package com.patra.ingest.domain.model.vo;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 游标归一化水位信息（Cursor Watermark）。
 * <p>同一游标可被不同类型表示（时间 / 数值 / 原始字符串），此对象统一承载归一化结果。</p>
 * <ul>
 *   <li>observedMaxValue：原始观察到的最大游标值（字符串保留原貌）</li>
 *   <li>normalizedInstant：若可解析为时间，存储其 UTC Instant 表示</li>
 *   <li>normalizedNumeric：若可解析为数值，存储其数值形式</li>
 * </ul>
 * 可部分为空：例如仅时间或仅数值。
 */
public record CursorWatermark(String observedMaxValue, Instant normalizedInstant, BigDecimal normalizedNumeric) {

    /**
     * 空水位（用于首次执行 / 未探测到）。
     */
    public static CursorWatermark empty() {
        return new CursorWatermark(null, null, null);
    }

    /**
     * 是否存在归一化时间水位。
     */
    public boolean hasInstant() {
        return normalizedInstant != null;
    }

    /**
     * 是否存在归一化数值水位。
     */
    public boolean hasNumeric() {
        return normalizedNumeric != null;
    }
}
