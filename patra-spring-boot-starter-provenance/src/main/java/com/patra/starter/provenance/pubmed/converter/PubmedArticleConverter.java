package com.patra.starter.provenance.pubmed.converter;

import com.patra.common.model.CanonicalLiterature;
import com.patra.common.model.CanonicalLiterature.AuthorInfo;
import com.patra.common.model.CanonicalLiterature.JournalInfo;
import com.patra.starter.provenance.pubmed.model.response.Article;
import com.patra.starter.provenance.pubmed.model.response.Author;
import com.patra.starter.provenance.pubmed.model.response.Journal;
import com.patra.starter.provenance.pubmed.model.response.Journal.JournalIssue;
import com.patra.starter.provenance.pubmed.model.response.Journal.PubDate;
import com.patra.starter.provenance.pubmed.model.response.MedlineJournalInfo;
import com.patra.starter.provenance.pubmed.model.response.PubmedArticle;
import com.patra.starter.provenance.pubmed.model.response.PubmedData.ArticleId;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * PubMed文章转换器
 *
 * <p>将 {@link PubmedArticle} 响应映射为 {@link CanonicalLiterature} 标准文献模型。
 * 集中所有字段提取逻辑，使下游组件能够操作稳定的共享内核模型。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class PubmedArticleConverter {

  /**
   * Convert a PubMed article into the standardized model.
   *
   * @param article PubMed article response
   * @return standardized literature representation
   */
  public CanonicalLiterature toCanonicalLiterature(PubmedArticle article) {
    if (article == null) {
      return null;
    }
    if (log.isDebugEnabled()) {
      log.debug("Converting PubMed article to CanonicalLiterature pmid={}", article.pmid());
    }
    Article citation = article.article();
    return CanonicalLiterature.builder()
        .title(citation != null ? citation.title() : null)
        .abstractText(extractAbstract(citation))
        .authors(convertAuthors(citation))
        .journal(convertJournal(article))
        .identifiers(buildIdentifiers(article))
        .publicationDate(extractPublicationDate(article))
        .keywords(extractKeywords(article))
        .build();
  }

  private String extractAbstract(Article article) {
    if (article == null || CollectionUtils.isEmpty(article.abstractSections())) {
      return null;
    }
    return article.abstractSections().stream()
        .map(
            section -> {
              String text = section.text();
              if (!StringUtils.hasText(text)) {
                return null;
              }
              if (StringUtils.hasText(section.label())) {
                return section.label() + ": " + text;
              }
              return text;
            })
        .filter(StringUtils::hasText)
        .collect(Collectors.joining("\n"));
  }

  private List<AuthorInfo> convertAuthors(Article article) {
    if (article == null || CollectionUtils.isEmpty(article.authors())) {
      return List.of();
    }
    return article.authors().stream().map(this::mapAuthor).collect(Collectors.toUnmodifiableList());
  }

  private AuthorInfo mapAuthor(Author author) {
    String affiliation =
        !CollectionUtils.isEmpty(author.affiliations()) ? author.affiliations().get(0) : null;
    return AuthorInfo.builder()
        .lastName(author.lastName())
        .foreName(author.foreName())
        .affiliation(affiliation)
        .build();
  }

  private JournalInfo convertJournal(PubmedArticle article) {
    Article citation = article.article();
    Journal journal = citation != null ? citation.journal() : null;
    MedlineJournalInfo medline = article.journalInfo();

    if (journal == null && medline == null) {
      return null;
    }

    String issn =
        StringUtils.hasText(journal != null ? journal.issn() : null)
            ? journal.issn()
            : (medline != null ? medline.issnLinking() : null);
    String title =
        StringUtils.hasText(journal != null ? journal.title() : null)
            ? journal.title()
            : (medline != null ? medline.medlineTa() : null);

    return JournalInfo.builder().title(title).issn(issn).publisher(null).build();
  }

  private Map<String, String> buildIdentifiers(PubmedArticle article) {
    Map<String, String> identifiers = new LinkedHashMap<>();
    if (StringUtils.hasText(article.pmid())) {
      identifiers.put("pmid", article.pmid());
    }
    for (ArticleId id : article.articleIds()) {
      if (!StringUtils.hasText(id.type()) || !StringUtils.hasText(id.value())) {
        continue;
      }
      String type = id.type().toLowerCase(Locale.ROOT);
      switch (type) {
        case "doi" -> identifiers.putIfAbsent("doi", id.value());
        case "pmc", "pmcid" -> identifiers.putIfAbsent("pmc", id.value());
        default -> {
          // Ignore other identifier types for now.
        }
      }
    }
    return identifiers;
  }

  private LocalDate extractPublicationDate(PubmedArticle article) {
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

  private List<String> extractKeywords(PubmedArticle article) {
    List<String> keywords = article.keywords();
    if (CollectionUtils.isEmpty(keywords)) {
      return List.of();
    }
    List<String> normalized = new ArrayList<>();
    for (String keyword : keywords) {
      if (StringUtils.hasText(keyword)) {
        normalized.add(keyword);
      }
    }
    return List.copyOf(normalized);
  }
}
