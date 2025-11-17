package com.patra.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 出版物数据源使用的通用采集日期类型枚举。
 *
 * <p>用于标识文献记录中不同类型的日期字段(例如 PubMed 的 PDAT/EDAT/MHDA 字段)。
 *
 * <p>示例:
 *
 * <ul>
 *   <li><b>PDAT</b> – 发布日期,文献的官方发布日期,常用于过滤检索。
 *   <li><b>EDAT</b> – Entrez 日期,记录被采集入库的日期。
 *   <li><b>MHDA</b> – MeSH 日期,MeSH 主题词被分配的日期。
 * </ul>
 *
 * <p>参考: PubMed 帮助文档。
 */
@Getter
@RequiredArgsConstructor
public enum IngestDateType {
  PDAT("PDAT", "Publication Date", "文献的官方发布日期"),
  EDAT("EDAT", "Entrez Date", "文献记录被采集进入 PubMed 数据库的日期"),
  MHDA("MHDA", "MeSH Date", "MeSH 索引被分配到文献的日期");

  /** 数据源特定的日期类型标识码(例如 {@code PDAT})。 */
  private final String code;

  /** 简短的显示名称,例如 {@code Publication Date}。 */
  private final String name;

  /** 日期类型的人类可读描述。 */
  private final String description;

  /**
   * 用于 Jackson 从代码创建枚举的工厂方法。
   *
   * @param code 日期类型代码
   * @return 匹配的日期类型枚举
   * @throws IllegalArgumentException 如果代码未知
   */
  @JsonCreator
  public static IngestDateType fromCode(String code) {
    for (IngestDateType type : IngestDateType.values()) {
      if (type.code.equalsIgnoreCase(code)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的日期类型代码: " + code);
  }

  /**
   * 将枚举序列化为其代码以输出 JSON。
   *
   * @return 日期类型代码
   */
  @JsonValue
  public String toCode() {
    return this.code;
  }
}
