package com.patra.catalog.domain.model.vo.venue.pubmed;

import java.util.List;

/// PubMed 期刊解析数据。
///
/// 表示从 NLM Serfile XML 解析出的期刊数据，作为领域层的值对象。
/// 这是精简版，只包含业务层实际使用的字段。
///
/// **字段说明**：
///
/// - 标识符：nlmUniqueId, issnL, issnPrint, issnElectronic
/// - 基本信息：title, medlineTA (缩写), coden, country, frequency
/// - 出版历史：publicationFirstYear, publicationEndYear
/// - 关联数据：languages, meshHeadings, titleRelations, indexingHistories
///
/// @param nlmUniqueId NLM 唯一标识符
/// @param title 期刊标题
/// @param medlineTA MEDLINE 标题缩写
/// @param issnL Linking ISSN
/// @param issnPrint 印刷版 ISSN
/// @param issnElectronic 电子版 ISSN
/// @param coden CODEN 编码
/// @param country 出版国家
/// @param frequency 出版频率
/// @param publicationFirstYear 创刊年份
/// @param publicationEndYear 停刊年份
/// @param languages 语言列表
/// @param meshHeadings MeSH 主题词列表
/// @param titleRelations 期刊关联关系列表
/// @param indexingHistories 索引历史列表
/// @author linqibin
/// @since 0.1.0
public record PubmedSerialData(
    // === 标识符 ===
    String nlmUniqueId,
    String issnL,
    String issnPrint,
    String issnElectronic,

    // === 基本信息 ===
    String title,
    String medlineTA,
    String coden,
    String country,
    String frequency,

    // === 出版历史 ===
    Integer publicationFirstYear,
    Integer publicationEndYear,

    // === 关联数据 ===
    List<PubmedLanguage> languages,
    List<PubmedMeshHeading> meshHeadings,
    List<PubmedTitleRelation> titleRelations,
    List<PubmedIndexingHistory> indexingHistories) {

  /// 构造函数，确保列表不为 null。
  public PubmedSerialData {
    languages = languages != null ? languages : List.of();
    meshHeadings = meshHeadings != null ? meshHeadings : List.of();
    titleRelations = titleRelations != null ? titleRelations : List.of();
    indexingHistories = indexingHistories != null ? indexingHistories : List.of();
  }

  /// 判断是否有 ISSN-L。
  public boolean hasIssnL() {
    return issnL != null && !issnL.isBlank();
  }

  /// 判断是否有任何 ISSN（Print 或 Electronic）。
  public boolean hasAnyIssn() {
    return (issnPrint != null && !issnPrint.isBlank())
        || (issnElectronic != null && !issnElectronic.isBlank());
  }

  /// 判断是否有 NLM ID。
  public boolean hasNlmId() {
    return nlmUniqueId != null && !nlmUniqueId.isBlank();
  }

  /// 判断期刊是否已停刊。
  public boolean isCeased() {
    return publicationEndYear != null;
  }

  /// 获取主语言（第一个 Primary 类型的语言）。
  public String getPrimaryLanguage() {
    return languages.stream()
        .filter(PubmedLanguage::isPrimary)
        .map(PubmedLanguage::code)
        .findFirst()
        .orElse(languages.isEmpty() ? null : languages.getFirst().code());
  }

  /// 判断是否有 MeSH 主题词。
  public boolean hasMeshHeadings() {
    return !meshHeadings.isEmpty();
  }

  /// 判断是否有期刊关联关系。
  public boolean hasTitleRelations() {
    return !titleRelations.isEmpty();
  }

  /// 判断是否有索引历史。
  public boolean hasIndexingHistories() {
    return !indexingHistories.isEmpty();
  }

  /// Builder 模式，方便构建 PubmedSerialData。
  public static Builder builder() {
    return new Builder();
  }

  /// PubmedSerialData 构建器。
  @SuppressWarnings("checkstyle:MissingJavadocMethod")
  public static class Builder {
    private String nlmUniqueId;
    private String issnL;
    private String issnPrint;
    private String issnElectronic;
    private String title;
    private String medlineTA;
    private String coden;
    private String country;
    private String frequency;
    private Integer publicationFirstYear;
    private Integer publicationEndYear;
    private List<PubmedLanguage> languages;
    private List<PubmedMeshHeading> meshHeadings;
    private List<PubmedTitleRelation> titleRelations;
    private List<PubmedIndexingHistory> indexingHistories;

    public Builder nlmUniqueId(String nlmUniqueId) {
      this.nlmUniqueId = nlmUniqueId;
      return this;
    }

    public Builder issnL(String issnL) {
      this.issnL = issnL;
      return this;
    }

    public Builder issnPrint(String issnPrint) {
      this.issnPrint = issnPrint;
      return this;
    }

    public Builder issnElectronic(String issnElectronic) {
      this.issnElectronic = issnElectronic;
      return this;
    }

    public Builder title(String title) {
      this.title = title;
      return this;
    }

    public Builder medlineTA(String medlineTA) {
      this.medlineTA = medlineTA;
      return this;
    }

    public Builder coden(String coden) {
      this.coden = coden;
      return this;
    }

    public Builder country(String country) {
      this.country = country;
      return this;
    }

    public Builder frequency(String frequency) {
      this.frequency = frequency;
      return this;
    }

    public Builder publicationFirstYear(Integer publicationFirstYear) {
      this.publicationFirstYear = publicationFirstYear;
      return this;
    }

    public Builder publicationEndYear(Integer publicationEndYear) {
      this.publicationEndYear = publicationEndYear;
      return this;
    }

    public Builder languages(List<PubmedLanguage> languages) {
      this.languages = languages;
      return this;
    }

    public Builder meshHeadings(List<PubmedMeshHeading> meshHeadings) {
      this.meshHeadings = meshHeadings;
      return this;
    }

    public Builder titleRelations(List<PubmedTitleRelation> titleRelations) {
      this.titleRelations = titleRelations;
      return this;
    }

    public Builder indexingHistories(List<PubmedIndexingHistory> indexingHistories) {
      this.indexingHistories = indexingHistories;
      return this;
    }

    public PubmedSerialData build() {
      return new PubmedSerialData(
          nlmUniqueId,
          issnL,
          issnPrint,
          issnElectronic,
          title,
          medlineTA,
          coden,
          country,
          frequency,
          publicationFirstYear,
          publicationEndYear,
          languages,
          meshHeadings,
          titleRelations,
          indexingHistories);
    }
  }
}
