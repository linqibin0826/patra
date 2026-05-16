package dev.linqibin.patra.ingest.domain.model.vo.shared;

import java.time.Instant;
import java.util.Map;

/// 切片边界规范值对象。
///
/// 抽象不同的切片维度,如时间窗口、ID 范围、令牌片段和额外参数。
///
/// - `windowFrom`/`windowTo`: 半开时间边界
///   - `idRangeFrom`/`idRangeTo`: 标识符范围 (语义由策略解释)
///   - `extra`: 只读扩展映射
///
/// 不变式: `extra` 永不为 `null` 且被防御性复制。
///
/// @param windowFrom 时间窗口起始时间
/// @param windowTo 时间窗口结束时间
/// @param idRangeFrom ID 范围起始值
/// @param idRangeTo ID 范围结束值
/// @param extra 扩展参数映射
/// @author linqibin
/// @since 0.1.0
public record SliceSpec(
    Instant windowFrom,
    Instant windowTo,
    String idRangeFrom,
    String idRangeTo,
    Map<String, Object> extra) {
  public SliceSpec {
    extra = extra == null ? Map.of() : Map.copyOf(extra);
  }
}
