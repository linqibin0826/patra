package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/**
 * 出版载体类型枚举。
 *
 * <p>字段映射：cat_venue.venue_type → JOURNAL/BOOK/CONFERENCE/OTHER
 *
 * <p>业务规则：
 *
 * <ul>
 *   <li><b>JOURNAL</b>：期刊类型，必须有 ISSN，支持 ISO/MEDLINE 缩写
 *   <li><b>BOOK</b>：书籍类型，必须有 ISBN，支持版次（edition）
 *   <li><b>CONFERENCE</b>：会议类型，必须有会议名称、地点、日期
 *   <li><b>OTHER</b>：其他类型（预印本、技术报告等）
 * </ul>
 *
 * <p>设计约束：
 *
 * <ul>
 *   <li>载体类型一旦确定，不应随意变更（影响关联的文献分类）
 *   <li>不同类型有不同的必填字段验证规则
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * VenueType type = VenueType.fromCode("JOURNAL");
 * if (type.isJournal()) {
 *     // 验证ISSN是否存在
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum VenueType {

  /** 期刊（Journal） */
  JOURNAL("JOURNAL", "Journal"),

  /** 书籍（Book） */
  BOOK("BOOK", "Book"),

  /** 会议（Conference） */
  CONFERENCE("CONFERENCE", "Conference"),

  /** 其他（预印本、技术报告等） */
  OTHER("OTHER", "Other");

  /** 数据库存储的代码值（大写） */
  private final String code;

  /** 描述文本 */
  private final String description;

  VenueType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * 从代码值解析枚举（不区分大小写）。
   *
   * @param value 代码值（如 "JOURNAL", "book", "Conference"）
   * @return 对应的枚举值
   * @throws IllegalArgumentException 如果代码值无效
   */
  public static VenueType fromCode(String value) {
    Assert.notBlank(value, "载体类型代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (VenueType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的载体类型：" + value);
  }

  /**
   * 判断是否为期刊。
   *
   * @return true 如果为期刊类型
   */
  public boolean isJournal() {
    return this == JOURNAL;
  }

  /**
   * 判断是否为书籍。
   *
   * @return true 如果为书籍类型
   */
  public boolean isBook() {
    return this == BOOK;
  }

  /**
   * 判断是否为会议。
   *
   * @return true 如果为会议类型
   */
  public boolean isConference() {
    return this == CONFERENCE;
  }

  /**
   * 判断是否需要 ISSN。
   *
   * @return true 如果是期刊类型
   */
  public boolean requiresIssn() {
    return this == JOURNAL;
  }

  /**
   * 判断是否需要 ISBN。
   *
   * @return true 如果是书籍类型
   */
  public boolean requiresIsbn() {
    return this == BOOK;
  }

  /**
   * 判断是否需要会议信息。
   *
   * @return true 如果是会议类型
   */
  public boolean requiresConferenceInfo() {
    return this == CONFERENCE;
  }
}
