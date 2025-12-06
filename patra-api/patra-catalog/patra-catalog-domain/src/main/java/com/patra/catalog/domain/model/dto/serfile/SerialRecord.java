package com.patra.catalog.domain.model.dto.serfile;

import java.util.List;

/// Serfile Serial 解析结果记录。
///
/// 从 Serfile XML 的 `Serial` 元素解析出的完整数据传输对象。
/// 封装从 XML 解析出的期刊数据，作为中间传输对象。
/// 不是领域实体，仅用于解析结果传递。
///
/// **XML 结构示例**：
///
/// ```xml
/// <Serial Status="NLMCollection">
///   <NlmWorkID>1234567</NlmWorkID>
///   <NlmUniqueID>0001234</NlmUniqueID>
///   <Title>Journal of Example Medicine</Title>
///   <MedlineTA>J Exam Med</MedlineTA>
///   <PublicationInfo>
///     <Country>United States</Country>
///     <PublicationFirstYear>1990</PublicationFirstYear>
///     <PublicationEndYear>2020</PublicationEndYear>
///     <Frequency FrequencyType="Current">Monthly</Frequency>
///   </PublicationInfo>
///   <ISSN IssnType="Print">1234-5678</ISSN>
///   <ISSN IssnType="Electronic">1234-5679</ISSN>
///   <ISSNLinking>1234-5678</ISSNLinking>
///   <Coden>JEXMED</Coden>
///   <Language LangType="Primary">eng</Language>
///   <MeshHeadingList>...</MeshHeadingList>
///   <TitleRelated>...</TitleRelated>
///   <IndexingHistoryList>...</IndexingHistoryList>
/// </Serial>
/// ```
///
/// @param nlmUniqueId NLM 唯一标识符（必填）
/// @param title 期刊标题（必填）
/// @param medlineTA MEDLINE 标题缩写
/// @param issnL Linking ISSN
/// @param issnPrint 印刷版 ISSN
/// @param issnElectronic 电子版 ISSN
/// @param coden CODEN 编码（6字符）
/// @param country 出版国家
/// @param frequency 出版频率（如 Monthly, Weekly）
/// @param publicationFirstYear 创刊年份
/// @param publicationEndYear 停刊年份
/// @param meshHeadings MeSH 主题词列表
/// @param titleRelations 期刊关联关系列表
/// @param indexingHistories 索引历史列表
/// @param languages 语言列表（ISO 639-3 代码）
/// @author linqibin
/// @since 0.1.0
public record SerialRecord(
    String nlmUniqueId,
    String title,
    String medlineTA,
    String issnL,
    String issnPrint,
    String issnElectronic,
    String coden,
    String country,
    String frequency,
    Integer publicationFirstYear,
    Integer publicationEndYear,
    List<SerialMeshHeading> meshHeadings,
    List<SerialTitleRelated> titleRelations,
    List<SerialIndexingHistory> indexingHistories,
    List<String> languages) {

  /// 构造函数，确保列表不为 null。
  public SerialRecord {
    meshHeadings = meshHeadings != null ? meshHeadings : List.of();
    titleRelations = titleRelations != null ? titleRelations : List.of();
    indexingHistories = indexingHistories != null ? indexingHistories : List.of();
    languages = languages != null ? languages : List.of();
  }

  /// 判断是否有 ISSN-L。
  ///
  /// @return true 如果有 ISSN-L
  public boolean hasIssnL() {
    return issnL != null && !issnL.isBlank();
  }

  /// 判断是否有任何 ISSN（Print 或 Electronic）。
  ///
  /// @return true 如果有任何 ISSN
  public boolean hasAnyIssn() {
    return (issnPrint != null && !issnPrint.isBlank())
        || (issnElectronic != null && !issnElectronic.isBlank());
  }

  /// 判断是否有 CODEN。
  ///
  /// @return true 如果有 CODEN
  public boolean hasCoden() {
    return coden != null && !coden.isBlank();
  }

  /// 判断是否有 MeSH 主题词。
  ///
  /// @return true 如果有 MeSH 主题词
  public boolean hasMeshHeadings() {
    return !meshHeadings.isEmpty();
  }

  /// 判断是否有期刊关联关系。
  ///
  /// @return true 如果有关联关系
  public boolean hasTitleRelations() {
    return !titleRelations.isEmpty();
  }

  /// 判断是否有索引历史。
  ///
  /// @return true 如果有索引历史
  public boolean hasIndexingHistories() {
    return !indexingHistories.isEmpty();
  }

  /// 判断期刊是否已停刊。
  ///
  /// @return true 如果有停刊年份
  public boolean isCeased() {
    return publicationEndYear != null;
  }

  /// 获取主语言（第一个语言）。
  ///
  /// @return 主语言代码，如果没有则返回 null
  public String getPrimaryLanguage() {
    return languages.isEmpty() ? null : languages.getFirst();
  }

  /// Builder 模式，方便构建 SerialRecord。
  public static Builder builder() {
    return new Builder();
  }

  /// SerialRecord 构建器。
  public static class Builder {
    private String nlmUniqueId;
    private String title;
    private String medlineTA;
    private String issnL;
    private String issnPrint;
    private String issnElectronic;
    private String coden;
    private String country;
    private String frequency;
    private Integer publicationFirstYear;
    private Integer publicationEndYear;
    private List<SerialMeshHeading> meshHeadings;
    private List<SerialTitleRelated> titleRelations;
    private List<SerialIndexingHistory> indexingHistories;
    private List<String> languages;

    public Builder nlmUniqueId(String nlmUniqueId) {
      this.nlmUniqueId = nlmUniqueId;
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

    public Builder meshHeadings(List<SerialMeshHeading> meshHeadings) {
      this.meshHeadings = meshHeadings;
      return this;
    }

    public Builder titleRelations(List<SerialTitleRelated> titleRelations) {
      this.titleRelations = titleRelations;
      return this;
    }

    public Builder indexingHistories(List<SerialIndexingHistory> indexingHistories) {
      this.indexingHistories = indexingHistories;
      return this;
    }

    public Builder languages(List<String> languages) {
      this.languages = languages;
      return this;
    }

    public SerialRecord build() {
      return new SerialRecord(
          nlmUniqueId,
          title,
          medlineTA,
          issnL,
          issnPrint,
          issnElectronic,
          coden,
          country,
          frequency,
          publicationFirstYear,
          publicationEndYear,
          meshHeadings,
          titleRelations,
          indexingHistories,
          languages);
    }
  }
}
