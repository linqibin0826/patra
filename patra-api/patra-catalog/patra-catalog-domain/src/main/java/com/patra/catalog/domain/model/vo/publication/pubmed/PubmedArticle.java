package com.patra.catalog.domain.model.vo.publication.pubmed;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Builder;

/// PubMed 文献解析中间 DTO。
///
/// 用于在 XML 解析阶段承载 PubMed 文献的原始数据，
/// 后续在 Processor 阶段进行 Venue 匹配并转换为 PublicationAggregate。
///
/// **设计说明**：
///
/// - 作为 Reader → Processor 之间的数据载体
/// - 字段与 PubMed XML 元素一一对应
/// - 不包含业务逻辑，仅提供便捷访问方法
///
/// **Venue 匹配优先级**：
///
/// 1. NLM Unique ID（最可靠）
/// 2. ISSN-L（Linking ISSN，期刊链接标识）
/// 3. Print ISSN
/// 4. Electronic ISSN
///
/// @author linqibin
/// @since 0.1.0
@Builder
public record PubmedArticle(
    // === 文献标识符 ===
    /// PubMed ID（必填，主键）
    String pmid,
    /// DOI（可选）
    String doi,
    /// PubMed Central ID（可选）
    String pmcId,

    // === 标题 ===
    /// 文章标题（必填）
    String articleTitle,
    /// 本地语言标题（可选，如中文标题）
    String vernacularTitle,

    // === 期刊信息 ===
    /// NLM 唯一标识（用于 Venue 匹配，必填）
    String nlmUniqueId,
    /// 印刷版 ISSN（可选）
    String issnPrint,
    /// 电子版 ISSN（可选）
    String issnElectronic,
    /// 链接 ISSN（ISSN-L，可选）
    String issnLinking,
    /// 期刊标题（可选）
    String journalTitle,

    // === 出版信息 ===
    /// 卷号（可选）
    String volume,
    /// 期号（可选）
    String issue,
    /// 出版年份（必填）
    Integer pubYear,
    /// 出版月份（可选，1-12）
    Integer pubMonth,
    /// 出版日期（可选，1-31）
    Integer pubDay,
    /// MedlineDate 原始字符串（可选，用于无法精确解析的日期）
    String medlineDate,

    // === 元数据 ===
    /// 语言列表（如 ["eng", "chi"]）
    List<String> languages,
    /// 发布状态（如 "epublish", "ppublish", "aheadofprint"）
    String publicationStatus,
    /// 作者列表是否完整
    Boolean authorsComplete) {

  /// 紧凑构造器：对 languages 进行防御性拷贝。
  public PubmedArticle {
    languages = languages != null ? List.copyOf(languages) : List.of();
  }

  /// 检查是否有 DOI。
  ///
  /// @return 如果有非空 DOI 返回 true
  public boolean hasDoi() {
    return doi != null && !doi.isBlank();
  }

  /// 检查是否有 PMC ID。
  ///
  /// @return 如果有非空 PMC ID 返回 true
  public boolean hasPmcId() {
    return pmcId != null && !pmcId.isBlank();
  }

  /// 获取首选 ISSN（按优先级：ISSN-L > Print > Electronic）。
  ///
  /// @return 首选 ISSN，如果都为空返回 empty
  public Optional<String> getPrimaryIssn() {
    return Stream.of(issnLinking, issnPrint, issnElectronic)
        .filter(issn -> issn != null && !issn.isBlank())
        .findFirst();
  }

  /// 获取所有非空 ISSN。
  ///
  /// @return 所有非空 ISSN 列表
  public List<String> getAllIssns() {
    return Stream.of(issnLinking, issnPrint, issnElectronic)
        .filter(issn -> issn != null && !issn.isBlank())
        .toList();
  }
}
