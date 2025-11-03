package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.ArrayList;
import java.util.List;

/**
 * 从XML直接解析的PubMed文章简化视图。
 *
 * <p>该类封装了PubMed文章的核心信息,包括PMID、文章元数据、期刊信息和补充数据。 提供便捷的访问器方法用于获取关键字、文章标识符等常用信息。
 *
 * @author Patra
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PubmedArticle {

  private static final PubmedData EMPTY_PUBMED_DATA = new PubmedData();

  /** Medline引用信息,包含PMID和文章核心元数据 */
  @JacksonXmlProperty(localName = "MedlineCitation")
  private MedlineCitation medlineCitation;

  /** PubMed补充数据,包含历史事件和文章标识符 */
  @JacksonXmlProperty(localName = "PubmedData")
  private PubmedData pubmedData;

  public PubmedArticle() {}

  /** 返回PubMed标识符(PMID) */
  public String pmid() {
    return medlineCitation != null ? medlineCitation.pmid() : null;
  }

  /** 返回核心文章元数据 */
  public Article article() {
    return medlineCitation != null ? medlineCitation.article() : null;
  }

  /** 返回Medline报告的期刊信息 */
  public MedlineJournalInfo journalInfo() {
    return medlineCitation != null ? medlineCitation.journalInfo() : null;
  }

  /** 返回PubMed补充数据,如历史事件和文章标识符 */
  public PubmedData pubmedData() {
    return pubmedData != null ? pubmedData : EMPTY_PUBMED_DATA;
  }

  /** 返回从所有关键字块合并的关键字列表 */
  public List<String> keywords() {
    return medlineCitation != null ? medlineCitation.keywords() : List.of();
  }

  /** 返回文章标识符列表的便捷访问器(例如DOI、PMC) */
  public List<PubmedData.ArticleId> articleIds() {
    return pubmedData != null ? pubmedData.articleIds() : List.of();
  }

  /** Medline引用信息的内部表示 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class MedlineCitation {

    /** PubMed标识符对象 */
    @JacksonXmlProperty(localName = "PMID")
    private Pmid pmid;

    /** 文章核心元数据 */
    @JacksonXmlProperty(localName = "Article")
    private Article article;

    /** Medline期刊信息 */
    @JacksonXmlProperty(localName = "MedlineJournalInfo")
    private MedlineJournalInfo journalInfo;

    /** 关键字列表集合 */
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

  /** 关键字列表的内部表示 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class KeywordList {

    /** 关键字集合 */
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

  /** 单个关键字的内部表示 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class Keyword {

    /** 关键字文本内容 */
    @JacksonXmlText private String value;

    private Keyword() {}
  }

  /** PMID标识符的内部表示 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class Pmid {

    /** PMID数值 */
    @JacksonXmlText private String value;

    private Pmid() {}
  }
}
