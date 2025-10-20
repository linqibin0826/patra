package com.patra.ingest.app.usecase.execution.execute;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.model.vo.StandardLiterature;
import com.patra.ingest.domain.model.vo.StandardLiterature.StandardAuthor;
import com.patra.ingest.domain.model.vo.StandardLiterature.StandardJournal;
import com.patra.starter.provenance.common.support.JsonHelpers;
import com.patra.starter.provenance.pubmed.model.response.Article;
import com.patra.starter.provenance.pubmed.model.response.Author;
import com.patra.starter.provenance.pubmed.model.response.Journal;
import com.patra.starter.provenance.pubmed.model.response.MedlineJournalInfo;
import com.patra.starter.provenance.pubmed.model.response.PubmedArticle;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Converter that maps {@link PubmedArticle} responses to domain-level {@link StandardLiterature}
 * value objects.
 *
 * <p>Centralizes all field extraction logic so downstream components operate on a stable,
 * provenance-agnostic model.
 */
@Component
public class PubmedArticleConverter {

  /**
   * Convert a PubMed article into the standardized ingest model.
   *
   * @param article PubMed article response
   * @return standardized literature representation
   */
  public StandardLiterature toStandardLiterature(PubmedArticle article) {
    Article citation = article.article();
    return StandardLiterature.builder()
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

  private List<StandardAuthor> convertAuthors(Article article) {
    if (article == null || CollectionUtils.isEmpty(article.authors())) {
      return List.of();
    }
    return article.authors().stream().map(this::mapAuthor).collect(Collectors.toUnmodifiableList());
  }

  private StandardAuthor mapAuthor(Author author) {
    String affiliation =
        !CollectionUtils.isEmpty(author.affiliations()) ? author.affiliations().get(0) : null;
    return StandardAuthor.builder()
        .lastName(author.lastName())
        .foreName(author.foreName())
        .affiliation(affiliation)
        .build();
  }

  private StandardJournal convertJournal(PubmedArticle article) {
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

    return StandardJournal.builder().title(title).issn(issn).publisher(null).build();
  }

  private Map<String, String> buildIdentifiers(PubmedArticle article) {
    Map<String, String> identifiers = new LinkedHashMap<>();
    if (StringUtils.hasText(article.pmid())) {
      identifiers.put("pmid", article.pmid());
    }
    extractArticleId(article.rawCitation(), "doi").ifPresent(doi -> identifiers.put("doi", doi));
    extractArticleId(article.rawCitation(), "pmc").ifPresent(pmc -> identifiers.put("pmc", pmc));
    return identifiers;
  }

  private Optional<String> extractArticleId(JsonNode citationNode, String idType) {
    if (citationNode == null || idType == null) {
      return Optional.empty();
    }
    JsonNode articleIdNode = citationNode.path("ArticleIdList").path("ArticleId");
    for (JsonNode node : JsonHelpers.toNodeList(articleIdNode)) {
      String type = JsonHelpers.textValue(node.path("@IdType"));
      if (idType.equalsIgnoreCase(type)) {
        String value = JsonHelpers.textValue(node);
        if (StringUtils.hasText(value)) {
          return Optional.of(value);
        }
      }
    }
    return Optional.empty();
  }

  private LocalDate extractPublicationDate(PubmedArticle article) {
    JsonNode pubDateNode =
        article.rawCitation().path("Article").path("Journal").path("JournalIssue").path("PubDate");
    String year = JsonHelpers.textValue(pubDateNode.path("Year"));
    if (!StringUtils.hasText(year)) {
      return null;
    }
    String month = JsonHelpers.textValue(pubDateNode.path("Month"));
    String day = JsonHelpers.textValue(pubDateNode.path("Day"));
    try {
      int yearValue = Integer.parseInt(year);
      int monthValue = resolveMonth(month);
      int dayValue = resolveDay(day);
      return LocalDate.of(yearValue, monthValue, dayValue);
    } catch (Exception ex) {
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
    JsonNode keywordNode = article.rawCitation().path("KeywordList").path("Keyword");
    List<String> keywords = JsonHelpers.toStringList(keywordNode);
    if (keywords == null) {
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
