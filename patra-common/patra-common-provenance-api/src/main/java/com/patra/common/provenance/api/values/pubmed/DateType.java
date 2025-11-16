package com.patra.common.provenance.api.values.pubmed;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * PubMed DateType 参数值枚举
 *
 * <p>指定日期过滤时使用的日期字段类型
 *
 * <h3>日期类型说明</h3>
 *
 * <ul>
 *   <li><b>PUBLICATION_DATE</b> - 发表日期（论文正式发表的日期）
 *   <li><b>ENTREZ_DATE</b> - 录入日期（数据录入 PubMed 的日期）
 *   <li><b>MODIFICATION_DATE</b> - 修改日期（记录最后修改日期）
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 按发表日期过滤
 * params.put("datetype", DateType.PUBLICATION_DATE.value());
 * params.put("mindate", "2023/01/01");
 * params.put("maxdate", "2023/12/31");
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum DateType {
  /** 发表日期（Publication Date） */
  PUBLICATION_DATE("pdat"),

  /** 录入日期（Entrez Date） */
  ENTREZ_DATE("edat"),

  /** 修改日期（Modification Date） */
  MODIFICATION_DATE("mdat");

  private final String value;

  DateType(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

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

  @Override
  public String toString() {
    return value;
  }
}
