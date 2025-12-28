package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 引用子集枚举。
///
/// 字段映射：cat_venue_indexing_history.citation_subset
///
/// 定义 MEDLINE/PubMed 的引用子集类型，来源于 NLM Serfile 的 IndexingHistory.CitationSubset 属性。
///
/// 子集说明：
///
/// | 代码 | 全称 | 说明 |
/// |------|------|------|
/// | IM | Index Medicus | 核心医学期刊索引 |
/// | AIM | Abridged Index Medicus | 简明医学期刊索引（核心子集） |
/// | N | Nursing | 护理学期刊 |
/// | D | Dental | 口腔医学期刊 |
/// | H | Health Administration | 卫生管理期刊 |
/// | K | Consumer Health | 消费者健康期刊 |
/// | T | Health Technology Assessment | 卫生技术评估 |
/// | E | Bioethics | 生物伦理学期刊 |
/// | S | Space Life Sciences | 太空生命科学 |
/// | X | AIDS | 艾滋病相关期刊 |
/// | B | Biotechnology | 生物技术期刊 |
/// | C | Communication Disorders | 沟通障碍期刊 |
/// | F | History of Medicine | 医学史期刊 |
/// | Q | Toxicology | 毒理学期刊 |
/// | J | Japanese Science Literature | 日本科学文献 |
/// | OM | Old MEDLINE | 1950-1965 年历史文献 |
/// | P | Population Information | 人口信息 |
/// | QIS | Quality Improvement Studies | 质量改进研究 |
/// | R | Research Support | 研究支持 |
///
/// 使用示例：
///
/// ```java
/// CitationSubset subset = CitationSubset.fromCode("IM");
/// if (subset.isCoreMedical()) {
///     // 核心医学期刊处理
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum CitationSubset {

  /// Index Medicus（核心医学期刊索引）
  IM("IM", "Index Medicus", true),

  /// Abridged Index Medicus（简明医学期刊索引）
  AIM("AIM", "Abridged Index Medicus", true),

  /// Nursing（护理学期刊）
  N("N", "Nursing", false),

  /// Dental（口腔医学期刊）
  D("D", "Dental", false),

  /// Health Administration（卫生管理期刊）
  H("H", "Health Administration", false),

  /// Consumer Health（消费者健康期刊）
  K("K", "Consumer Health", false),

  /// Health Technology Assessment（卫生技术评估）
  T("T", "Health Technology Assessment", false),

  /// Bioethics（生物伦理学期刊）
  E("E", "Bioethics", false),

  /// Space Life Sciences（太空生命科学）
  S("S", "Space Life Sciences", false),

  /// AIDS（艾滋病相关期刊）
  X("X", "AIDS", false),

  /// Biotechnology（生物技术期刊）
  B("B", "Biotechnology", false),

  /// Communication Disorders（沟通障碍期刊）
  C("C", "Communication Disorders", false),

  /// History of Medicine（医学史期刊）
  F("F", "History of Medicine", false),

  /// Toxicology（毒理学期刊）
  Q("Q", "Toxicology", false),

  /// 日本科学文献（Japanese Science Literature）
  J("J", "Japanese Science Literature", false),

  /// Old MEDLINE（1950-1965 年历史文献）
  OM("OM", "Old MEDLINE", false),

  /// Population Information（人口信息）
  P("P", "Population Information", false),

  /// Quality Improvement Studies（质量改进研究）
  QIS("QIS", "Quality Improvement Studies", false),

  /// Research Support（研究支持）
  R("R", "Research Support", false);

  /// 数据库存储的代码值
  private final String code;

  /// 描述文本
  private final String description;

  /// 是否为核心医学子集
  private final boolean coreMedical;

  CitationSubset(String code, String description, boolean coreMedical) {
    this.code = code;
    this.description = description;
    this.coreMedical = coreMedical;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static CitationSubset fromCode(String value) {
    Assert.notBlank(value, "引用子集代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (CitationSubset subset : values()) {
      if (subset.code.equals(normalized)) {
        return subset;
      }
    }
    throw new IllegalArgumentException("未知的引用子集：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static CitationSubset fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    for (CitationSubset subset : values()) {
      if (subset.code.equals(normalized)) {
        return subset;
      }
    }
    return null;
  }

  /// 判断是否为 Index Medicus 核心子集。
  ///
  /// @return true 如果为 IM 或 AIM
  public boolean isCoreMedical() {
    return coreMedical;
  }

  /// 判断是否为 Index Medicus。
  ///
  /// @return true 如果为 IM
  public boolean isIndexMedicus() {
    return this == IM;
  }

  /// 判断是否为 Abridged Index Medicus。
  ///
  /// @return true 如果为 AIM
  public boolean isAbridgedIndexMedicus() {
    return this == AIM;
  }
}
