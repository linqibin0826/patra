package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 数据来源代码枚举。
///
/// 字段映射：cat_venue_source_data.source_code
///
/// 支持的数据源：
///
/// - **OPENALEX**：OpenAlex 开放学术数据平台
/// - **PUBMED**：PubMed/NLM Catalog 生物医学文献数据库
/// - **DOAJ**：Directory of Open Access Journals（开放获取期刊目录）
/// - **CROSSREF**：Crossref 元数据注册机构
/// - **JCR**：Journal Citation Reports（科睿唯安期刊引证报告）
///
/// 数据源特点对比：
///
/// | 数据源 | 覆盖范围 | 更新频率 | 核心数据 |
/// |--------|----------|----------|----------|
/// | OPENALEX | 综合学术 | 月更新 | 引用关系、作者网络 |
/// | PUBMED | 生物医学 | 日更新 | NLM ID、MEDLINE 收录 |
/// | DOAJ | OA 期刊 | 周更新 | APC、OA 类型 |
/// | CROSSREF | 学术出版 | 实时 | DOI、出版商信息 |
/// | JCR | 核心期刊 | 年更新 | 影响因子、分区 |
///
/// 设计说明：
///
/// - 每个 Venue 可关联多个数据源
/// - 同一数据源只能关联一条 VenueSourceData 记录
/// - 数据合并时按优先级选择字段值
///
/// 使用示例：
///
/// ```java
/// DataSourceCode source = DataSourceCode.fromCode("OPENALEX");
/// if (source.isOpenAlex()) {
///     // 处理 OpenAlex 特有字段
/// }
///
/// // 检查数据源类型
/// if (source.providesRatingData()) {
///     // 该数据源可提供评级数据
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum DataSourceCode {

  /// OpenAlex 开放学术数据平台
  OPENALEX("OPENALEX", "OpenAlex", 1, false),

  /// PubMed/NLM Catalog 生物医学文献数据库
  PUBMED("PUBMED", "PubMed Catalog", 2, false),

  /// Directory of Open Access Journals（开放获取期刊目录）
  DOAJ("DOAJ", "DOAJ", 3, false),

  /// Crossref 元数据注册机构
  CROSSREF("CROSSREF", "Crossref", 4, false),

  /// Journal Citation Reports（科睿唯安期刊引证报告）
  JCR("JCR", "Journal Citation Reports", 5, true);

  /// 数据库存储的代码值
  private final String code;

  /// 数据源描述
  private final String description;

  /// 优先级（数字越小优先级越高，用于字段合并时选择）
  private final int priority;

  /// 是否提供评级数据（影响因子、分区等）
  private final boolean providesRatingData;

  DataSourceCode(String code, String description, int priority, boolean providesRatingData) {
    this.code = code;
    this.description = description;
    this.priority = priority;
    this.providesRatingData = providesRatingData;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "OPENALEX", "pubmed"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static DataSourceCode fromCode(String value) {
    Assert.notBlank(value, "数据源代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (DataSourceCode source : values()) {
      if (source.code.equals(normalized)) {
        return source;
      }
    }
    throw new IllegalArgumentException("未知的数据源：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static DataSourceCode fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    for (DataSourceCode source : values()) {
      if (source.code.equals(normalized)) {
        return source;
      }
    }
    return null;
  }

  /// 判断是否为 OpenAlex 数据源。
  ///
  /// @return true 如果为 OpenAlex
  public boolean isOpenAlex() {
    return this == OPENALEX;
  }

  /// 判断是否为 PubMed 数据源。
  ///
  /// @return true 如果为 PubMed
  public boolean isPubMed() {
    return this == PUBMED;
  }

  /// 判断是否为 DOAJ 数据源。
  ///
  /// @return true 如果为 DOAJ
  public boolean isDoaj() {
    return this == DOAJ;
  }

  /// 判断是否为 Crossref 数据源。
  ///
  /// @return true 如果为 Crossref
  public boolean isCrossref() {
    return this == CROSSREF;
  }

  /// 判断是否为 JCR 数据源。
  ///
  /// @return true 如果为 JCR
  public boolean isJcr() {
    return this == JCR;
  }

  /// 判断是否为元数据源（提供期刊基础信息）。
  ///
  /// @return true 如果为元数据源
  public boolean isMetadataSource() {
    return this == OPENALEX || this == PUBMED || this == CROSSREF;
  }

  /// 判断是否为 OA 相关数据源。
  ///
  /// @return true 如果为 OA 相关数据源
  public boolean isOaSource() {
    return this == OPENALEX || this == DOAJ;
  }

  /// 比较两个数据源的优先级。
  ///
  /// @param other 另一个数据源
  /// @return 负数表示 this 优先级更高，正数表示 other 优先级更高，0 表示相同
  public int comparePriority(DataSourceCode other) {
    return Integer.compare(this.priority, other.priority);
  }
}
