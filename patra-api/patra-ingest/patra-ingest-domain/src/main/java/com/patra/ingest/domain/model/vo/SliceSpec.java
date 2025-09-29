package com.patra.ingest.domain.model.vo;

import java.time.Instant;
import java.util.Map;

/**
 * 切片边界规范（Slice Specification）。
 * <p>统一抽象多种切片维度：时间窗口 / ID 区间 / Token 分段 / 其它扩展参数。</p>
 * <ul>
 *   <li>windowFrom / windowTo：时间维度边界（半开区间）</li>
 *   <li>idRangeFrom / idRangeTo：主键或业务 ID 范围（闭区间语义，具体由策略解析）</li>
 *   <li>extra：扩展键值（只读防变异）</li>
 * </ul>
 * 不变式：extra 永远非 null 且不可修改（CopyOf）。
 */
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
