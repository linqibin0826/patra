package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.List;

/**
 * 从 PubMed 引文中衍生的期刊元数据。
 *
 * <p>包含期刊的标识信息 (ISSN)、标题、ISO 缩写以及期刊期号和发表日期信息。
 *
 * <p><b>主要字段:</b>
 *
 * <ul>
 *   <li><b>issn</b>: 期刊 ISSN (国际标准连续出版物编号)
 *   <li><b>title</b>: 完整期刊标题
 *   <li><b>isoAbbreviation</b>: ISO 标准期刊缩写
 *   <li><b>journalIssue</b>: 期号信息,包含发表日期
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Journal {

  @JacksonXmlProperty(localName = "ISSN")
  private Issn issn;

  @JacksonXmlProperty(localName = "Title")
  private String title;

  @JacksonXmlProperty(localName = "ISOAbbreviation")
  private String isoAbbreviation;

  @JacksonXmlProperty(localName = "JournalIssue")
  private JournalIssue journalIssue;

  public Journal() {}

  /** Journal ISSN value. */
  public String issn() {
    return issn != null ? issn.value : null;
  }

  /** Journal ISSN type attribute. */
  public String issnType() {
    return issn != null ? issn.type : null;
  }

  /** Full journal title. */
  public String title() {
    return title;
  }

  /** ISO abbreviation for the journal. */
  public String isoAbbreviation() {
    return isoAbbreviation;
  }

  /** Issue information containing publication date. */
  public JournalIssue journalIssue() {
    return journalIssue;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class JournalIssue {

    @JacksonXmlProperty(localName = "PubDate")
    private PubDate pubDate;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Season")
    private List<String> seasons;

    public JournalIssue() {}

    /** Publication date metadata. */
    public PubDate pubDate() {
      return pubDate;
    }

    /** Season metadata if provided (rare). */
    public List<String> seasons() {
      return seasons != null ? List.copyOf(seasons) : List.of();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class PubDate {

    @JacksonXmlProperty(localName = "Year")
    private String year;

    @JacksonXmlProperty(localName = "Month")
    private String month;

    @JacksonXmlProperty(localName = "Day")
    private String day;

    public PubDate() {}

    public String year() {
      return year;
    }

    public String month() {
      return month;
    }

    public String day() {
      return day;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class Issn {

    @JacksonXmlText private String value;

    @JacksonXmlProperty(isAttribute = true, localName = "IssnType")
    private String type;

    private Issn() {}
  }
}
