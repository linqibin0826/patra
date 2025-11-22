package com.patra.ingest.domain.model.vo.cursor;

import com.patra.ingest.domain.model.enums.CursorType;
import java.math.BigDecimal;
import java.time.Instant;

/// 规范化游标值对象,表示时间、数值或令牌类型的游标。
/// 
/// 保留原始字符串及其解析后的形式,用于比较和排序。
/// 
/// 不可变性:通过值语义比较相等性
/// 
/// 使用场景:在分页采集中跟踪当前位置
/// 
/// @param type 游标类型 {@link CursorType}
/// @param raw 规范化的原始值
/// @param instant 当游标表示时间时的解析时间戳
/// @param numeric 当游标表示数值时的解析数值
public record CursorValue(CursorType type, String raw, Instant instant, BigDecimal numeric) {
  /// 构建基于时间的游标值。
/// 
/// @param v 时间戳
/// @return 时间游标值
  public static CursorValue time(Instant v) {
    return new CursorValue(CursorType.TIME, v.toString(), v, null);
  }

  /// 构建基于数值(ID)的游标值。
/// 
/// @param v 数值
/// @return 数值游标值
  public static CursorValue id(BigDecimal v) {
    return new CursorValue(CursorType.ID, v.toPlainString(), null, v);
  }

  /// 构建基于字符串令牌的游标值。
/// 
/// @param token 令牌字符串
/// @return 令牌游标值
  public static CursorValue token(String token) {
    return new CursorValue(CursorType.TOKEN, token, null, null);
  }

  /// 构建空/默认游标值。
/// 
/// @return 空游标值
  public static CursorValue empty() {
    return new CursorValue(CursorType.TIME, null, null, null);
  }
}
