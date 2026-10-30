package dev.patra.catalog.infra.publication.batch;

import static dev.patra.catalog.infra.publication.support.StringUtils.trimToNull;

import dev.patra.catalog.domain.kernel.model.enums.ProvenanceCode;
import dev.patra.catalog.domain.kernel.model.enums.PublicationDateType;
import dev.patra.catalog.domain.kernel.model.enums.PublicationIdentifierType;
import dev.patra.catalog.domain.kernel.model.vo.Orcid;
import dev.patra.catalog.domain.kernel.model.vo.Pagination;
import dev.patra.catalog.domain.kernel.model.vo.VenueId;
import dev.patra.catalog.domain.organization.port.read.FunderLookupPort;
import dev.patra.catalog.domain.publication.model.aggregate.PublicationAggregate;
import dev.patra.catalog.domain.publication.model.aggregate.VenueInstanceAggregate;
import dev.patra.catalog.domain.publication.model.enums.IndexingStatus;
import dev.patra.catalog.domain.publication.model.enums.PublicationMedium;
import dev.patra.catalog.domain.publication.model.enums.PublicationStatus;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Abstract;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.AbstractSection;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Affiliation;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Author;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.DescriptorName;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.FundingInfo;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Identifier;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Journal;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Keyword;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.KeywordSet;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.MeshHeading;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.PublicationDates;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.PublicationType;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.QualifierName;
import dev.patra.catalog.domain.publication.model.vo.JournalInstanceParams;
import dev.patra.catalog.domain.publication.model.vo.LanguageInfo;
import dev.patra.catalog.domain.publication.model.vo.PublicationAbstract;
import dev.patra.catalog.domain.publication.model.vo.PublicationAbstractSection;
import dev.patra.catalog.domain.publication.model.vo.PublicationAuthorAffiliationSnapshot;
import dev.patra.catalog.domain.publication.model.vo.PublicationAuthorSnapshot;
import dev.patra.catalog.domain.publication.model.vo.PublicationIdentifier;
import dev.patra.catalog.domain.publication.model.vo.PublicationMetadata;
import dev.patra.catalog.domain.publication.port.gateway.VenueInstanceGateway;
import dev.patra.catalog.domain.publication.port.lookup.LanguageLookupPort;
import dev.patra.catalog.domain.venue.port.read.VenueLookupPort;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.AlternativeAbstractData;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.FundingData;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.InvestigatorData;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.KeywordData;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.MeshHeadingData;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.PersonalNameSubjectData;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.PublicationDateData;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.PublicationTypeData;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.QualifierData;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.SupplMeshData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

  /// ORCID 官方 URL 前缀（已大写，匹配前先将原始值大写以实现忽略大小写剥离）。
  private static final List<String> ORCID_URL_PREFIXES =
      List.of("HTTPS://ORCID.ORG/", "HTTP://ORCID.ORG/");

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
    return dates.getPublished().year();
  }

  /// 从 CanonicalPublication 提取出版月份。
  private Integer extractPublicationMonth(CanonicalPublication publication) {
    PublicationDates dates = publication.getDates();
    if (dates == null || dates.getPublished() == null) {
      return null;
    }
    return dates.getPublished().month();
  }

  /// 从 CanonicalPublication 提取出版日期。
  private Integer extractPublicationDay(CanonicalPublication publication) {
    PublicationDates dates = publication.getDates();
    if (dates == null || dates.getPublished() == null) {
      return null;
    }
    return dates.getPublished().day();
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

    // 补充引用数据：月 / 日按实际精度，页码与期刊 ISO 缩写
    aggregate.assignCitationDetails(
        extractPublicationMonth(publication),
        extractPublicationDay(publication),
        buildPagination(publication),
        publication.getJournal() != null ? publication.getJournal().getIsoAbbreviation() : null);

    // 补充摘要
    PublicationAbstract abstractContent = buildPublicationAbstract(publication);
    if (abstractContent != null && abstractContent.hasContent()) {
      aggregate.attachAbstract(abstractContent);
    }

    // 补充作者快照（唯一事实来源挂聚合；软关联 authorId 由 Repository 层填充）
    List<PublicationAuthorSnapshot> authorSnapshots = buildAuthorSnapshots(publication);
    if (!authorSnapshots.isEmpty()) {
      aggregate.attachAuthors(authorSnapshots);
    }

    // 补充扩展标识符（PMC、PII 等非主要标识符）
    List<PublicationIdentifier> extendedIdentifiers = extractExtendedIdentifiers(publication);
    if (!extendedIdentifiers.isEmpty()) {
      aggregate.addExtendedIdentifiers(extendedIdentifiers);
    }

    return aggregate;
  }

  /// 构建页码；XML 里没有 Pagination 时为 null。
  ///
  /// @param publication 规范化文献
  /// @return 页码，可空
  private Pagination buildPagination(CanonicalPublication publication) {
    CanonicalPublication.Pagination source = publication.getPagination();
    if (source == null) {
      return null;
    }
    Pagination pagination =
        Pagination.of(source.getMedlinePgn(), source.getStartPage(), source.getEndPage());
    return pagination.isEmpty() ? null : pagination;
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

  /// 构建摘要信息（两形态一等公民）。
  ///
  /// 判型规则：
  /// - 至少一个带 Label 的段落 → STRUCTURED（含混合形态，无 Label 段以 `label=null` 保位）
  /// - 全部无 Label → UNSTRUCTURED（段落不入库，文本合并落 plainText）
  /// - 无段落时退回 Canonical 层的 `text`（API 等其他来源可能只给整段文本）
  ///
  /// plainText 统一在此生成：带 Label 段拼为 `LABEL: text`，无 Label 段直接取 text，段间以 `\n` 连接。
  ///
  /// @param publication 规范化文献
  /// @return 摘要值对象，无摘要返回 null
  private PublicationAbstract buildPublicationAbstract(CanonicalPublication publication) {
    Abstract abstractContent = publication.getAbstractContent();
    if (abstractContent == null) {
      return null;
    }

    String copyright = abstractContent.getCopyright();
    List<PublicationAbstractSection> sections = toAbstractSections(abstractContent.getSections());

    if (sections.isEmpty()) {
      String plainText = abstractContent.getText();
      if (plainText != null && !plainText.isBlank()) {
        return PublicationAbstract.ofPlainText(plainText, copyright);
      }
      return null;
    }

    String plainText = joinSectionsAsPlainText(sections);
    boolean structured = sections.stream().anyMatch(PublicationAbstractSection::hasLabel);
    if (structured) {
      return PublicationAbstract.ofBoth(plainText, sections, copyright);
    }
    // 全部无 Label：判为非结构化摘要，段落不入库
    return PublicationAbstract.ofPlainText(plainText, copyright);
  }

  /// 将 Canonical 层段落转换为领域段落值对象（丢弃内容空白的段落）。
  ///
  /// @param rawSections Canonical 层段落列表（可能为 null）
  /// @return 领域段落列表（可能为空，不会为 null）
  private List<PublicationAbstractSection> toAbstractSections(List<AbstractSection> rawSections) {
    if (rawSections == null || rawSections.isEmpty()) {
      return List.of();
    }
    return rawSections.stream()
        .filter(section -> section != null && !isBlank(section.getContent()))
        .map(section -> PublicationAbstractSection.of(section.getLabel(), section.getContent()))
        .toList();
  }

  /// 段落拼接为纯文本（读端 plainText 的唯一生成点）。
  ///
  /// @param sections 段落列表
  /// @return 拼接后的纯文本
  private String joinSectionsAsPlainText(List<PublicationAbstractSection> sections) {
    return sections.stream()
        .map(
            section ->
                section.hasLabel() ? section.label() + ": " + section.text() : section.text())
        .collect(Collectors.joining("\n"));
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

  // ========== 作者快照构建 ==========

  /// 构建作者快照列表（姓名规则见 `PublicationAuthorSnapshot.deriveDisplayName`）。
  ///
  /// 全部姓名字段与集体名均空的作者跳过（不落库），跳过后顺序保持连续。
  /// ORCID 在此仅归一化存档；软关联 `authorId` 由 Repository 层批查填充。
  ///
  /// @param publication 规范化文献
  /// @return 作者快照列表（可能为空，不会为 null）
  private List<PublicationAuthorSnapshot> buildAuthorSnapshots(CanonicalPublication publication) {
    List<Author> authors = publication.getAuthors();
    if (authors == null || authors.isEmpty()) {
      return List.of();
    }

    List<PublicationAuthorSnapshot> snapshots = new ArrayList<>();
    int order = 1;
    for (Author author : authors) {
      if (author == null) {
        continue;
      }
      String lastName = trimToNull(author.getLastName());
      String foreName = trimToNull(author.getForeName());
      String initials = trimToNull(author.getInitials());
      String collectiveName = trimToNull(author.getOrganizationName());
      String displayName =
          PublicationAuthorSnapshot.deriveDisplayName(lastName, foreName, initials, collectiveName);
      if (displayName == null) {
        log.debug("跳过无有效姓名的作者：importBatch={}", importBatch);
        continue;
      }

      snapshots.add(
          PublicationAuthorSnapshot.builder()
              .order(order)
              .lastName(lastName)
              .foreName(foreName)
              .initials(initials)
              .suffix(trimToNull(author.getSuffix()))
              .collectiveName(collectiveName)
              .displayName(displayName)
              .orcid(normalizeOrcid(extractOrcid(author.getIdentifiers())))
              .firstAuthor(order == 1)
              .equalContribution(Boolean.TRUE.equals(author.getEqualContribution()))
              .affiliations(buildAffiliationSnapshots(author))
              .build());
      order++;
    }
    return snapshots;
  }

  /// 构建作者机构快照（保留文献侧顺序，1 起连续编号，空白机构原文跳过）。
  ///
  /// @param author 规范化作者
  /// @return 机构快照列表（可能为空，不会为 null）
  private List<PublicationAuthorAffiliationSnapshot> buildAffiliationSnapshots(Author author) {
    List<Affiliation> affiliations = author.getAffiliations();
    if (affiliations == null || affiliations.isEmpty()) {
      return List.of();
    }

    List<PublicationAuthorAffiliationSnapshot> snapshots = new ArrayList<>();
    int order = 1;
    for (Affiliation affiliation : affiliations) {
      String name = affiliation != null ? trimToNull(affiliation.getName()) : null;
      if (name == null) {
        continue;
      }
      snapshots.add(PublicationAuthorAffiliationSnapshot.of(order++, name));
    }
    return snapshots;
  }

  /// 归一化 ORCID：trim → 大写 → 剥离官方 URL 前缀（大写常量匹配）→ 格式与 ISO 7064 校验位校验。
  ///
  /// 任一校验不通过返回 null（脏数据不落库，而非抛异常中断整批导入）。
  ///
  /// @param raw ORCID 原始值（可能为 null）
  /// @return 归一化后的 ORCID，非法时返回 null
  private String normalizeOrcid(String raw) {
    String value = trimToNull(raw);
    if (value == null) {
      return null;
    }
    value = value.toUpperCase(Locale.ROOT);
    for (String prefix : ORCID_URL_PREFIXES) {
      if (value.startsWith(prefix)) {
        value = value.substring(prefix.length());
        break;
      }
    }
    if (!Orcid.isValid(value)) {
      log.debug("丢弃格式非法的 ORCID：raw={}, importBatch={}", raw, importBatch);
      return null;
    }
    Orcid orcid = Orcid.of(value);
    if (!orcid.isChecksumValid()) {
      log.debug("丢弃校验位非法的 ORCID：raw={}, importBatch={}", raw, importBatch);
      return null;
    }
    return orcid.value();
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
  /// PubMed 偶尔在同一标引下把同一限定词列两次（如 PMID 41601277 的 Glycine/Q000031，
  /// 第二次带 `AutoHM="Y"`），这里按 UI 合并：任一次为主要主题即为主要主题，
  /// 顺序取首次出现的位置。
  ///
  /// @param qualifierNames 限定词列表
  /// @return 限定词数据列表（按 UI 去重，保留首次出现的顺序）
  private List<QualifierData> buildQualifierData(List<QualifierName> qualifierNames) {
    if (qualifierNames == null || qualifierNames.isEmpty()) {
      return List.of();
    }

    Map<String, Boolean> majorTopicByUi = new LinkedHashMap<>();
    for (QualifierName qualifier : qualifierNames) {
      if (qualifier == null || qualifier.getUi() == null) {
        continue;
      }
      majorTopicByUi.merge(
          qualifier.getUi(), Boolean.TRUE.equals(qualifier.getMajorTopic()), Boolean::logicalOr);
    }

    List<QualifierData> result = new ArrayList<>();
    int order = 1;
    for (Map.Entry<String, Boolean> entry : majorTopicByUi.entrySet()) {
      result.add(QualifierData.of(entry.getKey(), entry.getValue(), order++));
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
      if (altAbstract == null) {
        continue;
      }

      // sections 可能为 null（starter-provenance 路径不填充），必须判空
      List<PublicationAbstractSection> sections = toAbstractSections(altAbstract.getSections());
      // 文本与段落都为空才跳过
      if (isBlank(altAbstract.getText()) && sections.isEmpty()) {
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

      // plainText 与主摘要同源同形态：有段落时由段落统一拼接，无段落才退回 Canonical 层的 text
      String plainText =
          sections.isEmpty() ? altAbstract.getText() : joinSectionsAsPlainText(sections);

      result.add(
          AlternativeAbstractData.builder()
              .languageCode(langCode)
              .abstractType(altAbstract.getType())
              .plainText(plainText)
              .sections(sections)
              .copyright(altAbstract.getCopyright())
              .abstractOrder(order++)
              .build());
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
  /// - 日期按实际精度（[dev.patra.catalog.domain.publication.model.vo.PartialDate]），缺的月 / 日不补
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
          PublicationDateData.primaryFromPartialDate(
              PublicationDateType.PUBLISHED.getCode(), dates.getPublished(), order++));
    }

    // electronic → EPUBLISH
    if (dates.getElectronic() != null) {
      result.add(
          PublicationDateData.fromPartialDate(
              PublicationDateType.EPUBLISH.getCode(), dates.getElectronic(), order++));
    }

    // received → RECEIVED
    if (dates.getReceived() != null) {
      result.add(
          PublicationDateData.fromPartialDate(
              PublicationDateType.RECEIVED.getCode(), dates.getReceived(), order++));
    }

    // accepted → ACCEPTED
    if (dates.getAccepted() != null) {
      result.add(
          PublicationDateData.fromPartialDate(
              PublicationDateType.ACCEPTED.getCode(), dates.getAccepted(), order++));
    }

    // revised → REVISED
    if (dates.getRevised() != null) {
      result.add(
          PublicationDateData.fromPartialDate(
              PublicationDateType.REVISED.getCode(), dates.getRevised(), order++));
    }

    // created → ENTREZ_DATE（进入 PubMed 数据库日期）
    if (dates.getCreated() != null) {
      result.add(
          PublicationDateData.fromPartialDate(
              PublicationDateType.ENTREZ_DATE.getCode(), dates.getCreated(), order++));
    }

    // pmcRelease → PMC_RELEASE（PMC 放开日期，全文抓取据此安排再查时间）
    if (dates.getPmcRelease() != null) {
      result.add(
          PublicationDateData.fromPartialDate(
              PublicationDateType.PMC_RELEASE.getCode(), dates.getPmcRelease(), order++));
    }

    // completed → OTHER
    if (dates.getCompleted() != null) {
      result.add(
          PublicationDateData.fromPartialDate(
              PublicationDateType.OTHER.getCode(), dates.getCompleted(), order++));
    }

    // indexed → OTHER
    if (dates.getIndexed() != null) {
      result.add(
          PublicationDateData.fromPartialDate(
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
            nullToEmpty(lastName).toLowerCase(Locale.ROOT).trim(),
            nullToEmpty(foreName).toLowerCase(Locale.ROOT).trim(),
            nullToEmpty(orcid).toLowerCase(Locale.ROOT).trim());
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
