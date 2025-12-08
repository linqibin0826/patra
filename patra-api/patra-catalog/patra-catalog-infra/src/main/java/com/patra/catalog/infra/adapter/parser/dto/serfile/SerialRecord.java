package com.patra.catalog.infra.adapter.parser.dto.serfile;

import java.time.LocalDateTime;
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
/// <Serial Status="NLMCollection" PMC="Yes" MedPrint="No" DataCreationMethod="P">
///   <NlmWorkID>1234567</NlmWorkID>
///   <NlmUniqueID>0001234</NlmUniqueID>
///   <Title>Journal of Example Medicine</Title>
///   <MedlineTA>J Exam Med</MedlineTA>
///   <SortSerialName>JOURNAL OF EXAMPLE MEDICINE</SortSerialName>
///   <PublicationInfo>
///     <Country>United States</Country>
///     <Place>New York</Place>
///     <Publisher>Example Publisher</Publisher>
///     <PublicationFirstYear>1990</PublicationFirstYear>
///     <PublicationEndYear>2020</PublicationEndYear>
///     <Frequency FrequencyType="Current">Monthly</Frequency>
///     <DatesOfSerialPublication>1990-2020</DatesOfSerialPublication>
///   </PublicationInfo>
///   <ISSN IssnType="Print">1234-5678</ISSN>
///   <ISSN IssnType="Electronic">1234-5679</ISSN>
///   <ISSNLinking>1234-5678</ISSNLinking>
///   <Coden>JEXMED</Coden>
///   <Language LangType="Primary">eng</Language>
///   <CurrentlyIndexedYN>Y</CurrentlyIndexedYN>
///   <CurrentlyIndexedForSubset CurrentSubset="IM" CurrentIndexingTreatment="Full"/>
///   <IndexingSubset>IM</IndexingSubset>
///   <BroadJournalHeadingList>...</BroadJournalHeadingList>
///   <CrossReferenceList>...</CrossReferenceList>
///   <MeshHeadingList>...</MeshHeadingList>
///   <TitleRelated>...</TitleRelated>
///   <IndexingHistoryList>...</IndexingHistoryList>
///   <GeneralNote>...</GeneralNote>
///   <IlsCreatedTimestamp>...</IlsCreatedTimestamp>
///   <IlsUpdatedTimestamp>...</IlsUpdatedTimestamp>
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
/// @param languages 语言列表（含语言类型）
/// @param status Serial 状态（NLMCollection/NotNLMCollection）
/// @param pmc PMC 状态（Yes/Forthcoming/InProcess/Inactive）
/// @param medPrintYN 是否 MedPrint
/// @param dataCreationMethod 数据创建方式（P/K/O）
/// @param nlmWorkId NLM Work ID
/// @param sortSerialName 排序用期刊名称
/// @param currentlyIndexedYN 是否当前索引
/// @param currentlyIndexedForSubsets 当前索引子集列表
/// @param indexingSubset 索引子集
/// @param indexingStartDate 索引开始日期
/// @param indexOnlineYN 是否在线索引
/// @param indexingSelectedURL 索引选择 URL
/// @param reportedMedlineYN 是否报告给 MEDLINE
/// @param processingCode 处理代码
/// @param places 出版地列表
/// @param publishers 出版商列表
/// @param datesOfSerialPublication 期刊出版日期描述
/// @param frequencyType 频率类型（Current/Former）
/// @param broadJournalHeadings 广泛期刊分类列表
/// @param crossReferences 交叉引用列表
/// @param generalNotes 一般备注列表
/// @param titleContinuationYN 是否标题延续
/// @param minorTitleChangeYN 是否小标题变更
/// @param ilsCreatedTimestamp ILS 创建时间戳
/// @param ilsUpdatedTimestamp ILS 更新时间戳
/// @param deletedTimestamp 删除时间戳
/// @param medlineDataUpdatedTimestamp MEDLINE 数据更新时间戳
/// @param sefCreatedTimestamp SEF 创建时间戳
/// @param sefUpdatedTimestamp SEF 更新时间戳
/// @author linqibin
/// @since 0.1.0
public record SerialRecord(
    // === 基本标识符 ===
    String nlmUniqueId,
    String nlmWorkId,
    String title,
    String medlineTA,
    String sortSerialName,

    // === ISSN 和 CODEN ===
    String issnL,
    String issnPrint,
    String issnElectronic,
    String coden,

    // === Serial 属性 ===
    String status,
    String pmc,
    Boolean medPrintYN,
    String dataCreationMethod,

    // === 出版信息 ===
    String country,
    String frequency,
    String frequencyType,
    Integer publicationFirstYear,
    Integer publicationEndYear,
    String datesOfSerialPublication,
    List<String> places,
    List<String> publishers,

    // === 语言 ===
    List<SerialLanguage> languages,

    // === 索引相关 ===
    Boolean currentlyIndexedYN,
    List<SerialCurrentlyIndexedForSubset> currentlyIndexedForSubsets,
    String indexingSubset,
    String indexingStartDate,
    Boolean indexOnlineYN,
    String indexingSelectedURL,
    Boolean reportedMedlineYN,
    String processingCode,

    // === 分类和关联 ===
    List<SerialBroadHeading> broadJournalHeadings,
    List<SerialCrossReference> crossReferences,
    List<SerialMeshHeading> meshHeadings,
    List<SerialTitleRelated> titleRelations,
    List<SerialIndexingHistory> indexingHistories,

    // === 备注 ===
    List<SerialGeneralNote> generalNotes,

    // === 标记字段 ===
    Boolean titleContinuationYN,
    Boolean minorTitleChangeYN,

    // === 时间戳 ===
    LocalDateTime ilsCreatedTimestamp,
    LocalDateTime ilsUpdatedTimestamp,
    LocalDateTime deletedTimestamp,
    LocalDateTime medlineDataUpdatedTimestamp,
    LocalDateTime sefCreatedTimestamp,
    LocalDateTime sefUpdatedTimestamp) {

  /// 构造函数，确保列表不为 null。
  public SerialRecord {
    places = places != null ? places : List.of();
    publishers = publishers != null ? publishers : List.of();
    languages = languages != null ? languages : List.of();
    currentlyIndexedForSubsets =
        currentlyIndexedForSubsets != null ? currentlyIndexedForSubsets : List.of();
    broadJournalHeadings = broadJournalHeadings != null ? broadJournalHeadings : List.of();
    crossReferences = crossReferences != null ? crossReferences : List.of();
    meshHeadings = meshHeadings != null ? meshHeadings : List.of();
    titleRelations = titleRelations != null ? titleRelations : List.of();
    indexingHistories = indexingHistories != null ? indexingHistories : List.of();
    generalNotes = generalNotes != null ? generalNotes : List.of();
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

  /// 获取主语言（第一个 Primary 类型的语言）。
  ///
  /// @return 主语言代码，如果没有则返回 null
  public String getPrimaryLanguage() {
    return languages.stream()
        .filter(SerialLanguage::isPrimary)
        .map(SerialLanguage::code)
        .findFirst()
        .orElse(languages.isEmpty() ? null : languages.getFirst().code());
  }

  /// 判断是否属于 NLM 馆藏。
  ///
  /// @return true 如果 status 为 NLMCollection
  public boolean isNlmCollection() {
    return "NLMCollection".equals(status);
  }

  /// 判断是否在 PMC 中。
  ///
  /// @return true 如果 pmc 为 Yes
  public boolean isInPmc() {
    return "Yes".equals(pmc);
  }

  /// 判断是否当前正在索引。
  ///
  /// @return true 如果 currentlyIndexedYN 为 true
  public boolean isCurrentlyIndexed() {
    return Boolean.TRUE.equals(currentlyIndexedYN);
  }

  /// 判断是否有广泛期刊分类。
  ///
  /// @return true 如果有广泛期刊分类
  public boolean hasBroadJournalHeadings() {
    return !broadJournalHeadings.isEmpty();
  }

  /// 判断是否有交叉引用。
  ///
  /// @return true 如果有交叉引用
  public boolean hasCrossReferences() {
    return !crossReferences.isEmpty();
  }

  /// 判断是否有一般备注。
  ///
  /// @return true 如果有一般备注
  public boolean hasGeneralNotes() {
    return !generalNotes.isEmpty();
  }

  /// Builder 模式，方便构建 SerialRecord。
  public static Builder builder() {
    return new Builder();
  }

  /// SerialRecord 构建器。
  @SuppressWarnings("checkstyle:MissingJavadocMethod")
  public static class Builder {
    // === 基本标识符 ===
    private String nlmUniqueId;
    private String nlmWorkId;
    private String title;
    private String medlineTA;
    private String sortSerialName;

    // === ISSN 和 CODEN ===
    private String issnL;
    private String issnPrint;
    private String issnElectronic;
    private String coden;

    // === Serial 属性 ===
    private String status;
    private String pmc;
    private Boolean medPrintYN;
    private String dataCreationMethod;

    // === 出版信息 ===
    private String country;
    private String frequency;
    private String frequencyType;
    private Integer publicationFirstYear;
    private Integer publicationEndYear;
    private String datesOfSerialPublication;
    private List<String> places;
    private List<String> publishers;

    // === 语言 ===
    private List<SerialLanguage> languages;

    // === 索引相关 ===
    private Boolean currentlyIndexedYN;
    private List<SerialCurrentlyIndexedForSubset> currentlyIndexedForSubsets;
    private String indexingSubset;
    private String indexingStartDate;
    private Boolean indexOnlineYN;
    private String indexingSelectedURL;
    private Boolean reportedMedlineYN;
    private String processingCode;

    // === 分类和关联 ===
    private List<SerialBroadHeading> broadJournalHeadings;
    private List<SerialCrossReference> crossReferences;
    private List<SerialMeshHeading> meshHeadings;
    private List<SerialTitleRelated> titleRelations;
    private List<SerialIndexingHistory> indexingHistories;

    // === 备注 ===
    private List<SerialGeneralNote> generalNotes;

    // === 标记字段 ===
    private Boolean titleContinuationYN;
    private Boolean minorTitleChangeYN;

    // === 时间戳 ===
    private LocalDateTime ilsCreatedTimestamp;
    private LocalDateTime ilsUpdatedTimestamp;
    private LocalDateTime deletedTimestamp;
    private LocalDateTime medlineDataUpdatedTimestamp;
    private LocalDateTime sefCreatedTimestamp;
    private LocalDateTime sefUpdatedTimestamp;

    // === 基本标识符 ===

    public Builder nlmUniqueId(String nlmUniqueId) {
      this.nlmUniqueId = nlmUniqueId;
      return this;
    }

    public Builder nlmWorkId(String nlmWorkId) {
      this.nlmWorkId = nlmWorkId;
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

    public Builder sortSerialName(String sortSerialName) {
      this.sortSerialName = sortSerialName;
      return this;
    }

    // === ISSN 和 CODEN ===

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

    // === Serial 属性 ===

    public Builder status(String status) {
      this.status = status;
      return this;
    }

    public Builder pmc(String pmc) {
      this.pmc = pmc;
      return this;
    }

    public Builder medPrintYN(Boolean medPrintYN) {
      this.medPrintYN = medPrintYN;
      return this;
    }

    public Builder dataCreationMethod(String dataCreationMethod) {
      this.dataCreationMethod = dataCreationMethod;
      return this;
    }

    // === 出版信息 ===

    public Builder country(String country) {
      this.country = country;
      return this;
    }

    public Builder frequency(String frequency) {
      this.frequency = frequency;
      return this;
    }

    public Builder frequencyType(String frequencyType) {
      this.frequencyType = frequencyType;
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

    public Builder datesOfSerialPublication(String datesOfSerialPublication) {
      this.datesOfSerialPublication = datesOfSerialPublication;
      return this;
    }

    public Builder places(List<String> places) {
      this.places = places;
      return this;
    }

    public Builder publishers(List<String> publishers) {
      this.publishers = publishers;
      return this;
    }

    // === 语言 ===

    public Builder languages(List<SerialLanguage> languages) {
      this.languages = languages;
      return this;
    }

    // === 索引相关 ===

    public Builder currentlyIndexedYN(Boolean currentlyIndexedYN) {
      this.currentlyIndexedYN = currentlyIndexedYN;
      return this;
    }

    public Builder currentlyIndexedForSubsets(
        List<SerialCurrentlyIndexedForSubset> currentlyIndexedForSubsets) {
      this.currentlyIndexedForSubsets = currentlyIndexedForSubsets;
      return this;
    }

    public Builder indexingSubset(String indexingSubset) {
      this.indexingSubset = indexingSubset;
      return this;
    }

    public Builder indexingStartDate(String indexingStartDate) {
      this.indexingStartDate = indexingStartDate;
      return this;
    }

    public Builder indexOnlineYN(Boolean indexOnlineYN) {
      this.indexOnlineYN = indexOnlineYN;
      return this;
    }

    public Builder indexingSelectedURL(String indexingSelectedURL) {
      this.indexingSelectedURL = indexingSelectedURL;
      return this;
    }

    public Builder reportedMedlineYN(Boolean reportedMedlineYN) {
      this.reportedMedlineYN = reportedMedlineYN;
      return this;
    }

    public Builder processingCode(String processingCode) {
      this.processingCode = processingCode;
      return this;
    }

    // === 分类和关联 ===

    public Builder broadJournalHeadings(List<SerialBroadHeading> broadJournalHeadings) {
      this.broadJournalHeadings = broadJournalHeadings;
      return this;
    }

    public Builder crossReferences(List<SerialCrossReference> crossReferences) {
      this.crossReferences = crossReferences;
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

    // === 备注 ===

    public Builder generalNotes(List<SerialGeneralNote> generalNotes) {
      this.generalNotes = generalNotes;
      return this;
    }

    // === 标记字段 ===

    public Builder titleContinuationYN(Boolean titleContinuationYN) {
      this.titleContinuationYN = titleContinuationYN;
      return this;
    }

    public Builder minorTitleChangeYN(Boolean minorTitleChangeYN) {
      this.minorTitleChangeYN = minorTitleChangeYN;
      return this;
    }

    // === 时间戳 ===

    public Builder ilsCreatedTimestamp(LocalDateTime ilsCreatedTimestamp) {
      this.ilsCreatedTimestamp = ilsCreatedTimestamp;
      return this;
    }

    public Builder ilsUpdatedTimestamp(LocalDateTime ilsUpdatedTimestamp) {
      this.ilsUpdatedTimestamp = ilsUpdatedTimestamp;
      return this;
    }

    public Builder deletedTimestamp(LocalDateTime deletedTimestamp) {
      this.deletedTimestamp = deletedTimestamp;
      return this;
    }

    public Builder medlineDataUpdatedTimestamp(LocalDateTime medlineDataUpdatedTimestamp) {
      this.medlineDataUpdatedTimestamp = medlineDataUpdatedTimestamp;
      return this;
    }

    public Builder sefCreatedTimestamp(LocalDateTime sefCreatedTimestamp) {
      this.sefCreatedTimestamp = sefCreatedTimestamp;
      return this;
    }

    public Builder sefUpdatedTimestamp(LocalDateTime sefUpdatedTimestamp) {
      this.sefUpdatedTimestamp = sefUpdatedTimestamp;
      return this;
    }

    public SerialRecord build() {
      return new SerialRecord(
          nlmUniqueId,
          nlmWorkId,
          title,
          medlineTA,
          sortSerialName,
          issnL,
          issnPrint,
          issnElectronic,
          coden,
          status,
          pmc,
          medPrintYN,
          dataCreationMethod,
          country,
          frequency,
          frequencyType,
          publicationFirstYear,
          publicationEndYear,
          datesOfSerialPublication,
          places,
          publishers,
          languages,
          currentlyIndexedYN,
          currentlyIndexedForSubsets,
          indexingSubset,
          indexingStartDate,
          indexOnlineYN,
          indexingSelectedURL,
          reportedMedlineYN,
          processingCode,
          broadJournalHeadings,
          crossReferences,
          meshHeadings,
          titleRelations,
          indexingHistories,
          generalNotes,
          titleContinuationYN,
          minorTitleChangeYN,
          ilsCreatedTimestamp,
          ilsUpdatedTimestamp,
          deletedTimestamp,
          medlineDataUpdatedTimestamp,
          sefCreatedTimestamp,
          sefUpdatedTimestamp);
    }
  }
}
