package com.patra.starter.provenance.pubmed.converter;

import com.patra.starter.provenance.pubmed.model.response.PubmedPublication;
import com.patra.starter.provenance.pubmed.model.response.PubmedPublication.Article;
import com.patra.starter.provenance.pubmed.model.response.PubmedPublication.Author;
import com.patra.starter.provenance.pubmed.model.response.PubmedPublication.Journal;
import com.patra.starter.provenance.pubmed.model.response.PubmedPublication.Journal.JournalIssue;
import com.patra.starter.provenance.pubmed.model.response.PubmedPublication.Journal.PubDate;
import com.patra.starter.provenance.pubmed.model.response.PubmedPublication.MedlineJournalInfo;
import com.patra.starter.provenance.pubmed.model.response.PubmedPublication.PubmedData;
import com.patra.starter.provenance.pubmed.model.response.PubmedPublication.PubmedData.ArticleId;
import dev.linqibin.patra.common.model.CanonicalPublication;
import dev.linqibin.patra.common.model.enums.PublicationIdentifierType;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/// PubMed文章转换器
///
/// 将 {@link PubmedPublication} 响应映射为 {@link CanonicalPublication} 标准出版物模型。
/// 集中所有字段提取逻辑，使下游组件能够操作稳定的共享内核模型。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class PubmedPublicationConverter {

  /// 将PubMed文章转换为标准化出版物模型
  ///
  /// @param article PubMed文章响应
  /// @return 标准化的文献表示
  public CanonicalPublication toCanonicalPublication(PubmedPublication article) {
    if (article == null) {
      return null;
    }
    if (log.isDebugEnabled()) {
      log.debug("Converting PubMed article to CanonicalPublication pmid={}", article.pmid());
    }
    Article citation = article.article();
    return CanonicalPublication.builder()
        .title(citation != null ? citation.title() : null)
        .originalTitle(citation != null ? citation.vernacularTitle() : null)
        .language(citation != null ? citation.language() : null)
        .publicationTypes(extractPublicationTypes(article))
        .publicationStatus(article.pubmedData().publicationStatus())
        .mediaType(citation != null ? citation.pubModel() : null)
        .abstractContent(extractAbstract(citation))
        .alternativeAbstracts(convertAlternativeAbstracts(article))
        .authors(convertAuthors(article))
        .authorsComplete(extractAuthorsComplete(citation))
        .investigators(convertInvestigators(article))
        .personalNameSubjects(convertPersonalNameSubjects(article))
        .journal(convertJournal(article))
        .identifiers(buildIdentifiers(article))
        .meshHeadings(convertMeshHeadings(article))
        .supplMeshNames(convertSupplMeshNames(article))
        .keywords(extractKeywords(article))
        .substances(convertSubstances(article))
        .genes(convertGenes(article))
        .pagination(convertPagination(article))
        .dates(extractPublicationDates(article))
        .publicationHistory(extractPublicationHistory(article))
        .funding(convertFunding(article))
        .externalReferences(convertExternalReferences(article))
        .supplementalObjects(convertSupplementalObjects(article))
        .citationCount(article.pubmedData().pmcRefCount())
        .numberOfReferences(article.numberOfReferences())
        .references(convertReferences(article))
        .conflictOfInterestStatement(article.coiStatement())
        .relatedItems(convertRelatedItems(article))
        .metadata(extractMetadata(article))
        .build();
  }

  private CanonicalPublication.Abstract extractAbstract(Article article) {
    if (article == null || CollectionUtils.isEmpty(article.abstractSections())) {
      return null;
    }

    List<CanonicalPublication.AbstractSection> sections =
        article.abstractSections().stream()
            .filter(section -> StringUtils.hasText(section.text()))
            .map(
                section ->
                    CanonicalPublication.AbstractSection.builder()
                        .label(section.label())
                        .category(section.nlmCategory())
                        .content(section.text())
                        .build())
            .collect(Collectors.toList());

    if (sections.isEmpty()) {
      return null;
    }

    // 构建纯文本版本（向后兼容）
    String text =
        article.abstractSections().stream()
            .map(
                section -> {
                  String content = section.text();
                  if (!StringUtils.hasText(content)) {
                    return null;
                  }
                  if (StringUtils.hasText(section.label())) {
                    return section.label() + ": " + content;
                  }
                  return content;
                })
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("\n"));

    return CanonicalPublication.Abstract.builder()
        .text(text)
        .sections(sections)
        .copyright(article.copyrightInformation())
        .build();
  }

  /// 转换作者列表
  ///
  /// @param article PubMed文章
  /// @return 标准化的作者列表，如果没有作者则返回空列表
  private List<CanonicalPublication.Author> convertAuthors(PubmedPublication article) {
    Article citationArticle = article.article();
    if (citationArticle == null || CollectionUtils.isEmpty(citationArticle.authors())) {
      return List.of();
    }

    List<CanonicalPublication.Author> canonicalAuthors = new ArrayList<>();
    for (Author author : citationArticle.authors()) {
      // 转换机构信息（保留 ROR/Ringgold 等标识符）
      List<CanonicalPublication.Affiliation> affiliations = null;
      if (!CollectionUtils.isEmpty(author.affiliationInfo())) {
        affiliations =
            author.affiliationInfo().stream()
                .filter(info -> StringUtils.hasText(info.value()))
                .map(this::convertAffiliationInfo)
                .collect(Collectors.toList());
        if (affiliations.isEmpty()) {
          affiliations = null;
        }
      }

      // 转换作者标识符（ORCID、ResearcherID等）
      List<CanonicalPublication.Identifier> authorIdentifiers = null;
      if (!CollectionUtils.isEmpty(author.identifiers())) {
        authorIdentifiers =
            author.identifiers().stream()
                .filter(id -> StringUtils.hasText(id.source()) && StringUtils.hasText(id.value()))
                .map(
                    id ->
                        CanonicalPublication.Identifier.builder()
                            .type(PublicationIdentifierType.fromCodeOrOther(id.source()))
                            .value(id.value())
                            .build())
                .collect(Collectors.toList());
        if (authorIdentifiers.isEmpty()) {
          authorIdentifiers = null;
        }
      }

      // 处理同等贡献作者
      Boolean equalContribution = null;
      if (StringUtils.hasText(author.equalContrib())) {
        equalContribution = "Y".equalsIgnoreCase(author.equalContrib());
      }

      // 处理作者信息有效性
      Boolean valid = null;
      if (StringUtils.hasText(author.validYN())) {
        valid = "Y".equalsIgnoreCase(author.validYN());
      }

      canonicalAuthors.add(
          CanonicalPublication.Author.builder()
              .lastName(author.lastName())
              .foreName(author.foreName())
              .initials(author.initials())
              .suffix(author.suffix())
              .organizationName(author.collectiveName())
              .equalContribution(equalContribution)
              .valid(valid)
              .affiliations(affiliations)
              .identifiers(authorIdentifiers)
              .build());
    }

    return canonicalAuthors;
  }

  /// 转换单个机构信息（保留标识符）。
  ///
  /// @param info PubMed 机构信息
  /// @return 标准化的机构信息
  private CanonicalPublication.Affiliation convertAffiliationInfo(Author.AffiliationInfo info) {
    // 提取机构标识符（ROR、Ringgold、GRID 等）
    List<CanonicalPublication.Identifier> identifiers = null;
    if (!CollectionUtils.isEmpty(info.identifiers())) {
      identifiers =
          info.identifiers().stream()
              .filter(id -> StringUtils.hasText(id.source()) && StringUtils.hasText(id.value()))
              .map(
                  id ->
                      CanonicalPublication.Identifier.builder()
                          .type(PublicationIdentifierType.fromCodeOrOther(id.source()))
                          .value(id.value())
                          .build())
              .collect(Collectors.toList());
      if (identifiers.isEmpty()) {
        identifiers = null;
      }
    }

    return CanonicalPublication.Affiliation.builder()
        .name(info.value())
        .identifiers(identifiers)
        .build();
  }

  /// 转换期刊信息
  ///
  /// @param article PubMed文章
  /// @return 标准化的期刊信息，如果没有期刊信息则返回null
  private CanonicalPublication.Journal convertJournal(PubmedPublication article) {
    Article citation = article.article();
    Journal journal = citation != null ? citation.journal() : null;
    MedlineJournalInfo medline = article.journalInfo();

    if (journal == null && medline == null) {
      return null;
    }

    // 提取ISSN（优先使用Journal中的ISSN）
    String issn =
        StringUtils.hasText(journal != null ? journal.issn() : null)
            ? journal.issn()
            : (medline != null ? medline.issnLinking() : null);

    // 提取ISSN类型
    String issnType = journal != null ? journal.issnType() : null;

    // 提取期刊标题（优先使用Journal中的标题）
    String title =
        StringUtils.hasText(journal != null ? journal.title() : null)
            ? journal.title()
            : (medline != null ? medline.medlineTa() : null);

    // 提取ISO缩写
    String isoAbbreviation = journal != null ? journal.isoAbbreviation() : null;

    // 提取Medline缩写
    String medlineAbbreviation = medline != null ? medline.medlineTa() : null;

    // 提取期刊国家
    String country = medline != null ? medline.country() : null;

    // 提取NLM唯一标识
    String nlmUniqueId = medline != null ? medline.nlmUniqueId() : null;

    // 提取ISSN-L（Linking ISSN）
    String issnLinking = medline != null ? medline.issnLinking() : null;

    // 提取引用媒介（来自JournalIssue）
    String citedMedium = null;
    if (journal != null && journal.journalIssue() != null) {
      citedMedium = journal.journalIssue().citedMedium();
    }

    // 提取卷号和期号
    String volume = null;
    String issue = null;
    if (journal != null && journal.journalIssue() != null) {
      volume = journal.journalIssue().volume();
      issue = journal.journalIssue().issue();
    }

    return CanonicalPublication.Journal.builder()
        .title(title)
        .isoAbbreviation(isoAbbreviation)
        .medlineAbbreviation(medlineAbbreviation)
        .issn(issn)
        .issnType(issnType)
        .issnLinking(issnLinking)
        .nlmUniqueId(nlmUniqueId)
        .citedMedium(citedMedium)
        .volume(volume)
        .issue(issue)
        .country(country)
        .publisher(null)
        .build();
  }

  /// 构建标识符列表
  ///
  /// @param article PubMed文章
  /// @return 标识符列表，如果没有标识符则返回null
  private List<CanonicalPublication.Identifier> buildIdentifiers(PubmedPublication article) {
    List<CanonicalPublication.Identifier> identifiers = new ArrayList<>();

    // 添加 PMID
    if (StringUtils.hasText(article.pmid())) {
      identifiers.add(
          CanonicalPublication.Identifier.builder()
              .type(PublicationIdentifierType.PMID)
              .value(article.pmid())
              .build());
    }

    // 从 PubmedData.ArticleIdList 提取 DOI 和 PMC
    for (ArticleId id : article.articleIds()) {
      if (!StringUtils.hasText(id.type()) || !StringUtils.hasText(id.value())) {
        continue;
      }
      String type = id.type().toLowerCase(Locale.ROOT);
      switch (type) {
        case "doi" ->
            identifiers.add(
                CanonicalPublication.Identifier.builder()
                    .type(PublicationIdentifierType.DOI)
                    .value(id.value())
                    .build());
        case "pmc", "pmcid" ->
            identifiers.add(
                CanonicalPublication.Identifier.builder()
                    .type(PublicationIdentifierType.PMC)
                    .value(id.value())
                    .build());
        default -> {
          // Ignore other identifier types for now.
        }
      }
    }

    // 从 Article.ELocationID 提取 DOI 和 PII
    Article citation = article.article();
    if (citation != null) {
      for (Article.ELocationId eLocationId : citation.eLocationIds()) {
        String type = eLocationId.eidType();
        String value = eLocationId.value();
        if (StringUtils.hasText(type) && StringUtils.hasText(value)) {
          // 只添加有效的标识符
          if ("Y".equals(eLocationId.validYN()) || eLocationId.validYN() == null) {
            identifiers.add(
                CanonicalPublication.Identifier.builder()
                    .type(PublicationIdentifierType.fromCodeOrOther(type))
                    .value(value)
                    .build());
          }
        }
      }
    }

    // 从 MedlineCitation.OtherID 提取其他标识符
    for (PubmedPublication.OtherId otherId : article.otherIds()) {
      String source = otherId.source();
      String value = otherId.value();
      if (StringUtils.hasText(source) && StringUtils.hasText(value)) {
        identifiers.add(
            CanonicalPublication.Identifier.builder()
                .type(PublicationIdentifierType.fromCodeOrOther(source))
                .value(value)
                .build());
      }
    }

    return identifiers.isEmpty() ? null : identifiers;
  }

  /// 提取出版日期信息
  ///
  /// @param article PubMed文章
  /// @return 出版日期信息，如果没有任何日期则返回null
  private CanonicalPublication.PublicationDates extractPublicationDates(PubmedPublication article) {
    LocalDate publishedDate = extractPublicationDate(article);

    // 从 History 提取 received、accepted 日期
    LocalDate receivedDate = extractHistoryDate(article, "received");
    LocalDate acceptedDate = extractHistoryDate(article, "accepted");

    // ✅ 修正：从 MedlineCitation.DateRevised 提取修订日期（而非 History）
    LocalDate revisedDate = extractDateInfo(article.dateRevised());

    // 从 MedlineCitation 提取 dateCreated、dateCompleted
    LocalDate createdDate = extractDateInfo(article.dateCreated());
    LocalDate completedDate = extractDateInfo(article.dateCompleted());

    // 提取电子版发布日期
    LocalDate electronicDate = extractElectronicDate(article);

    if (publishedDate == null
        && receivedDate == null
        && acceptedDate == null
        && revisedDate == null
        && createdDate == null
        && completedDate == null
        && electronicDate == null) {
      return null;
    }

    return CanonicalPublication.PublicationDates.builder()
        .published(publishedDate)
        .electronic(electronicDate)
        .received(receivedDate)
        .accepted(acceptedDate)
        .revised(revisedDate)
        .created(createdDate)
        .completed(completedDate)
        .indexed(completedDate)
        .build();
  }

  private LocalDate extractPublicationDate(PubmedPublication article) {
    Article citation = article.article();
    if (citation == null) {
      return null;
    }
    Journal journal = citation.journal();
    if (journal == null) {
      return null;
    }
    JournalIssue issue = journal.journalIssue();
    if (issue == null) {
      return null;
    }
    PubDate pubDate = issue.pubDate();
    if (pubDate == null || !StringUtils.hasText(pubDate.year())) {
      return null;
    }
    String year = pubDate.year();
    String month = pubDate.month();
    String day = pubDate.day();
    try {
      int yearValue = Integer.parseInt(year.trim());
      int monthValue = resolveMonth(month);
      int dayValue = resolveDay(day);
      return LocalDate.of(yearValue, monthValue, dayValue);
    } catch (Exception ex) {
      log.debug(
          "Failed to parse publication date for pmid={} due to {}",
          article.pmid(),
          ex.getMessage());
      return null;
    }
  }

  private int resolveMonth(String value) {
    if (!StringUtils.hasText(value)) {
      return 1;
    }
    String trimmed = value.trim();
    if (trimmed.matches("\\d{1,2}")) {
      int month = Integer.parseInt(trimmed);
      return Math.min(Math.max(month, 1), 12);
    }
    try {
      return Month.valueOf(trimmed.toUpperCase(Locale.ROOT)).getValue();
    } catch (IllegalArgumentException ignored) {
      return 1;
    }
  }

  private int resolveDay(String value) {
    if (!StringUtils.hasText(value)) {
      return 1;
    }
    String trimmed = value.trim();
    if (trimmed.matches("\\d{1,2}")) {
      int day = Integer.parseInt(trimmed);
      return Math.min(Math.max(day, 1), 28);
    }
    return 1;
  }

  /// 提取关键字集合
  ///
  /// @param article PubMed文章
  /// @return 关键字集合列表，如果没有则返回null
  private List<CanonicalPublication.KeywordSet> extractKeywords(PubmedPublication article) {
    List<PubmedPublication.KeywordList> keywordLists = article.keywordLists();
    if (CollectionUtils.isEmpty(keywordLists)) {
      return null;
    }

    List<CanonicalPublication.KeywordSet> keywordSets = new ArrayList<>();
    for (PubmedPublication.KeywordList keywordList : keywordLists) {
      List<PubmedPublication.Keyword> keywords = keywordList.keywords();
      if (CollectionUtils.isEmpty(keywords)) {
        continue;
      }

      List<CanonicalPublication.Keyword> canonicalKeywords =
          keywords.stream()
              .filter(keyword -> StringUtils.hasText(keyword.value()))
              .map(
                  keyword -> {
                    Boolean majorTopic = null;
                    if (StringUtils.hasText(keyword.majorTopicYN())) {
                      majorTopic = "Y".equalsIgnoreCase(keyword.majorTopicYN());
                    }
                    return CanonicalPublication.Keyword.builder()
                        .term(keyword.value())
                        .majorTopic(majorTopic)
                        .build();
                  })
              .collect(Collectors.toList());

      if (!canonicalKeywords.isEmpty()) {
        // 映射来源：NOTNLM -> author, NLM -> indexer
        String source = "publisher"; // 默认值
        if (StringUtils.hasText(keywordList.owner())) {
          source =
              switch (keywordList.owner().toUpperCase(Locale.ROOT)) {
                case "NOTNLM" -> "author";
                case "NLM" -> "indexer";
                default -> "publisher";
              };
        }

        keywordSets.add(
            CanonicalPublication.KeywordSet.builder()
                .source(source)
                .keywords(canonicalKeywords)
                .build());
      }
    }

    return keywordSets.isEmpty() ? null : keywordSets;
  }

  /// 从History中提取特定类型的日期
  ///
  /// @param article PubMed文章
  /// @param pubStatus 发布状态（如received、accepted、revised）
  /// @return 日期，如果不存在则返回null
  private LocalDate extractHistoryDate(PubmedPublication article, String pubStatus) {
    for (PubmedData.HistoryEvent event : article.pubmedData().history()) {
      if (pubStatus.equalsIgnoreCase(event.status())) {
        return parseDate(event.year(), event.month(), event.day());
      }
    }
    return null;
  }

  /// 从DateInfo提取日期
  ///
  /// @param dateInfo 日期信息
  /// @return 日期，如果为null则返回null
  private LocalDate extractDateInfo(PubmedPublication.DateInfo dateInfo) {
    if (dateInfo == null) {
      return null;
    }
    return parseDate(dateInfo.year(), dateInfo.month(), dateInfo.day());
  }

  /// 提取电子版发布日期
  ///
  /// @param article PubMed文章
  /// @return 电子版发布日期，如果不存在则返回null
  private LocalDate extractElectronicDate(PubmedPublication article) {
    Article citation = article.article();
    if (citation == null) {
      return null;
    }

    for (Article.ArticleDate articleDate : citation.articleDates()) {
      if ("Electronic".equalsIgnoreCase(articleDate.dateType())) {
        return parseDate(articleDate.year(), articleDate.month(), articleDate.day());
      }
    }

    return null;
  }

  /// 解析日期
  ///
  /// @param year 年份
  /// @param month 月份
  /// @param day 日期
  /// @return LocalDate对象，解析失败返回null
  private LocalDate parseDate(String year, String month, String day) {
    if (!StringUtils.hasText(year)) {
      return null;
    }
    try {
      int yearValue = Integer.parseInt(year.trim());
      int monthValue = resolveMonth(month);
      int dayValue = resolveDay(day);
      return LocalDate.of(yearValue, monthValue, dayValue);
    } catch (Exception ex) {
      log.debug(
          "Failed to parse date year={} month={} day={} due to {}",
          year,
          month,
          day,
          ex.getMessage());
      return null;
    }
  }

  /// 转换化学物质列表
  ///
  /// @param article PubMed文章
  /// @return 化学物质列表，如果没有则返回null
  private List<CanonicalPublication.Substance> convertSubstances(PubmedPublication article) {
    List<PubmedPublication.Chemical> chemicals = article.chemicals();
    if (CollectionUtils.isEmpty(chemicals)) {
      return null;
    }

    List<CanonicalPublication.Substance> substances = new ArrayList<>(chemicals.size());
    for (PubmedPublication.Chemical chemical : chemicals) {
      PubmedPublication.NameOfSubstance nameOfSubstance = chemical.nameOfSubstance();
      if (nameOfSubstance == null || !StringUtils.hasText(nameOfSubstance.value())) {
        continue;
      }

      substances.add(
          CanonicalPublication.Substance.builder()
              .name(nameOfSubstance.value())
              .registryNumber(chemical.registryNumber())
              .vocabularyId(nameOfSubstance.ui())
              .vocabularySource("MeSH")
              .build());
    }

    return substances.isEmpty() ? null : substances;
  }

  /// 转换MeSH主题标引列表
  ///
  /// @param article PubMed文章
  /// @return MeSH主题标引列表，如果没有则返回null
  private List<CanonicalPublication.MeshHeading> convertMeshHeadings(PubmedPublication article) {
    List<PubmedPublication.MeshHeading> meshHeadings = article.meshHeadings();
    if (CollectionUtils.isEmpty(meshHeadings)) {
      return null;
    }

    List<CanonicalPublication.MeshHeading> canonicalMeshHeadings =
        new ArrayList<>(meshHeadings.size());
    for (PubmedPublication.MeshHeading meshHeading : meshHeadings) {
      PubmedPublication.DescriptorName descriptorName = meshHeading.descriptorName();
      if (descriptorName == null || !StringUtils.hasText(descriptorName.value())) {
        continue;
      }

      // 转换MeSH主题词
      Boolean majorTopic = null;
      if (StringUtils.hasText(descriptorName.majorTopicYN())) {
        majorTopic = "Y".equalsIgnoreCase(descriptorName.majorTopicYN());
      }

      CanonicalPublication.DescriptorName canonicalDescriptor =
          CanonicalPublication.DescriptorName.builder()
              .ui(descriptorName.ui())
              .term(descriptorName.value())
              .majorTopic(majorTopic)
              .type(descriptorName.type())
              .build();

      // 转换MeSH限定词
      List<CanonicalPublication.QualifierName> qualifiers = null;
      if (!CollectionUtils.isEmpty(meshHeading.qualifierNames())) {
        qualifiers =
            meshHeading.qualifierNames().stream()
                .filter(q -> StringUtils.hasText(q.value()))
                .map(
                    q -> {
                      Boolean qMajorTopic = null;
                      if (StringUtils.hasText(q.majorTopicYN())) {
                        qMajorTopic = "Y".equalsIgnoreCase(q.majorTopicYN());
                      }
                      return CanonicalPublication.QualifierName.builder()
                          .ui(q.ui())
                          .term(q.value())
                          .majorTopic(qMajorTopic)
                          .build();
                    })
                .collect(Collectors.toList());

        if (qualifiers.isEmpty()) {
          qualifiers = null;
        }
      }

      canonicalMeshHeadings.add(
          CanonicalPublication.MeshHeading.builder()
              .descriptorName(canonicalDescriptor)
              .qualifierNames(qualifiers)
              .build());
    }

    return canonicalMeshHeadings.isEmpty() ? null : canonicalMeshHeadings;
  }

  /// 转换资助信息列表
  ///
  /// @param article PubMed文章
  /// @return 资助信息列表，如果没有则返回null
  private List<CanonicalPublication.FundingInfo> convertFunding(PubmedPublication article) {
    Article citationArticle = article.article();
    if (citationArticle == null) {
      return null;
    }

    List<Article.Grant> grants = citationArticle.grants();
    if (CollectionUtils.isEmpty(grants)) {
      return null;
    }

    List<CanonicalPublication.FundingInfo> fundingList = new ArrayList<>(grants.size());
    for (Article.Grant grant : grants) {
      if (!StringUtils.hasText(grant.agency())) {
        continue;
      }

      fundingList.add(
          CanonicalPublication.FundingInfo.builder()
              .grantId(grant.grantId())
              .funderName(grant.agency())
              .funderAcronym(grant.acronym())
              .country(grant.country())
              .build());
    }

    return fundingList.isEmpty() ? null : fundingList;
  }

  /// 转换其他语言摘要列表
  ///
  /// @param article PubMed文章
  /// @return 其他语言摘要列表，如果没有则返回null
  private List<CanonicalPublication.AlternativeAbstract> convertAlternativeAbstracts(
      PubmedPublication article) {
    List<PubmedPublication.OtherAbstract> otherAbstracts = article.otherAbstracts();
    if (CollectionUtils.isEmpty(otherAbstracts)) {
      return null;
    }

    List<CanonicalPublication.AlternativeAbstract> abstracts =
        new ArrayList<>(otherAbstracts.size());
    for (PubmedPublication.OtherAbstract otherAbstract : otherAbstracts) {
      if (!StringUtils.hasText(otherAbstract.abstractText())) {
        continue;
      }

      abstracts.add(
          CanonicalPublication.AlternativeAbstract.builder()
              .type(otherAbstract.type())
              .language(otherAbstract.language())
              .text(otherAbstract.abstractText())
              .copyright(otherAbstract.copyrightInformation())
              .build());
    }

    return abstracts.isEmpty() ? null : abstracts;
  }

  /// 转换基因符号列表
  ///
  /// @param article PubMed文章
  /// @return 基因符号列表，如果没有则返回null
  private List<String> convertGenes(PubmedPublication article) {
    List<String> geneSymbols = article.geneSymbols();
    return CollectionUtils.isEmpty(geneSymbols) ? null : geneSymbols;
  }

  /// 转换页码信息
  ///
  /// @param article PubMed文章
  /// @return 页码信息对象，如果没有则返回null
  private CanonicalPublication.Pagination convertPagination(PubmedPublication article) {
    Article citationArticle = article.article();
    if (citationArticle == null) {
      return null;
    }

    Article.Pagination pagination = citationArticle.pagination();
    if (pagination == null) {
      return null;
    }

    // 提取页码字段
    String startPage = pagination.startPage();
    String endPage = pagination.endPage();
    String medlinePgn = pagination.medlinePgn();

    // 如果所有字段都为空，返回null
    if (!StringUtils.hasText(startPage)
        && !StringUtils.hasText(endPage)
        && !StringUtils.hasText(medlinePgn)) {
      return null;
    }

    return CanonicalPublication.Pagination.builder()
        .startPage(startPage)
        .endPage(endPage)
        .medlinePgn(medlinePgn)
        .build();
  }

  /// 提取出版类型列表
  ///
  /// @param article PubMed文章
  /// @return 出版类型列表，如果没有则返回null
  private List<CanonicalPublication.PublicationType> extractPublicationTypes(
      PubmedPublication article) {
    Article citationArticle = article.article();
    if (citationArticle == null) {
      return null;
    }

    List<PubmedPublication.Article.PublicationType> publicationTypes =
        citationArticle.publicationTypes();
    if (CollectionUtils.isEmpty(publicationTypes)) {
      return null;
    }

    List<CanonicalPublication.PublicationType> types =
        publicationTypes.stream()
            .filter(type -> StringUtils.hasText(type.value()))
            .map(
                type ->
                    CanonicalPublication.PublicationType.builder()
                        .value(type.value())
                        .id(type.ui())
                        .vocabularySource("MeSH")
                        .build())
            .collect(Collectors.toList());

    return types.isEmpty() ? null : types;
  }

  /// 提取作者列表完整性标志
  ///
  /// @param article 文章信息
  /// @return 作者列表是否完整，如果未指定则返回null
  private Boolean extractAuthorsComplete(Article article) {
    if (article == null) {
      return null;
    }
    String completeYN = article.authorsCompleteYN();
    if (!StringUtils.hasText(completeYN)) {
      return null;
    }
    return "Y".equalsIgnoreCase(completeYN);
  }

  /// 提取发布历史时间线
  ///
  /// @param article PubMed文章
  /// @return 发布历史事件列表，如果没有则返回null
  private List<CanonicalPublication.PublicationHistoryEvent> extractPublicationHistory(
      PubmedPublication article) {
    List<PubmedData.HistoryEvent> historyEvents = article.pubmedData().history();
    if (CollectionUtils.isEmpty(historyEvents)) {
      return null;
    }

    List<CanonicalPublication.PublicationHistoryEvent> events =
        historyEvents.stream()
            .filter(event -> StringUtils.hasText(event.status()))
            .map(
                event ->
                    CanonicalPublication.PublicationHistoryEvent.builder()
                        .status(event.status())
                        .year(event.year())
                        .month(event.month())
                        .day(event.day())
                        .hour(event.hour())
                        .minute(event.minute())
                        .build())
            .collect(Collectors.toList());

    return events.isEmpty() ? null : events;
  }

  /// 提取出版物元数据
  ///
  /// @param article PubMed文章
  /// @return 出版物元数据，如果所有字段都为空则返回null
  private CanonicalPublication.PublicationMetadata extractMetadata(PubmedPublication article) {
    String indexingMethod = article.indexingMethod();
    String owner = article.owner();
    String status = article.status();
    String citationSubset = article.citationSubset();

    // 如果所有字段都为空，返回null
    if (!StringUtils.hasText(indexingMethod)
        && !StringUtils.hasText(owner)
        && !StringUtils.hasText(status)
        && !StringUtils.hasText(citationSubset)) {
      return null;
    }

    return CanonicalPublication.PublicationMetadata.builder()
        .indexingMethod(indexingMethod)
        .owner(owner)
        .status(status)
        .citationSubset(citationSubset)
        .build();
  }

  /// 转换补充MeSH概念列表
  ///
  /// @param article PubMed文章
  /// @return 补充MeSH概念列表，如果没有则返回null
  private List<CanonicalPublication.SupplMeshName> convertSupplMeshNames(
      PubmedPublication article) {
    List<PubmedPublication.SupplMeshName> supplMeshNames = article.supplMeshNames();
    if (CollectionUtils.isEmpty(supplMeshNames)) {
      return null;
    }

    List<CanonicalPublication.SupplMeshName> canonicalSupplMeshNames =
        supplMeshNames.stream()
            .filter(s -> StringUtils.hasText(s.value()))
            .map(
                s ->
                    CanonicalPublication.SupplMeshName.builder()
                        .ui(s.ui())
                        .name(s.value())
                        .type(s.type())
                        .build())
            .collect(Collectors.toList());

    return canonicalSupplMeshNames.isEmpty() ? null : canonicalSupplMeshNames;
  }

  /// 转换研究者列表
  ///
  /// @param article PubMed文章
  /// @return 研究者列表，如果没有则返回null
  private List<CanonicalPublication.Investigator> convertInvestigators(PubmedPublication article) {
    List<PubmedPublication.Investigator> investigators = article.investigators();
    if (CollectionUtils.isEmpty(investigators)) {
      return null;
    }

    List<CanonicalPublication.Investigator> canonicalInvestigators =
        new ArrayList<>(investigators.size());
    for (PubmedPublication.Investigator investigator : investigators) {
      // 转换机构信息（保留 ROR/Ringgold 等标识符）
      List<CanonicalPublication.Affiliation> affiliations = null;
      if (!CollectionUtils.isEmpty(investigator.affiliationInfo())) {
        affiliations =
            investigator.affiliationInfo().stream()
                .filter(info -> StringUtils.hasText(info.value()))
                .map(this::convertAffiliationInfo)
                .collect(Collectors.toList());
        if (affiliations.isEmpty()) {
          affiliations = null;
        }
      }

      // 转换标识符
      List<CanonicalPublication.Identifier> identifiers = null;
      if (!CollectionUtils.isEmpty(investigator.identifiers())) {
        identifiers =
            investigator.identifiers().stream()
                .filter(id -> StringUtils.hasText(id.source()) && StringUtils.hasText(id.value()))
                .map(
                    id ->
                        CanonicalPublication.Identifier.builder()
                            .type(PublicationIdentifierType.fromCodeOrOther(id.source()))
                            .value(id.value())
                            .build())
                .collect(Collectors.toList());
        if (identifiers.isEmpty()) {
          identifiers = null;
        }
      }

      // 处理有效性标志
      Boolean valid = null;
      if (StringUtils.hasText(investigator.validYN())) {
        valid = "Y".equalsIgnoreCase(investigator.validYN());
      }

      canonicalInvestigators.add(
          CanonicalPublication.Investigator.builder()
              .lastName(investigator.lastName())
              .foreName(investigator.foreName())
              .initials(investigator.initials())
              .suffix(investigator.suffix())
              .valid(valid)
              .affiliations(affiliations)
              .identifiers(identifiers)
              .build());
    }

    return canonicalInvestigators.isEmpty() ? null : canonicalInvestigators;
  }

  /// 转换作为主题的人物列表
  ///
  /// @param article PubMed文章
  /// @return 人物主题列表，如果没有则返回null
  private List<CanonicalPublication.PersonalNameSubject> convertPersonalNameSubjects(
      PubmedPublication article) {
    List<PubmedPublication.PersonalNameSubject> personalNameSubjects =
        article.personalNameSubjects();
    if (CollectionUtils.isEmpty(personalNameSubjects)) {
      return null;
    }

    List<CanonicalPublication.PersonalNameSubject> canonicalPersonalNameSubjects =
        personalNameSubjects.stream()
            .filter(p -> StringUtils.hasText(p.lastName()) || StringUtils.hasText(p.foreName()))
            .map(
                p ->
                    CanonicalPublication.PersonalNameSubject.builder()
                        .lastName(p.lastName())
                        .foreName(p.foreName())
                        .initials(p.initials())
                        .suffix(p.suffix())
                        .build())
            .collect(Collectors.toList());

    return canonicalPersonalNameSubjects.isEmpty() ? null : canonicalPersonalNameSubjects;
  }

  /// 转换外部引用列表
  ///
  /// @param article PubMed文章
  /// @return 外部引用列表，如果没有则返回null
  private List<CanonicalPublication.ExternalReference> convertExternalReferences(
      PubmedPublication article) {
    Article citationArticle = article.article();
    if (citationArticle == null) {
      return null;
    }

    List<Article.DataBank> dataBanks = citationArticle.dataBanks();
    if (CollectionUtils.isEmpty(dataBanks)) {
      return null;
    }

    List<CanonicalPublication.ExternalReference> externalReferences =
        new ArrayList<>(dataBanks.size());
    for (Article.DataBank dataBank : dataBanks) {
      if (!StringUtils.hasText(dataBank.dataBankName())) {
        continue;
      }

      externalReferences.add(
          CanonicalPublication.ExternalReference.builder()
              .type("database")
              .name(dataBank.dataBankName())
              .identifiers(dataBank.accessionNumbers())
              .build());
    }

    return externalReferences.isEmpty() ? null : externalReferences;
  }

  /// 转换补充对象列表
  ///
  /// @param article PubMed文章
  /// @return 补充对象列表，如果没有则返回null
  private List<CanonicalPublication.SupplementalObject> convertSupplementalObjects(
      PubmedPublication article) {
    List<PubmedData.ObjectInfo> objects = article.pubmedData().objects();
    if (CollectionUtils.isEmpty(objects)) {
      return null;
    }

    List<CanonicalPublication.SupplementalObject> supplementalObjects =
        new ArrayList<>(objects.size());
    for (PubmedData.ObjectInfo object : objects) {
      if (!StringUtils.hasText(object.type())) {
        continue;
      }

      // 转换参数列表
      List<CanonicalPublication.ObjectParam> params = null;
      if (!CollectionUtils.isEmpty(object.params())) {
        params =
            object.params().stream()
                .filter(p -> StringUtils.hasText(p.name()))
                .map(
                    p ->
                        CanonicalPublication.ObjectParam.builder()
                            .name(p.name())
                            .value(p.value())
                            .build())
                .collect(Collectors.toList());
        if (params.isEmpty()) {
          params = null;
        }
      }

      supplementalObjects.add(
          CanonicalPublication.SupplementalObject.builder()
              .type(object.type())
              .params(params)
              .build());
    }

    return supplementalObjects.isEmpty() ? null : supplementalObjects;
  }

  /// 转换参考文献列表
  ///
  /// @param article PubMed文章
  /// @return 参考文献列表，如果没有则返回null
  private List<CanonicalPublication.Reference> convertReferences(PubmedPublication article) {
    List<PubmedData.Reference> references = article.pubmedData().references();
    if (CollectionUtils.isEmpty(references)) {
      return null;
    }

    List<CanonicalPublication.Reference> canonicalReferences = new ArrayList<>(references.size());
    for (PubmedData.Reference reference : references) {
      // 转换标识符
      List<CanonicalPublication.Identifier> identifiers = null;
      if (!CollectionUtils.isEmpty(reference.articleIds())) {
        identifiers =
            reference.articleIds().stream()
                .filter(id -> StringUtils.hasText(id.type()) && StringUtils.hasText(id.value()))
                .map(
                    id ->
                        CanonicalPublication.Identifier.builder()
                            .type(PublicationIdentifierType.fromCodeOrOther(id.type()))
                            .value(id.value())
                            .build())
                .collect(Collectors.toList());
        if (identifiers.isEmpty()) {
          identifiers = null;
        }
      }

      canonicalReferences.add(
          CanonicalPublication.Reference.builder()
              .citation(reference.citation())
              .identifiers(identifiers)
              .build());
    }

    return canonicalReferences.isEmpty() ? null : canonicalReferences;
  }

  /// 转换相关项目列表（评论、更正、撤稿等）
  ///
  /// @param article PubMed文章
  /// @return 相关项目列表，如果没有则返回null
  private List<CanonicalPublication.RelatedItem> convertRelatedItems(PubmedPublication article) {
    List<PubmedPublication.CommentsCorrections> commentsCorrections = article.commentsCorrections();
    if (CollectionUtils.isEmpty(commentsCorrections)) {
      return null;
    }

    List<CanonicalPublication.RelatedItem> relatedItems =
        commentsCorrections.stream()
            .filter(cc -> StringUtils.hasText(cc.refType()))
            .map(
                cc ->
                    CanonicalPublication.RelatedItem.builder()
                        .relationType(cc.refType())
                        .citation(cc.refSource())
                        .identifier(cc.pmid())
                        .identifierType(StringUtils.hasText(cc.pmid()) ? "pmid" : null)
                        .description(cc.note())
                        .build())
            .collect(Collectors.toList());

    return relatedItems.isEmpty() ? null : relatedItems;
  }
}
