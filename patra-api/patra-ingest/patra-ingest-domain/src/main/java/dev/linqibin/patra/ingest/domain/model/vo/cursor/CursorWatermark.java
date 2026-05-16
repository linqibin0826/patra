package dev.linqibin.patra.ingest.domain.model.vo.cursor;

import java.math.BigDecimal;
import java.time.Instant;

/// 规范化游标水位线值对象,表示观察到的最大游标值。
///
/// 游标可以表示为时间、数值或原始字符串值,此记录携带规范化的形式。
///
/// 不可变性:通过值语义比较相等性
///
/// 使用场景:在增量采集中记录已处理的最大游标位置
///
/// @param observedMaxValue 观察到的原始最大游标值
/// @param normalizedInstant 当可用时的解析时间戳
/// @param normalizedNumeric 当可用时的解析数值表示
public record CursorWatermark(
    String observedMaxValue, Instant normalizedInstant, BigDecimal normalizedNumeric) {

  /// 为初始运行创建水位线占位符(尚无观察值)。
  ///
  /// @return 空水位线
  public static CursorWatermark empty() {
    return new CursorWatermark(null, null, null);
  }

  /// 指示是否存在规范化的时间戳水位线。
  ///
  /// @return 如果存在时间戳水位线则返回true
  public boolean hasInstant() {
    return normalizedInstant != null;
  }

  /// 指示是否存在规范化的数值水位线。
  ///
  /// @return 如果存在数值水位线则返回true
  public boolean hasNumeric() {
    return normalizedNumeric != null;
  }
}
