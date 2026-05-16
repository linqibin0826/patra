package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 载体标识符类型枚举。
///
/// 字段映射：cat_venue_identifier.identifier_type
///
/// 支持的标识符类型：
///
/// - **OPENALEX**：OpenAlex Source ID（格式：S1234567890）
/// - **ISSN**：国际标准连续出版物号（格式：XXXX-XXXX）
/// - **ISSN_L**：Linking ISSN（关联不同介质版本的 ISSN）
/// - **ISBN**：国际标准书号
/// - **NLM**：NLM 唯一标识符（PubMed/MEDLINE 使用）
/// - **MAG**：Microsoft Academic Graph ID（已停止更新）
/// - **FATCAT**：Internet Archive Fatcat ID
/// - **WIKIDATA**：Wikidata 实体 ID（格式：Q1234567）
/// - **DOAJ**：DOAJ 期刊 ID
/// - **CROSSREF**：Crossref DOI 前缀（出版商级别）
/// - **JCR**：JCR 期刊 ID
///
/// 使用示例：
///
/// ```java
/// VenueIdentifierType type = VenueIdentifierType.fromCode("ISSN");
/// if (type.isIssn()) {
///     // 验证 ISSN 格式
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum VenueIdentifierType {

  /// OpenAlex Source ID
  OPENALEX("OPENALEX", "OpenAlex ID"),

  /// 国际标准连续出版物号
  ISSN("ISSN", "ISSN"),

  /// Linking ISSN（关联不同介质版本）
  ISSN_L("ISSN_L", "Linking ISSN"),

  /// 国际标准书号
  ISBN("ISBN", "ISBN"),

  /// NLM 唯一标识符
  NLM("NLM", "NLM Unique ID"),

  /// Microsoft Academic Graph ID
  MAG("MAG", "Microsoft Academic Graph ID"),

  /// Internet Archive Fatcat ID
  FATCAT("FATCAT", "Fatcat ID"),

  /// Wikidata 实体 ID
  WIKIDATA("WIKIDATA", "Wikidata ID"),

  /// DOAJ 期刊 ID
  DOAJ("DOAJ", "DOAJ ID"),

  /// Crossref DOI 前缀（出版商级别）
  CROSSREF("CROSSREF", "Crossref DOI Prefix"),

  /// JCR 期刊 ID
  JCR("JCR", "JCR ID"),

  /// CODEN 编码（6字符期刊标识符，来自 Serfile）
  CODEN("CODEN", "CODEN Code");

  /// 数据库存储的代码值
  private final String code;

  /// 描述文本
  private final String description;

  VenueIdentifierType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "ISSN", "openalex"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static VenueIdentifierType fromCode(String value) {
    Assert.notBlank(value, "标识符类型代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (VenueIdentifierType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的标识符类型：" + value);
  }

  /// 判断是否为 OpenAlex ID。
  ///
  /// @return true 如果为 OpenAlex ID 类型
  public boolean isOpenAlex() {
    return this == OPENALEX;
  }

  /// 判断是否为 ISSN（包括普通 ISSN 和 Linking ISSN）。
  ///
  /// @return true 如果为 ISSN 类型
  public boolean isIssn() {
    return this == ISSN || this == ISSN_L;
  }

  /// 判断是否为 ISBN。
  ///
  /// @return true 如果为 ISBN 类型
  public boolean isIsbn() {
    return this == ISBN;
  }

  /// 判断是否为 NLM ID。
  ///
  /// @return true 如果为 NLM ID 类型
  public boolean isNlm() {
    return this == NLM;
  }

  /// 判断是否为外部数据源 ID（OpenAlex/MAG/Fatcat/Wikidata/DOAJ/Crossref/JCR）。
  ///
  /// @return true 如果为外部数据源 ID
  public boolean isExternalSourceId() {
    return this == OPENALEX
        || this == MAG
        || this == FATCAT
        || this == WIKIDATA
        || this == DOAJ
        || this == CROSSREF
        || this == JCR;
  }

  /// 判断是否为标准出版标识符（ISSN/ISBN）。
  ///
  /// @return true 如果为标准出版标识符
  public boolean isStandardPublishingId() {
    return this == ISSN || this == ISSN_L || this == ISBN;
  }

  /// 判断是否为 DOAJ ID。
  ///
  /// @return true 如果为 DOAJ ID 类型
  public boolean isDoaj() {
    return this == DOAJ;
  }

  /// 判断是否为 Crossref ID。
  ///
  /// @return true 如果为 Crossref ID 类型
  public boolean isCrossref() {
    return this == CROSSREF;
  }

  /// 判断是否为 JCR ID。
  ///
  /// @return true 如果为 JCR ID 类型
  public boolean isJcr() {
    return this == JCR;
  }

  /// 判断是否为 CODEN。
  ///
  /// @return true 如果为 CODEN 类型
  public boolean isCoden() {
    return this == CODEN;
  }
}
