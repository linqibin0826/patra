package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import com.patra.catalog.domain.model.enums.PublicationMedium;
import com.patra.catalog.domain.model.enums.PublicationStatus;
import com.patra.catalog.domain.model.vo.publication.LanguageInfo;
import com.patra.catalog.domain.model.vo.publication.PublicationAbstract;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venueinstance.JournalInstanceParams;
import com.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import com.patra.catalog.domain.port.lookup.FunderLookupPort;
import com.patra.catalog.domain.port.lookup.LanguageLookupPort;
import com.patra.catalog.domain.port.lookup.VenueLookupPort;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.FundingData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.KeywordData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.MeshHeadingData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.PublicationTypeData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.QualifierData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.SupplMeshData;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.CanonicalPublication;
import com.patra.common.model.CanonicalPublication.Abstract;
import com.patra.common.model.CanonicalPublication.AbstractSection;
import com.patra.common.model.CanonicalPublication.DescriptorName;
import com.patra.common.model.CanonicalPublication.FundingInfo;
import com.patra.common.model.CanonicalPublication.Identifier;
import com.patra.common.model.CanonicalPublication.Journal;
import com.patra.common.model.CanonicalPublication.Keyword;
import com.patra.common.model.CanonicalPublication.KeywordSet;
import com.patra.common.model.CanonicalPublication.MeshHeading;
import com.patra.common.model.CanonicalPublication.PublicationDates;
import com.patra.common.model.CanonicalPublication.PublicationType;
import com.patra.common.model.CanonicalPublication.QualifierName;
import com.patra.common.model.enums.PublicationIdentifierType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/// PubMed 文献处理器。
///
/// **职责**：
///
/// 1. 去重：检查 PMID 是否已存在，跳过重复记录
/// 2. Venue 匹配：通过 NLM ID/ISSN 匹配载体
/// 3. VenueInstance 创建：获取或创建载体实例（卷期）
/// 4. 聚合根构建：将 CanonicalPublication 转换为 PublicationAggregate
/// 5. MeSH 数据处理：解析 MeSH 标引并映射为关联数据
///
/// **跳过条件**：
///
/// - PMID 已存在（去重）
/// - 无法匹配 Venue（数据不完整）
///
/// **返回 null 表示跳过该记录**，Spring Batch 会自动忽略。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class PubmedArticleItemProcessor
    implements ItemProcessor<CanonicalPublication, PublicationImportResult> {

  private final PublicationRepository publicationRepository;
  private final VenueLookupPort venueLookupPort;
  private final VenueInstanceGateway venueInstanceGateway;
  private final LanguageLookupPort languageLookupPort;
  private final FunderLookupPort funderLookupPort;

  @Override
  public PublicationImportResult process(CanonicalPublication publication) throws Exception {
    String pmid = extractPmid(publication);

    if (pmid == null) {
      log.debug("跳过缺少 PMID 的文献");
      return null;
    }

    // 1. 去重检查
    if (publicationRepository.existsByPmid(pmid)) {
      log.debug("跳过已存在的文献：PMID={}", pmid);
      return null;
    }

    // 2. Venue 匹配
    // TODO: 当前仅支持期刊文章，后续需扩展支持其他出版物类型
    //   - Book/Book Chapter：PubMed 通过 NCBI Bookshelf 索引的书籍章节，使用 <Book> 元素
    //   - Preprint：bioRxiv/medRxiv 预印本
    //   - Dataset：数据集引用
    Journal journal = publication.getJournal();
    if (journal == null) {
      log.debug("跳过非期刊类型文献：PMID={}", pmid);
      return null;
    }

    String nlmUniqueId = journal.getNlmUniqueId();
    List<String> issns = collectIssns(journal);
    Optional<VenueId> venueIdOpt = venueLookupPort.findByPriority(nlmUniqueId, issns);

    // TODO: 没有匹配到 Venue 时不应该跳过文献，应该：
    //   1. 创建 PublicationAggregate（venueId 和 venueInstanceId 为 null）
    //   2. 后续通过异步任务或事件驱动方式补充 Venue 关联
    //   3. 或者将这些"孤儿文献"记录到专门的队列/表中待处理
    if (venueIdOpt.isEmpty()) {
      log.warn("无法匹配 Venue，跳过文献：PMID={}, NLM={}, ISSNs={}", pmid, nlmUniqueId, issns);
      return null;
    }

    VenueId venueId = venueIdOpt.get();

    // 3. VenueInstance 创建/获取
    Integer pubYear = extractPublicationYear(publication);
    JournalInstanceParams params =
        JournalInstanceParams.builder()
            .venueId(venueId)
            .volume(journal.getVolume())
            .issue(journal.getIssue())
            .publicationYear(pubYear)
            .publicationMonth(extractPublicationMonth(publication))
            .publicationDay(extractPublicationDay(publication))
            .build();
    VenueInstanceAggregate venueInstance = venueInstanceGateway.findOrCreateJournalInstance(params);

    // 4. 构建 PublicationAggregate
    PublicationAggregate aggregate =
        buildPublicationAggregate(publication, pmid, venueId, venueInstance, pubYear);

    // 5. 处理关联数据
    List<MeshHeadingData> meshHeadings = buildMeshHeadingData(publication);
    List<SupplMeshData> supplMeshNames = buildSupplMeshData(publication);
    List<KeywordData> keywords = buildKeywordData(publication);
    List<FundingData> funding = buildFundingData(publication);
    List<PublicationTypeData> publicationTypes = buildPublicationTypeData(publication);

    return PublicationImportResult.ofAllWithSupplMesh(
        aggregate, meshHeadings, keywords, funding, publicationTypes, supplMeshNames);
  }

  /// 从 CanonicalPublication 提取 PMID。
  private String extractPmid(CanonicalPublication publication) {
    if (publication.getIdentifiers() == null) {
      return null;
    }
    return publication.getIdentifiers().stream()
        .filter(id -> PublicationIdentifierType.PMID == id.getType())
        .map(Identifier::getValue)
        .findFirst()
        .orElse(null);
  }

  /// 从 CanonicalPublication 提取 DOI。
  private String extractDoi(CanonicalPublication publication) {
    if (publication.getIdentifiers() == null) {
      return null;
    }
    return publication.getIdentifiers().stream()
        .filter(id -> PublicationIdentifierType.DOI == id.getType())
        .map(Identifier::getValue)
        .findFirst()
        .orElse(null);
  }

  /// 从 CanonicalPublication 提取出版年份。
  private Integer extractPublicationYear(CanonicalPublication publication) {
    PublicationDates dates = publication.getDates();
    if (dates == null || dates.getPublished() == null) {
      return null;
    }
    return dates.getPublished().getYear();
  }

  /// 从 CanonicalPublication 提取出版月份。
  private Integer extractPublicationMonth(CanonicalPublication publication) {
    PublicationDates dates = publication.getDates();
    if (dates == null || dates.getPublished() == null) {
      return null;
    }
    return dates.getPublished().getMonthValue();
  }

  /// 从 CanonicalPublication 提取出版日期。
  private Integer extractPublicationDay(CanonicalPublication publication) {
    PublicationDates dates = publication.getDates();
    if (dates == null || dates.getPublished() == null) {
      return null;
    }
    return dates.getPublished().getDayOfMonth();
  }

  /// 收集所有 ISSN（按优先级排序：Linking → Print/Electronic）。
  ///
  /// Venue 匹配优先级：nlmUniqueId > issnLinking > issn
  private List<String> collectIssns(Journal journal) {
    List<String> issns = new ArrayList<>(2);

    // ISSN-L 优先级最高
    if (journal.getIssnLinking() != null && !journal.getIssnLinking().isBlank()) {
      issns.add(journal.getIssnLinking());
    }

    // 主 ISSN（已在解析阶段确定为 Print 或 Electronic）
    if (journal.getIssn() != null && !journal.getIssn().isBlank()) {
      issns.add(journal.getIssn());
    }

    return issns;
  }

  /// 构建 PublicationAggregate。
  private PublicationAggregate buildPublicationAggregate(
      CanonicalPublication publication,
      String pmid,
      VenueId venueId,
      VenueInstanceAggregate venueInstance,
      Integer pubYear) {

    // 构建语言信息
    LanguageInfo languageInfo = buildLanguageInfo(publication);

    // 解析出版状态
    PublicationStatus publicationStatus =
        parsePublicationStatus(publication.getPublicationStatus());

    // 提取 DOI
    String doi = extractDoi(publication);

    // 解析媒介类型
    PublicationMedium mediaType = parsePublicationMedium(publication.getMediaType());

    // 创建聚合根
    PublicationAggregate aggregate =
        PublicationAggregate.create(
            ProvenanceCode.PUBMED,
            pmid,
            doi,
            venueId,
            venueInstance.getId(),
            publication.getTitle(),
            publication.getOriginalTitle(),
            languageInfo,
            publicationStatus,
            mediaType,
            pubYear,
            publication.getAuthorsComplete(),
            publication.getNumberOfReferences(),
            publication.getConflictOfInterestStatement());

    // 补充摘要
    PublicationAbstract abstractContent = buildPublicationAbstract(publication);
    if (abstractContent != null && abstractContent.hasContent()) {
      aggregate.attachAbstract(abstractContent);
    }

    // 补充扩展标识符（PMC、PII 等非主要标识符）
    List<PublicationIdentifier> extendedIdentifiers = extractExtendedIdentifiers(publication);
    if (!extendedIdentifiers.isEmpty()) {
      aggregate.addExtendedIdentifiers(extendedIdentifiers);
    }

    return aggregate;
  }

  /// 解析媒介类型。
  ///
  /// @param mediaTypeStr 媒介类型字符串（如 "print", "electronic", "both"）
  /// @return 对应的 PublicationMedium 枚举，如果解析失败返回 null
  private PublicationMedium parsePublicationMedium(String mediaTypeStr) {
    if (mediaTypeStr == null || mediaTypeStr.isBlank()) {
      return null;
    }
    try {
      return PublicationMedium.fromCode(mediaTypeStr);
    } catch (IllegalArgumentException e) {
      log.warn("未知的媒介类型：{}，使用默认值 null", mediaTypeStr);
      return null;
    }
  }

  /// 构建摘要信息。
  ///
  /// 支持纯文本摘要和结构化摘要两种形式。
  ///
  /// @param publication 规范化文献
  /// @return 摘要值对象，如果无摘要返回 null
  private PublicationAbstract buildPublicationAbstract(CanonicalPublication publication) {
    Abstract abstractContent = publication.getAbstractContent();
    if (abstractContent == null) {
      return null;
    }

    String plainText = abstractContent.getText();
    String copyright = abstractContent.getCopyright();
    List<AbstractSection> sections = abstractContent.getSections();

    // 结构化摘要
    if (sections != null && !sections.isEmpty()) {
      Map<String, String> structuredSections = new LinkedHashMap<>();
      for (AbstractSection section : sections) {
        if (section.getLabel() != null && section.getContent() != null) {
          structuredSections.put(section.getLabel(), section.getContent());
        }
      }
      if (!structuredSections.isEmpty()) {
        // 同时有纯文本和结构化段落
        if (plainText != null && !plainText.isBlank()) {
          return PublicationAbstract.ofBoth(plainText, structuredSections, copyright);
        }
        return PublicationAbstract.ofStructured(structuredSections, copyright);
      }
    }

    // 纯文本摘要
    if (plainText != null && !plainText.isBlank()) {
      return PublicationAbstract.ofPlainText(plainText, copyright);
    }

    return null;
  }

  /// 提取扩展标识符（PMC、PII 等非主要标识符）。
  ///
  /// 排除 PMID 和 DOI（避免与主表冗余字段重复存储）。
  ///
  /// @param publication 规范化文献
  /// @return 扩展标识符列表
  private List<PublicationIdentifier> extractExtendedIdentifiers(CanonicalPublication publication) {
    List<Identifier> identifiers = publication.getIdentifiers();
    if (identifiers == null || identifiers.isEmpty()) {
      return List.of();
    }

    // 排除 PMID 和 DOI（主表字段是冗余副本，用于快速查询；扩展标识符表是完整数据源）
    Set<PublicationIdentifierType> excludedTypes =
        Set.of(PublicationIdentifierType.PMID, PublicationIdentifierType.DOI);

    List<PublicationIdentifier> extendedIds = new ArrayList<>();
    for (Identifier id : identifiers) {
      if (id.getType() != null && id.getValue() != null && !excludedTypes.contains(id.getType())) {
        try {
          extendedIds.add(PublicationIdentifier.of(id.getType(), id.getValue(), "PubMed"));
        } catch (IllegalArgumentException e) {
          log.warn("无效的扩展标识符：type={}, value={}，跳过", id.getType(), id.getValue());
        }
      }
    }
    return extendedIds;
  }

  /// 构建语言信息。
  ///
  /// PubMed 使用 ISO 639-3 三字母代码（如 "eng", "chi"），
  /// 通过 LanguageLookupPort 转换为项目标准 BCP 47 代码（如 "en", "zh"）。
  private LanguageInfo buildLanguageInfo(CanonicalPublication publication) {
    String iso639Code = publication.getLanguage();

    // 默认英语
    if (iso639Code == null || iso639Code.isBlank()) {
      return LanguageInfo.ofCode("en");
    }

    // 通过 LanguageLookupPort 解析语言代码
    String bcp47Code = languageLookupPort.resolve(iso639Code);

    // 如果解析失败（返回 null 或 "unknown"），使用默认英语
    if (bcp47Code == null || LanguageLookupPort.UNKNOWN_LANGUAGE.equals(bcp47Code)) {
      log.warn("无法解析语言代码：{}，使用默认值 en", iso639Code);
      return LanguageInfo.ofCode("en");
    }

    return LanguageInfo.ofCode(bcp47Code);
  }

  /// 解析出版状态。
  private PublicationStatus parsePublicationStatus(String status) {
    if (status == null || status.isBlank()) {
      return PublicationStatus.PUBMED; // 默认状态
    }

    try {
      return PublicationStatus.fromCode(status);
    } catch (IllegalArgumentException e) {
      log.warn("未知的出版状态：{}，使用默认值 PUBMED", status);
      return PublicationStatus.PUBMED;
    }
  }

  // ========== MeSH 数据处理 ==========

  /// 构建 MeSH 标引数据。
  ///
  /// 从 CanonicalPublication 提取 MeSH 标引，转换为 MeshHeadingData 列表供 Writer 写入关联表。
  /// 使用 UI 作为关联键，与项目其他 MeSH 关联表保持一致。
  ///
  /// @param publication 规范化文献
  /// @return MeSH 标引数据列表（可能为空，不会为 null）
  private List<MeshHeadingData> buildMeshHeadingData(CanonicalPublication publication) {
    List<MeshHeading> meshHeadings = publication.getMeshHeadings();
    if (meshHeadings == null || meshHeadings.isEmpty()) {
      return List.of();
    }

    List<MeshHeadingData> result = new ArrayList<>();
    int order = 1;

    for (MeshHeading heading : meshHeadings) {
      DescriptorName descriptor = heading.getDescriptorName();
      if (descriptor == null || descriptor.getUi() == null) {
        continue;
      }

      // 处理限定词
      List<QualifierData> qualifiers = buildQualifierData(heading.getQualifierNames());

      // 构建标引数据（直接使用 UI 作为关联键）
      MeshHeadingData headingData =
          MeshHeadingData.of(
              descriptor.getUi(),
              Boolean.TRUE.equals(descriptor.getMajorTopic()),
              order++,
              qualifiers);

      result.add(headingData);
    }

    return result;
  }

  /// 构建限定词数据。
  ///
  /// @param qualifierNames 限定词列表
  /// @return 限定词数据列表（保留原始顺序）
  private List<QualifierData> buildQualifierData(List<QualifierName> qualifierNames) {
    if (qualifierNames == null || qualifierNames.isEmpty()) {
      return List.of();
    }

    List<QualifierData> result = new ArrayList<>();
    int order = 1;
    for (QualifierName qualifier : qualifierNames) {
      if (qualifier == null || qualifier.getUi() == null) {
        continue;
      }

      result.add(
          QualifierData.of(
              qualifier.getUi(), Boolean.TRUE.equals(qualifier.getMajorTopic()), order++));
    }

    return result;
  }

  // ========== 关键词数据处理 ==========

  /// 构建关键词数据。
  ///
  /// 从 CanonicalPublication 提取所有关键词集合，展平为 KeywordData 列表。
  /// 每个关键词包含来源（author/publisher/indexer 等）、文本、主题标记和顺序。
  ///
  /// @param publication 规范化文献
  /// @return 关键词数据列表（可能为空，不会为 null）
  private List<KeywordData> buildKeywordData(CanonicalPublication publication) {
    List<KeywordSet> keywordSets = publication.getKeywords();
    if (keywordSets == null || keywordSets.isEmpty()) {
      return List.of();
    }

    List<KeywordData> result = new ArrayList<>();
    int order = 1;

    for (KeywordSet keywordSet : keywordSets) {
      String source = keywordSet.getSource();
      List<Keyword> keywords = keywordSet.getKeywords();

      if (keywords == null || keywords.isEmpty()) {
        continue;
      }

      for (Keyword keyword : keywords) {
        if (keyword.getTerm() == null || keyword.getTerm().isBlank()) {
          continue;
        }

        result.add(
            KeywordData.of(
                source, keyword.getTerm(), Boolean.TRUE.equals(keyword.getMajorTopic()), order++));
      }
    }

    return result;
  }

  // ========== 资助信息数据处理 ==========

  /// 构建资助信息数据。
  ///
  /// 从 CanonicalPublication 提取资助/基金信息，转换为 FundingData 列表。
  /// 通过 FunderLookupPort 匹配资助机构 ID，保留原始数据字段。
  ///
  /// **匹配逻辑**：
  ///
  /// 1. 优先使用 funderIdentifier（FundRef ID 或 ROR ID）精确匹配
  /// 2. 如果标识符匹配失败，尝试使用 funderName 名称匹配
  /// 3. 即使匹配失败，也保留原始数据用于后续分析
  ///
  /// @param publication 规范化文献
  /// @return 资助信息数据列表（可能为空，不会为 null）
  private List<FundingData> buildFundingData(CanonicalPublication publication) {
    List<FundingInfo> fundingList = publication.getFunding();
    if (fundingList == null || fundingList.isEmpty()) {
      return List.of();
    }

    List<FundingData> result = new ArrayList<>();
    int order = 1;

    for (FundingInfo funding : fundingList) {
      // 至少需要有资助机构名称或项目编号
      if ((funding.getFunderName() == null || funding.getFunderName().isBlank())
          && (funding.getGrantId() == null || funding.getGrantId().isBlank())) {
        continue;
      }

      // 通过 FunderLookupPort 匹配资助机构
      Long organizationId =
          funderLookupPort
              .findByPriority(funding.getFunderIdentifier(), funding.getFunderName())
              .orElse(null);

      result.add(
          FundingData.builder()
              .organizationId(organizationId)
              .grantId(funding.getGrantId())
              .funderNameRaw(funding.getFunderName())
              .funderAcronymRaw(funding.getFunderAcronym())
              .funderIdentifierRaw(funding.getFunderIdentifier())
              .countryRaw(funding.getCountry())
              .fundingOrder(order++)
              .provenanceCode(ProvenanceCode.PUBMED.getCode())
              .build());
    }

    return result;
  }

  // ========== 出版类型数据处理 ==========

  /// 构建出版类型数据。
  ///
  /// 从 CanonicalPublication 提取出版类型（如 Journal Article、Review 等），
  /// 转换为 PublicationTypeData 列表。每个类型包含标识符、文本描述和词表来源。
  ///
  /// @param publication 规范化文献
  /// @return 出版类型数据列表（可能为空，不会为 null）
  private List<PublicationTypeData> buildPublicationTypeData(CanonicalPublication publication) {
    List<PublicationType> types = publication.getPublicationTypes();
    if (types == null || types.isEmpty()) {
      return List.of();
    }

    List<PublicationTypeData> result = new ArrayList<>();
    int order = 1;

    for (PublicationType type : types) {
      // 至少需要有类型值
      if (type.getValue() == null || type.getValue().isBlank()) {
        continue;
      }

      result.add(
          PublicationTypeData.of(
              type.getId(), type.getValue(), type.getVocabularySource(), order++));
    }

    return result;
  }

  // ========== 补充 MeSH 概念数据处理 ==========

  /// 构建补充 MeSH 概念数据。
  ///
  /// 从 CanonicalPublication 提取 SupplMeshNameList，转换为 SupplMeshData 列表。
  /// 每个记录包含 SCR UI 和顺序号，用于关联到 cat_mesh_scr 表。
  ///
  /// **业务含义**：
  ///
  /// MeSH SCR（Supplementary Concept Records）用于标注 PubMed 文献中的：
  /// - 化学物质和药物（Type="Chemical"）
  /// - 疾病名称变体（Type="Disease"）
  /// - 实验方案（Type="Protocol"）
  ///
  /// @param publication 规范化文献
  /// @return 补充 MeSH 概念数据列表（可能为空，不会为 null）
  private List<SupplMeshData> buildSupplMeshData(CanonicalPublication publication) {
    List<CanonicalPublication.SupplMeshName> supplMeshNames = publication.getSupplMeshNames();
    if (supplMeshNames == null || supplMeshNames.isEmpty()) {
      return List.of();
    }

    List<SupplMeshData> result = new ArrayList<>();
    int order = 1;

    for (CanonicalPublication.SupplMeshName supplMesh : supplMeshNames) {
      if (supplMesh == null || supplMesh.getUi() == null) {
        continue;
      }

      result.add(SupplMeshData.of(supplMesh.getUi(), order++));
    }

    return result;
  }
}
