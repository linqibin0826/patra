package com.patra.catalog.infra.batch.publication;

import static dev.linqibin.commons.util.StringUtils.trimToNull;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import com.patra.catalog.domain.model.enums.IndexingStatus;
import com.patra.catalog.domain.model.enums.PublicationDateType;
import com.patra.catalog.domain.model.enums.PublicationMedium;
import com.patra.catalog.domain.model.enums.PublicationStatus;
import com.patra.catalog.domain.model.vo.publication.LanguageInfo;
import com.patra.catalog.domain.model.vo.publication.PublicationAbstract;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifier;
import com.patra.catalog.domain.model.vo.publication.PublicationMetadata;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venueinstance.JournalInstanceParams;
import com.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import com.patra.catalog.domain.port.lookup.FunderLookupPort;
import com.patra.catalog.domain.port.lookup.LanguageLookupPort;
import com.patra.catalog.domain.port.lookup.VenueLookupPort;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.AlternativeAbstractData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.FundingData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.InvestigatorData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.KeywordData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.MeshHeadingData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.PersonalNameSubjectData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.PublicationDateData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.PublicationTypeData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.QualifierData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.SupplMeshData;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
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
/// 1. 标题容错：`title -> originalTitle -> 跳过`
/// 2. Venue 匹配：通过 NLM ID/ISSN 匹配载体
/// 3. VenueInstance 创建：获取或创建载体实例（卷期）
/// 4. 聚合根构建：将 CanonicalPublication 转换为 PublicationAggregate
/// 5. 关联数据处理：解析 MeSH/关键词/资助等数据
///
/// **跳过条件**：
///
/// - 缺少 PMID
/// - 标题与原始标题均为空
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

  private final VenueLookupPort venueLookupPort;
  private final VenueInstanceGateway venueInstanceGateway;
  private final LanguageLookupPort languageLookupPort;
  private final FunderLookupPort funderLookupPort;
  private final String importBatch;

  @Override
  public PublicationImportResult process(CanonicalPublication publication) throws Exception {
    String pmid = extractPmid(publication);
    String doi = extractDoi(publication);

    if (pmid == null) {
      log.debug("跳过缺少 PMID 的文献");
      return null;
    }

    String effectiveTitle = resolveEffectiveTitle(publication);
    if (effectiveTitle == null) {
      log.warn(
          "跳过缺少标题的文献：PMID={}, DOI={}, importBatch={}, reason=TITLE_MISSING",
          pmid,
          doi,
          importBatch);
      return null;
    }
    if (isBlank(publication.getTitle()) && !isBlank(publication.getOriginalTitle())) {
      log.debug(
          "文献标题缺失，回退使用 originalTitle：PMID={}, DOI={}, importBatch={}", pmid, doi, importBatch);
    }

    // 1. Venue 匹配
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

    // 2. VenueInstance 创建/获取
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

    // 3. 构建 PublicationAggregate
    PublicationAggregate aggregate =
        buildPublicationAggregate(
            publication, pmid, doi, venueId, venueInstance, pubYear, effectiveTitle);

    // 4. 处理关联数据
    List<MeshHeadingData> meshHeadings = buildMeshHeadingData(publication);
    List<SupplMeshData> supplMeshNames = buildSupplMeshData(publication);
    List<KeywordData> keywords = buildKeywordData(publication);
    List<FundingData> funding = buildFundingData(publication);
    List<PublicationTypeData> publicationTypes = buildPublicationTypeData(publication);
    List<AlternativeAbstractData> alternativeAbstracts = buildAlternativeAbstractData(publication);
    List<PublicationDateData> dates = buildPublicationDateData(publication);
    List<InvestigatorData> investigators = buildInvestigatorData(publication);
    List<PersonalNameSubjectData> personalNameSubjects = buildPersonalNameSubjectData(publication);

    // 5. 构建元数据
    PublicationMetadata metadata = buildMetadata(publication);

    PublicationImportResult result =
        PublicationImportResult.ofComplete(
            aggregate,
            metadata,
            meshHeadings,
            keywords,
            funding,
            publicationTypes,
            supplMeshNames,
            alternativeAbstracts,
            dates,
            investigators,
            personalNameSubjects);
    return result;
  }

  /// 解析文献有效标题。
  ///
  /// 标题优先级：
  /// 1. `title`（ArticleTitle）
  /// 2. `originalTitle`（VernacularTitle）
  ///
  /// 两者均为空时返回 `null`。
  private String resolveEffectiveTitle(CanonicalPublication publication) {
    String title = trimToNull(publication.getTitle());
    if (title != null) {
      return title;
    }
    return trimToNull(publication.getOriginalTitle());
  }

  /// 判断字符串是否为空白。
  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
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
      String doi,
      VenueId venueId,
      VenueInstanceAggregate venueInstance,
      Integer pubYear,
      String effectiveTitle) {

    // 构建语言信息
    LanguageInfo languageInfo = buildLanguageInfo(publication);

    // 解析出版状态
    PublicationStatus publicationStatus =
        parsePublicationStatus(publication.getPublicationStatus());

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
            effectiveTitle,
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

    // 如果解析失败（返回 null 或 "unknown"），使用默认英语，但保留原始代码
    if (bcp47Code == null || LanguageLookupPort.UNKNOWN_LANGUAGE.equals(bcp47Code)) {
      log.warn("无法解析语言代码：{}，使用默认值 en", iso639Code);
      return LanguageInfo.of(iso639Code, "en");
    }

    return LanguageInfo.of(iso639Code, bcp47Code);
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

  // ========== 翻译摘要数据处理 ==========

  /// 构建翻译摘要数据。
  ///
  /// 从 CanonicalPublication 提取 alternativeAbstracts（OtherAbstract），
  /// 转换为 AlternativeAbstractData 列表供 Writer 写入关联表。
  ///
  /// **业务含义**：
  ///
  /// PubMed 的 OtherAbstract 元素包含其他语言版本的摘要，常见来源：
  /// - Publisher：出版商提供的官方翻译
  /// - AIMSHP/KIEML：专业翻译机构
  /// - plain-language-summary：面向患者的通俗语言摘要
  ///
  /// @param publication 规范化文献
  /// @return 翻译摘要数据列表（可能为空，不会为 null）
  private List<AlternativeAbstractData> buildAlternativeAbstractData(
      CanonicalPublication publication) {
    List<CanonicalPublication.AlternativeAbstract> alternativeAbstracts =
        publication.getAlternativeAbstracts();
    if (alternativeAbstracts == null || alternativeAbstracts.isEmpty()) {
      return List.of();
    }

    List<AlternativeAbstractData> result = new ArrayList<>();
    int order = 1;

    for (CanonicalPublication.AlternativeAbstract altAbstract : alternativeAbstracts) {
      if (altAbstract == null || altAbstract.getText() == null || altAbstract.getText().isBlank()) {
        continue;
      }

      // 将 ISO 639-3 代码转换为 BCP 47（如 "chi" → "zh"），转换失败保留原始代码
      String langCode = altAbstract.getLanguage();
      if (langCode != null && !langCode.isBlank()) {
        String resolved = languageLookupPort.resolve(langCode);
        if (resolved != null && !LanguageLookupPort.UNKNOWN_LANGUAGE.equals(resolved)) {
          langCode = resolved;
        }
      }

      result.add(
          AlternativeAbstractData.of(
              langCode,
              altAbstract.getType(),
              altAbstract.getText(),
              altAbstract.getCopyright(),
              order++));
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

  // ========== 日期数据处理 ==========

  /// 构建文献日期数据。
  ///
  /// 从 CanonicalPublication 提取 PublicationDates，转换为 PublicationDateData 列表。
  /// 支持 8 种日期类型的映射：
  ///
  /// | CanonicalPublication.PublicationDates | PublicationDateType |
  /// |---------------------------------------|---------------------|
  /// | published | PUBLISHED |
  /// | electronic | EPUBLISH |
  /// | received | RECEIVED |
  /// | accepted | ACCEPTED |
  /// | revised | REVISED |
  /// | created | ENTREZ_DATE |
  /// | completed | OTHER |
  /// | indexed | OTHER |
  ///
  /// **特殊处理**：
  ///
  /// - `published` 日期标记为 `isPrimary=true`（主要发表日期）
  /// - 所有日期从 `LocalDate` 转换，精度均为 `day`
  ///
  /// @param publication 规范化文献
  /// @return 日期数据列表（可能为空，不会为 null）
  private List<PublicationDateData> buildPublicationDateData(CanonicalPublication publication) {
    PublicationDates dates = publication.getDates();
    if (dates == null) {
      return List.of();
    }

    List<PublicationDateData> result = new ArrayList<>();
    int order = 1;

    // published → PUBLISHED（主要发表日期）
    if (dates.getPublished() != null) {
      result.add(
          PublicationDateData.primaryFromLocalDate(
              PublicationDateType.PUBLISHED.getCode(), dates.getPublished(), order++));
    }

    // electronic → EPUBLISH
    if (dates.getElectronic() != null) {
      result.add(
          PublicationDateData.fromLocalDate(
              PublicationDateType.EPUBLISH.getCode(), dates.getElectronic(), order++));
    }

    // received → RECEIVED
    if (dates.getReceived() != null) {
      result.add(
          PublicationDateData.fromLocalDate(
              PublicationDateType.RECEIVED.getCode(), dates.getReceived(), order++));
    }

    // accepted → ACCEPTED
    if (dates.getAccepted() != null) {
      result.add(
          PublicationDateData.fromLocalDate(
              PublicationDateType.ACCEPTED.getCode(), dates.getAccepted(), order++));
    }

    // revised → REVISED
    if (dates.getRevised() != null) {
      result.add(
          PublicationDateData.fromLocalDate(
              PublicationDateType.REVISED.getCode(), dates.getRevised(), order++));
    }

    // created → ENTREZ_DATE（进入 PubMed 数据库日期）
    if (dates.getCreated() != null) {
      result.add(
          PublicationDateData.fromLocalDate(
              PublicationDateType.ENTREZ_DATE.getCode(), dates.getCreated(), order++));
    }

    // completed → OTHER
    if (dates.getCompleted() != null) {
      result.add(
          PublicationDateData.fromLocalDate(
              PublicationDateType.OTHER.getCode(), dates.getCompleted(), order++));
    }

    // indexed → OTHER
    if (dates.getIndexed() != null) {
      result.add(
          PublicationDateData.fromLocalDate(
              PublicationDateType.OTHER.getCode(), dates.getIndexed(), order++));
    }

    return result;
  }

  // ========== 元数据处理 ==========

  /// 构建文献元数据。
  ///
  /// 从 CanonicalPublication 提取元数据信息，转换为 PublicationMetadata 值对象。
  /// 包含索引状态、数据溯源、以及 PubMed 特有的 owner 和 citationSubset 信息。
  ///
  /// @param publication 规范化文献
  /// @return 元数据值对象
  private PublicationMetadata buildMetadata(CanonicalPublication publication) {
    CanonicalPublication.PublicationMetadata sourceMetadata = publication.getMetadata();

    // 解析 IndexingStatus
    IndexingStatus indexingStatus = null;
    String indexingMethod = null;
    String owner = null;
    String citationSubset = null;

    if (sourceMetadata != null) {
      indexingStatus = parseIndexingStatus(sourceMetadata.getStatus());
      indexingMethod = sourceMetadata.getIndexingMethod();
      owner = sourceMetadata.getOwner();
      citationSubset = sourceMetadata.getCitationSubset();
    }

    return PublicationMetadata.builder()
        .indexingStatus(indexingStatus)
        .indexingMethod(indexingMethod)
        .dataSource(ProvenanceCode.PUBMED)
        .importBatch(this.importBatch)
        .importDate(Instant.now())
        .owner(owner)
        .citationSubset(citationSubset)
        .build();
  }

  /// 解析 PubMed Status 为 IndexingStatus 枚举。
  ///
  /// PubMed MedlineCitation.Status 属性的可能值映射：
  ///
  /// | PubMed Status | IndexingStatus |
  /// |---------------|----------------|
  /// | MEDLINE | MEDLINE |
  /// | PubMed-not-MEDLINE | PUBMED_NOT_MEDLINE |
  /// | In-Process | IN_PROCESS |
  /// | In-Data-Review | IN_DATA_REVIEW |
  /// | Publisher | PENDING |
  /// | OLDMEDLINE | OLDMEDLINE |
  ///
  /// @param pubmedStatus PubMed MedlineCitation.Status 值
  /// @return IndexingStatus 枚举，无法识别时返回 null
  private IndexingStatus parseIndexingStatus(String pubmedStatus) {
    if (pubmedStatus == null || pubmedStatus.isBlank()) {
      return null;
    }

    String normalized = pubmedStatus.trim();

    return switch (normalized) {
      case "MEDLINE" -> IndexingStatus.MEDLINE;
      case "PubMed-not-MEDLINE" -> IndexingStatus.PUBMED_NOT_MEDLINE;
      case "In-Process" -> IndexingStatus.IN_PROCESS;
      case "In-Data-Review" -> IndexingStatus.IN_DATA_REVIEW;
      case "Publisher" -> IndexingStatus.PENDING;
      case "OLDMEDLINE" -> IndexingStatus.OLDMEDLINE;
      default -> {
        log.warn("未知的 PubMed Status: {}，使用 null", normalized);
        yield null;
      }
    };
  }

  // ========== 研究者数据处理 ==========

  /// 构建研究者数据。
  ///
  /// 从 CanonicalPublication 提取 investigators（非作者研究人员），
  /// 转换为 InvestigatorData 列表供 Writer 写入关联表。
  ///
  /// **业务含义**：
  ///
  /// Investigator 是参与研究但未列为文章作者的研究人员，常见于：
  /// - 大型临床试验（Principal Investigator、Co-Investigator）
  /// - 多中心研究（Site Investigator）
  /// - 协作研究组（Consortium Member）
  ///
  /// **去重策略**：
  ///
  /// 计算 dedupKey = MD5(LOWER(lastName) + "|" + LOWER(foreName) + "|" + LOWER(COALESCE(orcid, "")))
  /// Writer 使用此 key 进行去重匹配。
  ///
  /// @param publication 规范化文献
  /// @return 研究者数据列表（可能为空，不会为 null）
  private List<InvestigatorData> buildInvestigatorData(CanonicalPublication publication) {
    List<CanonicalPublication.Investigator> investigators = publication.getInvestigators();
    if (investigators == null || investigators.isEmpty()) {
      return List.of();
    }

    List<InvestigatorData> result = new ArrayList<>();
    int order = 1;

    for (CanonicalPublication.Investigator inv : investigators) {
      // 跳过无效记录：lastName 和 foreName 都为空
      if (isInvalidInvestigator(inv)) {
        continue;
      }

      // 提取 ORCID（从标识符列表中查找）
      String orcid = extractOrcid(inv.getIdentifiers());

      // 提取第一个机构名称
      String affiliationName = extractFirstAffiliationName(inv.getAffiliations());

      // 计算去重键
      String dedupKey = calculateDedupKey(inv.getLastName(), inv.getForeName(), orcid);

      result.add(
          InvestigatorData.builder()
              .lastName(inv.getLastName())
              .foreName(inv.getForeName())
              .initials(inv.getInitials())
              .suffix(inv.getSuffix())
              .orcid(orcid)
              .affiliationName(affiliationName)
              .dedupKey(dedupKey)
              .orderNum(order++)
              .build());
    }

    return result;
  }

  /// 检查研究者是否无效。
  ///
  /// 无效条件：lastName 和 foreName 都为空或 blank。
  private boolean isInvalidInvestigator(CanonicalPublication.Investigator inv) {
    boolean lastNameEmpty = inv.getLastName() == null || inv.getLastName().isBlank();
    boolean foreNameEmpty = inv.getForeName() == null || inv.getForeName().isBlank();
    return lastNameEmpty && foreNameEmpty;
  }

  /// 从标识符列表中提取 ORCID。
  ///
  /// @param identifiers 标识符列表
  /// @return ORCID 值，如果未找到返回 null
  private String extractOrcid(List<Identifier> identifiers) {
    if (identifiers == null || identifiers.isEmpty()) {
      return null;
    }
    return identifiers.stream()
        .filter(id -> PublicationIdentifierType.ORCID == id.getType())
        .map(Identifier::getValue)
        .findFirst()
        .orElse(null);
  }

  /// 提取第一个机构名称。
  ///
  /// @param affiliations 机构列表
  /// @return 第一个机构名称，如果列表为空返回 null
  private String extractFirstAffiliationName(List<CanonicalPublication.Affiliation> affiliations) {
    if (affiliations == null || affiliations.isEmpty()) {
      return null;
    }
    CanonicalPublication.Affiliation first = affiliations.getFirst();
    return first != null ? first.getName() : null;
  }

  /// 计算研究者去重键（MD5 哈希）。
  ///
  /// **计算规则**：
  /// `MD5(LOWER(lastName) + "|" + LOWER(foreName) + "|" + LOWER(COALESCE(orcid, "")))`
  ///
  /// @param lastName 姓
  /// @param foreName 名
  /// @param orcid ORCID（可能为 null）
  /// @return 32 位小写 MD5 哈希字符串
  private String calculateDedupKey(String lastName, String foreName, String orcid) {
    String raw =
        String.join(
            "|",
            nullToEmpty(lastName).toLowerCase().trim(),
            nullToEmpty(foreName).toLowerCase().trim(),
            nullToEmpty(orcid).toLowerCase().trim());
    return md5Hex(raw);
  }

  /// 将 null 转换为空字符串。
  private String nullToEmpty(String s) {
    return s != null ? s : "";
  }

  /// 计算字符串的 MD5 哈希值（32 位小写十六进制）。
  private String md5Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      // MD5 是 Java 标准算法，不应该抛出此异常
      throw new IllegalStateException("MD5 algorithm not available", e);
    }
  }

  // ========== 人物主题数据处理 ==========

  /// 构建人物主题数据。
  ///
  /// 从 CanonicalPublication 提取 personalNameSubjects（主题人物），
  /// 转换为 PersonalNameSubjectData 列表供 Writer 写入关联表。
  ///
  /// **业务含义**：
  ///
  /// PersonalNameSubject 是文献内容描述的对象（而非文献作者），常见于：
  /// - 传记文献：描述某人生平的文章
  /// - 历史类文献：涉及历史人物的研究
  /// - 纪念类文献：纪念某位学者或研究者的文章
  /// - 案例报告：以特定患者为主题的医学报告（已匿名）
  ///
  /// @param publication 规范化文献
  /// @return 人物主题数据列表（可能为空，不会为 null）
  private List<PersonalNameSubjectData> buildPersonalNameSubjectData(
      CanonicalPublication publication) {
    List<CanonicalPublication.PersonalNameSubject> subjects = publication.getPersonalNameSubjects();
    if (subjects == null || subjects.isEmpty()) {
      return List.of();
    }

    List<PersonalNameSubjectData> result = new ArrayList<>();
    int order = 1;

    for (CanonicalPublication.PersonalNameSubject subject : subjects) {
      // 跳过无效记录：lastName 和 foreName 都为空
      if (isInvalidPersonalNameSubject(subject)) {
        continue;
      }

      // 注意：CanonicalPublication.PersonalNameSubject 模型较简单，
      // 不包含 dates、description、subjectType、identifier 字段。
      // 这些字段在数据库表中预留，留待后续数据源扩展时使用。
      result.add(
          PersonalNameSubjectData.of(
              subject.getLastName(),
              subject.getForeName(),
              subject.getInitials(),
              subject.getSuffix(),
              null, // dates - 预留字段
              null, // description - 预留字段
              null, // subjectType - 预留字段
              null, // identifier - 预留字段
              order++));
    }

    return result;
  }

  /// 检查人物主题是否无效。
  ///
  /// 无效条件：lastName 和 foreName 都为空或 blank。
  private boolean isInvalidPersonalNameSubject(CanonicalPublication.PersonalNameSubject subject) {
    boolean lastNameEmpty = subject.getLastName() == null || subject.getLastName().isBlank();
    boolean foreNameEmpty = subject.getForeName() == null || subject.getForeName().isBlank();
    return lastNameEmpty && foreNameEmpty;
  }
}
