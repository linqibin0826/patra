package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 Medline 引文中提取的简化 PubMed 文章元数据。
 *
 * <p>包含文章的核心信息,包括标题、摘要、作者列表、期刊信息、发表类型等。 这是 PubMed 文章数据的主要数据传输对象。
 *
 * <p><b>主要字段:</b>
 *
 * <ul>
 *   <li><b>journal</b>: 期刊元数据 (标题、ISSN、发表日期等)
 *   <li><b>title</b>: 文章标题
 *   <li><b>abstractContent</b>: 摘要内容 (可能包含多个分段)
 *   <li><b>authors</b>: 作者列表
 *   <li><b>languages</b>: 语言列表
 *   <li><b>publicationTypes</b>: 发表类型标识符
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Article {

  @JacksonXmlProperty(localName = "Journal")
  private Journal journal;

  @JacksonXmlProperty(localName = "ArticleTitle")
  private String title;

  @JacksonXmlProperty(localName = "Abstract")
  private AbstractContent abstractContent;

  @JacksonXmlElementWrapper(useWrapping = false)
  @JacksonXmlProperty(localName = "Language")
  private List<String> languages;

  @JacksonXmlElementWrapper(localName = "AuthorList")
  @JacksonXmlProperty(localName = "Author")
  private List<Author> authors;

  @JacksonXmlElementWrapper(localName = "PublicationTypeList")
  @JacksonXmlProperty(localName = "PublicationType")
  private List<String> publicationTypes;

  public Article() {}

  /** 与文章关联的期刊元数据。 */
  public Journal journal() {
    return journal;
  }

  /** 文章标题。 */
  public String title() {
    return title;
  }

  /** PubMed 报告的主要语言。 */
  public String language() {
    if (languages == null || languages.isEmpty()) {
      return null;
    }
    return languages.get(0);
  }

  /** 摘要分段 (标签 + 文本)。 */
  public List<AbstractSection> abstractSections() {
    return abstractContent != null ? abstractContent.sections() : List.of();
  }

  /** 已解析的作者列表。 */
  public List<Author> authors() {
    if (authors == null || authors.isEmpty()) {
      return List.of();
    }
    return List.copyOf(authors);
  }

  /** PubMed 分配的发表类型标识符。 */
  public List<String> publicationTypes() {
    if (publicationTypes == null || publicationTypes.isEmpty()) {
      return List.of();
    }
    return List.copyOf(publicationTypes);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class AbstractContent {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "AbstractText")
    private List<AbstractText> sections;

    private AbstractContent() {}

    private List<AbstractSection> sections() {
      if (sections == null || sections.isEmpty()) {
        return List.of();
      }
      List<AbstractSection> result = new ArrayList<>(sections.size());
      for (AbstractText section : sections) {
        String text = section.value;
        if (text != null && !text.isBlank()) {
          result.add(new AbstractSection(section.label, text));
        }
      }
      return List.copyOf(result);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class AbstractText {

    @JacksonXmlText private String value;

    @JacksonXmlProperty(isAttribute = true, localName = "Label")
    private String label;

    private AbstractText() {}
  }

  /** 从引文中提取的摘要分段,带可选标签。 */
  public record AbstractSection(String label, String text) {}
}
