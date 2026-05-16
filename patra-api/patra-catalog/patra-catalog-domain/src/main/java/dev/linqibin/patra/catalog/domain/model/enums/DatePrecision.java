package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 日期精度枚举。
///
/// 字段映射：cat_publication_date.date_precision
///
/// 医学文献的日期经常不完整，本枚举用于标识日期的精度级别：
///
/// - **DAY** - 精确到日（如 2024-03-15）
/// - **MONTH** - 精确到月（如 2024-03，日期未知）
/// - **YEAR** - 仅有年份（如 2024，月/日未知）
///
/// 精度顺序：DAY > MONTH > YEAR
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum DatePrecision {

  /// 精确到年
  YEAR("year", "Year Only", 1),

  /// 精确到月
  MONTH("month", "Year and Month", 2),

  /// 精确到日
  DAY("day", "Full Date", 3);

  /// 数据库存储的代码值（小写）
  private final String code;

  /// 描述文本
  private final String description;

  /// 精度级别（数值越大精度越高）
  private final int level;

  DatePrecision(String code, String description, int level) {
    this.code = code;
    this.description = description;
    this.level = level;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "year", "MONTH", "Day"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static DatePrecision fromCode(String value) {
    Assert.notBlank(value, "日期精度代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (DatePrecision precision : values()) {
      if (precision.code.equals(normalized)) {
        return precision;
      }
    }
    throw new IllegalArgumentException("未知的日期精度：" + value);
  }

  /// 判断当前精度是否高于或等于指定精度。
  ///
  /// @param other 比较的精度
  /// @return true 如果当前精度 >= 指定精度
  public boolean isAtLeast(DatePrecision other) {
    return this.level >= other.level;
  }

  /// 判断是否为完整日期（精确到日）。
  ///
  /// @return true 如果为 DAY
  public boolean isComplete() {
    return this == DAY;
  }
}
