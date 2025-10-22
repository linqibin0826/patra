package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.ArrayList;
import java.util.List;

/**
 * Supplemental PubMed data block parsed directly from XML.
 *
 * @author
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PubmedData {

  @JacksonXmlProperty(localName = "PublicationStatus")
  private String publicationStatus;

  @JacksonXmlProperty(localName = "History")
  private History history;

  @JacksonXmlProperty(localName = "ArticleIdList")
  private ArticleIdList articleIdList;

  public PubmedData() {}

  /** Publication status reported by PubMed. */
  public String publicationStatus() {
    return publicationStatus;
  }

  /** Immutable list of history events describing publication timeline. */
  public List<HistoryEvent> history() {
    return history != null ? history.events() : List.of();
  }

  /** Immutable list of article identifiers (e.g., DOI, PMC). */
  public List<ArticleId> articleIds() {
    return articleIdList != null ? articleIdList.articleIds() : List.of();
  }

  @JsonIgnore
  public boolean hasArticleIds() {
    return !articleIds().isEmpty();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class History {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "PubMedPubDate")
    private List<PubDate> events;

    private History() {}

    private List<HistoryEvent> events() {
      if (events == null || events.isEmpty()) {
        return List.of();
      }
      List<HistoryEvent> mapped = new ArrayList<>(events.size());
      for (PubDate event : events) {
        mapped.add(event.toHistoryEvent());
      }
      return List.copyOf(mapped);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class PubDate {

    @JacksonXmlProperty(isAttribute = true, localName = "PubStatus")
    private String status;

    @JacksonXmlProperty(localName = "Year")
    private String year;

    @JacksonXmlProperty(localName = "Month")
    private String month;

    @JacksonXmlProperty(localName = "Day")
    private String day;

    private PubDate() {}

    private HistoryEvent toHistoryEvent() {
      return new HistoryEvent(status, year, month, day);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ArticleIdList {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "ArticleId")
    private List<ArticleId> articleIds;

    private ArticleIdList() {}

    private List<ArticleId> articleIds() {
      if (articleIds == null || articleIds.isEmpty()) {
        return List.of();
      }
      return List.copyOf(articleIds);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class ArticleId {

    @JacksonXmlText private String value;

    @JacksonXmlProperty(isAttribute = true, localName = "IdType")
    private String type;

    public ArticleId() {}

    /** Identifier value (e.g., DOI, PMC). */
    public String value() {
      return value;
    }

    /** Identifier type reported by PubMed (e.g., doi, pmc, pubmed). */
    public String type() {
      return type;
    }
  }

  /** History event describing a key publication milestone. */
  public record HistoryEvent(String status, String year, String month, String day) {}
}
