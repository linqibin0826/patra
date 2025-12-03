package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import java.util.Map;
import lombok.Getter;

/// 出版载体类型枚举。
///
/// 字段映射：cat_venue.venue_type
///
/// 支持的类型（与 OpenAlex Source types 对齐）：
///
/// - **JOURNAL**：学术期刊，通常有 ISSN，支持 ISO/MEDLINE 缩写
/// - **REPOSITORY**：预印本服务器和机构仓库（如 arXiv、PubMed Central）
/// - **CONFERENCE**：会议论文集
/// - **EBOOK_PLATFORM**：电子书托管平台（如 OAPEN、Project MUSE）
/// - **BOOK_SERIES**：书系（如 Springer Lecture Notes）
/// - **METADATA**：元数据聚合器（如 Crossref、DataCite）
/// - **IGSN_CATALOG**：IGSN 地质样本目录（如 SESAR、Geoscience Australia）
/// - **RAID_REGISTRY**：RAiD 研究活动标识符注册表
/// - **OTHER**：其他类型（不适合以上分类的来源）
///
/// 设计约束：
///
/// - 载体类型一旦确定，不应随意变更（影响关联的文献分类）
/// - 不同类型有不同的必填字段验证规则
///
/// 使用示例：
///
/// ```java
/// VenueType type = VenueType.fromCode("JOURNAL");
/// if (type.isJournal()) {
///     // 验证ISSN是否存在
/// }
///
/// // 从 OpenAlex type 转换
/// VenueType type = VenueType.fromOpenAlexType("repository");
/// VenueType type2 = VenueType.fromOpenAlexType("igsnCatalog");
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum VenueType {

  /// 学术期刊（Journal）
  JOURNAL("JOURNAL", "Journal"),

  /// 预印本服务器和机构仓库（Repository）
  REPOSITORY("REPOSITORY", "Repository"),

  /// 会议论文集（Conference）
  CONFERENCE("CONFERENCE", "Conference"),

  /// 电子书托管平台（eBook Platform）
  EBOOK_PLATFORM("EBOOK_PLATFORM", "eBook Platform"),

  /// 书系（Book Series）
  BOOK_SERIES("BOOK_SERIES", "Book Series"),

  /// 元数据聚合器（Metadata）
  METADATA("METADATA", "Metadata"),

  /// IGSN 地质样本目录（IGSN Catalog）
  IGSN_CATALOG("IGSN_CATALOG", "IGSN Catalog"),

  /// RAiD 研究活动标识符注册表（RAiD Registry）
  RAID_REGISTRY("RAID_REGISTRY", "RAiD Registry"),

  /// 其他类型
  OTHER("OTHER", "Other");

  /// OpenAlex type 到 VenueType 的映射表
  private static final Map<String, VenueType> OPENALEX_TYPE_MAP =
      Map.ofEntries(
          Map.entry("journal", JOURNAL),
          Map.entry("repository", REPOSITORY),
          Map.entry("conference", CONFERENCE),
          Map.entry("ebook platform", EBOOK_PLATFORM),
          Map.entry("book series", BOOK_SERIES),
          Map.entry("metadata", METADATA),
          Map.entry("igsncatalog", IGSN_CATALOG),
          Map.entry("raidregistry", RAID_REGISTRY),
          Map.entry("other", OTHER));

  /// 数据库存储的代码值（大写）
  private final String code;

  /// 描述文本
  private final String description;

  VenueType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "JOURNAL", "repository", "EBOOK_PLATFORM"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static VenueType fromCode(String value) {
    Assert.notBlank(value, "载体类型代码不能为空");
    String normalized = value.trim().toUpperCase().replace(" ", "_");
    for (VenueType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的载体类型：" + value);
  }

  /// 从 OpenAlex Source type 转换为 VenueType。
  ///
  /// OpenAlex 使用小写带空格的类型名（如 "ebook platform"），
  /// 此方法将其映射为对应的枚举值。
  ///
  /// **容错处理**：
  /// - 空值或空白字符串 → 返回 `OTHER`
  /// - 未知类型 → 返回 `OTHER`（OpenAlex 可能新增未文档化的类型）
  ///
  /// @param openAlexType OpenAlex 的 type 字段值（如 "journal", "ebook platform"），可为 null
  /// @return 对应的 VenueType 枚举值，无法识别时返回 OTHER
  public static VenueType fromOpenAlexType(String openAlexType) {
    if (openAlexType == null || openAlexType.isBlank()) {
      return OTHER;
    }
    String normalized = openAlexType.trim().toLowerCase();
    VenueType type = OPENALEX_TYPE_MAP.get(normalized);
    return type != null ? type : OTHER;
  }

  /// 判断是否为期刊。
  ///
  /// @return true 如果为期刊类型
  public boolean isJournal() {
    return this == JOURNAL;
  }

  /// 判断是否为仓库类型（预印本服务器、机构仓库）。
  ///
  /// @return true 如果为仓库类型
  public boolean isRepository() {
    return this == REPOSITORY;
  }

  /// 判断是否为会议。
  ///
  /// @return true 如果为会议类型
  public boolean isConference() {
    return this == CONFERENCE;
  }

  /// 判断是否为电子书平台。
  ///
  /// @return true 如果为电子书平台类型
  public boolean isEbookPlatform() {
    return this == EBOOK_PLATFORM;
  }

  /// 判断是否为书系。
  ///
  /// @return true 如果为书系类型
  public boolean isBookSeries() {
    return this == BOOK_SERIES;
  }

  /// 判断是否为元数据聚合器。
  ///
  /// @return true 如果为元数据类型
  public boolean isMetadata() {
    return this == METADATA;
  }

  /// 判断是否可能有 ISSN。
  ///
  /// 期刊和部分仓库可能有 ISSN。
  ///
  /// @return true 如果该类型通常有 ISSN
  public boolean mayHaveIssn() {
    return this == JOURNAL || this == REPOSITORY || this == BOOK_SERIES;
  }

  /// 判断是否为开放获取来源类型。
  ///
  /// 仓库类型通常是开放获取的。
  ///
  /// @return true 如果通常是 OA 来源
  public boolean isTypicallyOpenAccess() {
    return this == REPOSITORY;
  }
}
