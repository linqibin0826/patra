package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.ArrayList;
import java.util.List;

/**
 * 从XML直接解析的PubMed补充数据块。
 *
 * <p>包含文章的发布状态、历史事件时间线和各类文章标识符(DOI、PMC等)。 提供便捷的方法用于访问和检查这些补充信息。
 *
 * @author Patra
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PubmedData {

  /** 文章发布状态(如epublish、ppublish等) */
  @JacksonXmlProperty(localName = "PublicationStatus")
  private String publicationStatus;

  /** 文章发布历史时间线 */
  @JacksonXmlProperty(localName = "History")
  private History history;

  /** 文章标识符列表 */
  @JacksonXmlProperty(localName = "ArticleIdList")
  private ArticleIdList articleIdList;

  public PubmedData() {}

  /** 返回PubMed报告的发布状态 */
  public String publicationStatus() {
    return publicationStatus;
  }

  /** 返回描述发布时间线的不可变历史事件列表 */
  public List<HistoryEvent> history() {
    return history != null ? history.events() : List.of();
  }

  /** 返回文章标识符的不可变列表(例如DOI、PMC) */
  public List<ArticleId> articleIds() {
    return articleIdList != null ? articleIdList.articleIds() : List.of();
  }

  /** 检查是否包含文章标识符 */
  @JsonIgnore
  public boolean hasArticleIds() {
    return !articleIds().isEmpty();
  }

  /** 历史事件集合的内部表示 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class History {

    /** 发布日期事件列表 */
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

  /** 发布日期的内部表示 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class PubDate {

    /** 发布状态(如received、accepted、epublish等) */
    @JacksonXmlProperty(isAttribute = true, localName = "PubStatus")
    private String status;

    /** 年份 */
    @JacksonXmlProperty(localName = "Year")
    private String year;

    /** 月份 */
    @JacksonXmlProperty(localName = "Month")
    private String month;

    /** 日期 */
    @JacksonXmlProperty(localName = "Day")
    private String day;

    private PubDate() {}

    private HistoryEvent toHistoryEvent() {
      return new HistoryEvent(status, year, month, day);
    }
  }

  /** 文章标识符列表的内部表示 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ArticleIdList {

    /** 文章标识符集合 */
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

  /**
   * 文章标识符,表示各类唯一标识(DOI、PMC、PubMed等)。
   *
   * <p>每个标识符包含类型和对应的值。
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class ArticleId {

    /** 标识符值 */
    @JacksonXmlText private String value;

    /** 标识符类型 */
    @JacksonXmlProperty(isAttribute = true, localName = "IdType")
    private String type;

    public ArticleId() {}

    /** 返回标识符值(例如DOI、PMC值) */
    public String value() {
      return value;
    }

    /** 返回PubMed报告的标识符类型(例如doi、pmc、pubmed) */
    public String type() {
      return type;
    }
  }

  /**
   * 历史事件记录,描述关键的发布里程碑。
   *
   * @param status 发布状态(如received、accepted、epublish等)
   * @param year 年份
   * @param month 月份
   * @param day 日期
   */
  public record HistoryEvent(String status, String year, String month, String day) {}
}
