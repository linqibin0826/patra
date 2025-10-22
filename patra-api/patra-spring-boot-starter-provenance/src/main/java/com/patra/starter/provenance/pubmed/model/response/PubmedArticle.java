package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.ArrayList;
import java.util.List;

/**
 * Simplified view over a PubMed article parsed directly from XML.
 *
 * @author
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PubmedArticle {

  private static final PubmedData EMPTY_PUBMED_DATA = new PubmedData();

  @JacksonXmlProperty(localName = "MedlineCitation")
  private MedlineCitation medlineCitation;

  @JacksonXmlProperty(localName = "PubmedData")
  private PubmedData pubmedData;

  public PubmedArticle() {}

  /** PubMed identifier (PMID). */
  public String pmid() {
    return medlineCitation != null ? medlineCitation.pmid() : null;
  }

  /** Core article metadata. */
  public Article article() {
    return medlineCitation != null ? medlineCitation.article() : null;
  }

  /** Journal information reported by Medline. */
  public MedlineJournalInfo journalInfo() {
    return medlineCitation != null ? medlineCitation.journalInfo() : null;
  }

  /** Supplemental PubMed data such as history events and article identifiers. */
  public PubmedData pubmedData() {
    return pubmedData != null ? pubmedData : EMPTY_PUBMED_DATA;
  }

  /** Keyword list merged from all keyword blocks. */
  public List<String> keywords() {
    return medlineCitation != null ? medlineCitation.keywords() : List.of();
  }

  /** Convenience accessor for article identifiers (e.g., DOI, PMC). */
  public List<PubmedData.ArticleId> articleIds() {
    return pubmedData != null ? pubmedData.articleIds() : List.of();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class MedlineCitation {

    @JacksonXmlProperty(localName = "PMID")
    private Pmid pmid;

    @JacksonXmlProperty(localName = "Article")
    private Article article;

    @JacksonXmlProperty(localName = "MedlineJournalInfo")
    private MedlineJournalInfo journalInfo;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "KeywordList")
    private List<KeywordList> keywordLists;

    private MedlineCitation() {}

    private String pmid() {
      return pmid != null ? pmid.value : null;
    }

    private Article article() {
      return article;
    }

    private MedlineJournalInfo journalInfo() {
      return journalInfo;
    }

    private List<String> keywords() {
      if (keywordLists == null || keywordLists.isEmpty()) {
        return List.of();
      }
      List<String> values = new ArrayList<>();
      for (KeywordList list : keywordLists) {
        values.addAll(list.values());
      }
      return List.copyOf(values);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class KeywordList {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Keyword")
    private List<Keyword> keywords;

    private KeywordList() {}

    private List<String> values() {
      if (keywords == null || keywords.isEmpty()) {
        return List.of();
      }
      List<String> values = new ArrayList<>(keywords.size());
      for (Keyword keyword : keywords) {
        if (keyword.value != null && !keyword.value.isBlank()) {
          values.add(keyword.value);
        }
      }
      return values;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class Keyword {

    @JacksonXmlText private String value;

    private Keyword() {}
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class Pmid {

    @JacksonXmlText private String value;

    private Pmid() {}
  }
}
