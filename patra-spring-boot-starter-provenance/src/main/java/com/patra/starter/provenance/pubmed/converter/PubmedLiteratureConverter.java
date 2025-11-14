package com.patra.starter.provenance.pubmed.converter;

import com.patra.common.model.CanonicalLiterature;
import com.patra.starter.provenance.pubmed.model.response.PubmedLiterature;
import com.patra.starter.provenance.pubmed.model.response.PubmedLiterature.Article;
import com.patra.starter.provenance.pubmed.model.response.PubmedLiterature.Author;
import com.patra.starter.provenance.pubmed.model.response.PubmedLiterature.Journal;
import com.patra.starter.provenance.pubmed.model.response.PubmedLiterature.Journal.JournalIssue;
import com.patra.starter.provenance.pubmed.model.response.PubmedLiterature.Journal.PubDate;
import com.patra.starter.provenance.pubmed.model.response.PubmedLiterature.MedlineJournalInfo;
import com.patra.starter.provenance.pubmed.model.response.PubmedLiterature.PubmedData;
import com.patra.starter.provenance.pubmed.model.response.PubmedLiterature.PubmedData.ArticleId;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * PubMed文章转换器
 *
 * <p>将 {@link PubmedLiterature} 响应映射为 {@link CanonicalLiterature} 标准文献模型。
 * 集中所有字段提取逻辑，使下游组件能够操作稳定的共享内核模型。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class PubmedLiteratureConverter {

  /**
   * 将PubMed文章转换为标准化文献模型
   *
   * @param article PubMed文章响应
   * @return 标准化的文献表示
   */
  public CanonicalLiterature toCanonicalLiterature(PubmedLiterature article) {
    if (article == null) {
      return null;
    }
    if (log.isDebugEnabled()) {
      log.debug("Converting PubMed article to CanonicalLiterature pmid={}", article.pmid());
    }
    Article citation = article.article();
    return CanonicalLiterature.builder()
        .title(citation != null ? citation.title() : null)
        .originalTitle(citation != null ? citation.vernacularTitle() : null)
        .language(citation != null ? citation.language() : null)
        .publicationTypes(extractPublicationTypes(article))
        .publicationStatus(article.pubmedData().publicationStatus())
        .mediaType(citation != null ? citation.pubModel() : null)
        .abstractContent(extractAbstract(citation))
        .alternativeAbstracts(convertAlternativeAbstracts(article))
        .authors(convertAuthors(article))
        .journal(convertJournal(article))
        .identifiers(buildIdentifiers(article))
        .subjects(convertSubjects(article))
        .keywords(extractKeywords(article))
        .substances(convertSubstances(article))
        .genes(convertGenes(article))
        .pagination(convertPagination(article))
        .dates(extractPublicationDates(article))
        .funding(convertFunding(article))
        .citationCount(article.pubmedData().pmcRefCount())
        .conflictOfInterestStatement(article.coiStatement())
        .build();
  }

  private CanonicalLiterature.Abstract extractAbstract(Article article) {
    if (article == null || CollectionUtils.isEmpty(article.abstractSections())) {
      return null;
    }

    List<CanonicalLiterature.AbstractSection> sections =
        article.abstractSections().stream()
            .filter(section -> StringUtils.hasText(section.text()))
            .map(
                section ->
                    CanonicalLiterature.AbstractSection.builder()
                        .label(section.label())
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

    return CanonicalLiterature.Abstract.builder().text(text).sections(sections).build();
  }

  /**
   * 转换作者列表
   *
   * @param article PubMed文章
   * @return 标准化的作者列表，如果没有作者则返回空列表
   */
  private List<CanonicalLiterature.Author> convertAuthors(PubmedLiterature article) {
    Article citationArticle = article.article();
    if (citationArticle == null || CollectionUtils.isEmpty(citationArticle.authors())) {
      return List.of();
    }

    List<CanonicalLiterature.Author> canonicalAuthors = new ArrayList<>();
    for (Author author : citationArticle.authors()) {
      // 转换机构信息
      List<CanonicalLiterature.Affiliation> affiliations = null;
      if (!CollectionUtils.isEmpty(author.affiliations())) {
        affiliations =
            author.affiliations().stream()
                .filter(StringUtils::hasText)
                .map(
                    affiliationName ->
                        CanonicalLiterature.Affiliation.builder().name(affiliationName).build())
                .collect(Collectors.toList());
        if (affiliations.isEmpty()) {
          affiliations = null;
        }
      }

      // 转换作者标识符（ORCID、ResearcherID等）
      List<CanonicalLiterature.Identifier> authorIdentifiers = null;
      if (!CollectionUtils.isEmpty(author.identifiers())) {
        authorIdentifiers =
            author.identifiers().stream()
                .filter(id -> StringUtils.hasText(id.source()) && StringUtils.hasText(id.value()))
                .map(
                    id ->
                        CanonicalLiterature.Identifier.builder()
                            .type(id.source().toLowerCase(Locale.ROOT))
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

      canonicalAuthors.add(
          CanonicalLiterature.Author.builder()
              .lastName(author.lastName())
              .foreName(author.foreName())
              .initials(author.initials())
              .suffix(author.suffix())
              .organizationName(author.collectiveName())
              .equalContribution(equalContribution)
              .affiliations(affiliations)
              .identifiers(authorIdentifiers)
              .build());
    }

    return canonicalAuthors;
  }

  /**
   * 转换期刊信息
   *
   * @param article PubMed文章
   * @return 标准化的期刊信息，如果没有期刊信息则返回null
   */
  private CanonicalLiterature.Journal convertJournal(PubmedLiterature article) {
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

    // 提取期刊国家
    String country = medline != null ? medline.country() : null;

    // 提取卷号和期号
    String volume = null;
    String issue = null;
    if (journal != null && journal.journalIssue() != null) {
      volume = journal.journalIssue().volume();
      issue = journal.journalIssue().issue();
    }

    return CanonicalLiterature.Journal.builder()
        .title(title)
        .isoAbbreviation(isoAbbreviation)
        .issn(issn)
        .issnType(issnType)
        .volume(volume)
        .issue(issue)
        .country(country)
        .publisher(null)
        .build();
  }

  /**
   * 构建标识符列表
   *
   * @param article PubMed文章
   * @return 标识符列表，如果没有标识符则返回null
   */
  private List<CanonicalLiterature.Identifier> buildIdentifiers(PubmedLiterature article) {
    List<CanonicalLiterature.Identifier> identifiers = new ArrayList<>();

    // 添加 PMID
    if (StringUtils.hasText(article.pmid())) {
      identifiers.add(
          CanonicalLiterature.Identifier.builder().type("pmid").value(article.pmid()).build());
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
                CanonicalLiterature.Identifier.builder().type("doi").value(id.value()).build());
        case "pmc", "pmcid" ->
            identifiers.add(
                CanonicalLiterature.Identifier.builder().type("pmc").value(id.value()).build());
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
                CanonicalLiterature.Identifier.builder()
                    .type(type.toLowerCase(Locale.ROOT))
                    .value(value)
                    .build());
          }
        }
      }
    }

    // 从 MedlineCitation.OtherID 提取其他标识符
    for (PubmedLiterature.OtherId otherId : article.otherIds()) {
      String source = otherId.source();
      String value = otherId.value();
      if (StringUtils.hasText(source) && StringUtils.hasText(value)) {
        identifiers.add(
            CanonicalLiterature.Identifier.builder()
                .type(source.toLowerCase(Locale.ROOT))
                .value(value)
                .build());
      }
    }

    return identifiers.isEmpty() ? null : identifiers;
  }

  /**
   * 提取出版日期信息
   *
   * @param article PubMed文章
   * @return 出版日期信息，如果没有任何日期则返回null
   */
  private CanonicalLiterature.PublicationDates extractPublicationDates(PubmedLiterature article) {
    LocalDate publishedDate = extractPublicationDate(article);

    // 从 History 提取 received、accepted、revised 日期
    LocalDate receivedDate = extractHistoryDate(article, "received");
    LocalDate acceptedDate = extractHistoryDate(article, "accepted");
    LocalDate revisedDate = extractHistoryDate(article, "revised");

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

    return CanonicalLiterature.PublicationDates.builder()
        .published(publishedDate)
        .electronic(electronicDate)
        .received(receivedDate)
        .accepted(acceptedDate)
        .revised(revisedDate)
        .created(createdDate)
        .indexed(completedDate)
        .build();
  }

  private LocalDate extractPublicationDate(PubmedLiterature article) {
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

  private List<CanonicalLiterature.KeywordSet> extractKeywords(PubmedLiterature article) {
    List<String> keywords = article.keywords();
    if (CollectionUtils.isEmpty(keywords)) {
      return null;
    }

    List<CanonicalLiterature.Keyword> keywordList =
        keywords.stream()
            .filter(StringUtils::hasText)
            .map(keyword -> CanonicalLiterature.Keyword.builder().term(keyword).build())
            .collect(Collectors.toList());

    if (keywordList.isEmpty()) {
      return null;
    }

    return List.of(
        CanonicalLiterature.KeywordSet.builder()
            .source("publisher") // PubMed 关键词通常来自出版商
            .keywords(keywordList)
            .build());
  }

  /**
   * 从History中提取特定类型的日期
   *
   * @param article PubMed文章
   * @param pubStatus 发布状态（如received、accepted、revised）
   * @return 日期，如果不存在则返回null
   */
  private LocalDate extractHistoryDate(PubmedLiterature article, String pubStatus) {
    for (PubmedData.HistoryEvent event : article.pubmedData().history()) {
      if (pubStatus.equalsIgnoreCase(event.status())) {
        return parseDate(event.year(), event.month(), event.day());
      }
    }
    return null;
  }

  /**
   * 从DateInfo提取日期
   *
   * @param dateInfo 日期信息
   * @return 日期，如果为null则返回null
   */
  private LocalDate extractDateInfo(PubmedLiterature.DateInfo dateInfo) {
    if (dateInfo == null) {
      return null;
    }
    return parseDate(dateInfo.year(), dateInfo.month(), dateInfo.day());
  }

  /**
   * 提取电子版发布日期
   *
   * @param article PubMed文章
   * @return 电子版发布日期，如果不存在则返回null
   */
  private LocalDate extractElectronicDate(PubmedLiterature article) {
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

  /**
   * 解析日期
   *
   * @param year 年份
   * @param month 月份
   * @param day 日期
   * @return LocalDate对象，解析失败返回null
   */
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

  /**
   * 转换化学物质列表
   *
   * @param article PubMed文章
   * @return 化学物质列表，如果没有则返回null
   */
  private List<CanonicalLiterature.Substance> convertSubstances(PubmedLiterature article) {
    List<PubmedLiterature.Chemical> chemicals = article.chemicals();
    if (CollectionUtils.isEmpty(chemicals)) {
      return null;
    }

    List<CanonicalLiterature.Substance> substances = new ArrayList<>(chemicals.size());
    for (PubmedLiterature.Chemical chemical : chemicals) {
      PubmedLiterature.NameOfSubstance nameOfSubstance = chemical.nameOfSubstance();
      if (nameOfSubstance == null || !StringUtils.hasText(nameOfSubstance.value())) {
        continue;
      }

      substances.add(
          CanonicalLiterature.Substance.builder()
              .name(nameOfSubstance.value())
              .registryNumber(chemical.registryNumber())
              .vocabularyId(nameOfSubstance.ui())
              .vocabularySource("MeSH")
              .build());
    }

    return substances.isEmpty() ? null : substances;
  }

  /**
   * 转换MeSH主题词列表
   *
   * @param article PubMed文章
   * @return MeSH主题词列表，如果没有则返回null
   */
  private List<CanonicalLiterature.Subject> convertSubjects(PubmedLiterature article) {
    List<PubmedLiterature.MeshHeading> meshHeadings = article.meshHeadings();
    if (CollectionUtils.isEmpty(meshHeadings)) {
      return null;
    }

    List<CanonicalLiterature.Subject> subjects = new ArrayList<>(meshHeadings.size());
    for (PubmedLiterature.MeshHeading meshHeading : meshHeadings) {
      PubmedLiterature.DescriptorName descriptorName = meshHeading.descriptorName();
      if (descriptorName == null || !StringUtils.hasText(descriptorName.value())) {
        continue;
      }

      // 提取限定词
      List<CanonicalLiterature.SubjectQualifier> qualifiers = null;
      if (!CollectionUtils.isEmpty(meshHeading.qualifierNames())) {
        qualifiers =
            meshHeading.qualifierNames().stream()
                .filter(q -> StringUtils.hasText(q.value()))
                .map(
                    q ->
                        CanonicalLiterature.SubjectQualifier.builder()
                            .id(q.ui())
                            .term(q.value())
                            .majorTopic("Y".equalsIgnoreCase(q.majorTopicYN()))
                            .build())
                .collect(Collectors.toList());

        if (qualifiers.isEmpty()) {
          qualifiers = null;
        }
      }

      subjects.add(
          CanonicalLiterature.Subject.builder()
              .id(descriptorName.ui())
              .term(descriptorName.value())
              .majorTopic("Y".equalsIgnoreCase(descriptorName.majorTopicYN()))
              .type(descriptorName.type())
              .vocabulary("MeSH")
              .qualifiers(qualifiers)
              .build());
    }

    return subjects.isEmpty() ? null : subjects;
  }

  /**
   * 转换资助信息列表
   *
   * @param article PubMed文章
   * @return 资助信息列表，如果没有则返回null
   */
  private List<CanonicalLiterature.FundingInfo> convertFunding(PubmedLiterature article) {
    Article citationArticle = article.article();
    if (citationArticle == null) {
      return null;
    }

    List<Article.Grant> grants = citationArticle.grants();
    if (CollectionUtils.isEmpty(grants)) {
      return null;
    }

    List<CanonicalLiterature.FundingInfo> fundingList = new ArrayList<>(grants.size());
    for (Article.Grant grant : grants) {
      if (!StringUtils.hasText(grant.agency())) {
        continue;
      }

      fundingList.add(
          CanonicalLiterature.FundingInfo.builder()
              .grantId(grant.grantId())
              .funderName(grant.agency())
              .funderIdentifier(grant.acronym())
              .country(grant.country())
              .build());
    }

    return fundingList.isEmpty() ? null : fundingList;
  }

  /**
   * 转换其他语言摘要列表
   *
   * @param article PubMed文章
   * @return 其他语言摘要列表，如果没有则返回null
   */
  private List<CanonicalLiterature.AlternativeAbstract> convertAlternativeAbstracts(
      PubmedLiterature article) {
    List<PubmedLiterature.OtherAbstract> otherAbstracts = article.otherAbstracts();
    if (CollectionUtils.isEmpty(otherAbstracts)) {
      return null;
    }

    List<CanonicalLiterature.AlternativeAbstract> abstracts =
        new ArrayList<>(otherAbstracts.size());
    for (PubmedLiterature.OtherAbstract otherAbstract : otherAbstracts) {
      if (!StringUtils.hasText(otherAbstract.abstractText())) {
        continue;
      }

      abstracts.add(
          CanonicalLiterature.AlternativeAbstract.builder()
              .type(otherAbstract.type())
              .language(otherAbstract.language())
              .text(otherAbstract.abstractText())
              .copyright(otherAbstract.copyrightInformation())
              .build());
    }

    return abstracts.isEmpty() ? null : abstracts;
  }

  /**
   * 转换基因符号列表
   *
   * @param article PubMed文章
   * @return 基因符号列表，如果没有则返回null
   */
  private List<String> convertGenes(PubmedLiterature article) {
    List<String> geneSymbols = article.geneSymbols();
    return CollectionUtils.isEmpty(geneSymbols) ? null : geneSymbols;
  }

  /**
   * 转换页码信息
   *
   * @param article PubMed文章
   * @return 页码信息，如果没有则返回null
   */
  private String convertPagination(PubmedLiterature article) {
    Article citationArticle = article.article();
    if (citationArticle == null) {
      return null;
    }

    Article.Pagination pagination = citationArticle.pagination();
    if (pagination == null) {
      return null;
    }

    // 优先使用 MedlinePgn
    if (StringUtils.hasText(pagination.medlinePgn())) {
      return pagination.medlinePgn();
    }

    // 否则构造页码范围
    String startPage = pagination.startPage();
    String endPage = pagination.endPage();
    if (StringUtils.hasText(startPage) && StringUtils.hasText(endPage)) {
      return startPage + "-" + endPage;
    } else if (StringUtils.hasText(startPage)) {
      return startPage;
    }

    return null;
  }

  /**
   * 提取出版类型列表
   *
   * @param article PubMed文章
   * @return 出版类型列表，如果没有则返回null
   */
  private List<CanonicalLiterature.PublicationType> extractPublicationTypes(
      PubmedLiterature article) {
    Article citationArticle = article.article();
    if (citationArticle == null) {
      return null;
    }

    List<String> publicationTypes = citationArticle.publicationTypes();
    if (CollectionUtils.isEmpty(publicationTypes)) {
      return null;
    }

    List<CanonicalLiterature.PublicationType> types =
        publicationTypes.stream()
            .filter(StringUtils::hasText)
            .map(
                type ->
                    CanonicalLiterature.PublicationType.builder()
                        .value(type)
                        .vocabularySource("PubMed")
                        .build())
            .collect(Collectors.toList());

    return types.isEmpty() ? null : types;
  }
}
