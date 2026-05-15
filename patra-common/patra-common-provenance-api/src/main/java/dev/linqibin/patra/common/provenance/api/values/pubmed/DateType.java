package dev.linqibin.patra.common.provenance.api.values.pubmed;

import com.fasterxml.jackson.annotation.JsonValue;

/// PubMed DateType 参数值枚举
///
/// 指定日期过滤时使用的日期字段类型
///
/// ### 日期类型说明
///
/// - **PUBLICATION_DATE** - 发表日期（论文正式发表的日期）
///   - **ENTREZ_DATE** - 录入日期（数据录入 PubMed 的日期）
///   - **MODIFICATION_DATE** - 修改日期（记录最后修改日期）
///
/// ### 使用示例
///
/// ```java
/// // 按发表日期过滤
/// params.put("datetype", DateType.PUBLICATION_DATE.value());
/// params.put("mindate", "2023/01/01");
/// params.put("maxdate", "2023/12/31");
/// ```
///
/// @author linqibin
/// @since 0.1.0
public enum DateType {
  /// 发表日期（Publication Date）
  PUBLICATION_DATE("pdat"),

  /// 录入日期（Entrez Date）
  ENTREZ_DATE("edat"),

  /// 修改日期（Modification Date）
  MODIFICATION_DATE("mdat");

  private final String value;

  /// 构造 DateType 枚举常量。
  ///
  /// @param value API 参数值
  DateType(String value) {
    this.value = value;
  }

  /// 获取 API 参数值。
  ///
  /// @return API 参数字符串值
  @JsonValue
  public String value() {
    return value;
  }

  /// 从字符串解析（忽略大小写）。
  ///
  /// @param value 字符串值
  /// @return 对应的枚举
  /// @throws IllegalArgumentException 如果值为 null 或无效
  public static DateType fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("DateType 不能为 null");
    }
    for (DateType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的 DateType: " + value);
  }

  /// 安全解析（返回默认值而非抛异常）。
  ///
  /// @param value 字符串值
  /// @param defaultValue 默认值
  /// @return 对应的枚举，如果无效则返回默认值
  public static DateType fromStringOrDefault(String value, DateType defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    try {
      return fromString(value);
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }

  /// 返回日期类型的字符串表示形式。
  ///
  /// @return API 参数值
  @Override
  public String toString() {
    return value;
  }
}
