package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.ArrayList;
import java.util.List;

/**
 * Simplified PubMed article metadata extracted from the Medline citation.
 *
 * @author
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

  /** Journal metadata associated with the article. */
  public Journal journal() {
    return journal;
  }

  /** Article title. */
  public String title() {
    return title;
  }

  /** Primary language reported by PubMed. */
  public String language() {
    if (languages == null || languages.isEmpty()) {
      return null;
    }
    return languages.get(0);
  }

  /** Abstract sections (label + text). */
  public List<AbstractSection> abstractSections() {
    return abstractContent != null ? abstractContent.sections() : List.of();
  }

  /** List of parsed authors. */
  public List<Author> authors() {
    if (authors == null || authors.isEmpty()) {
      return List.of();
    }
    return List.copyOf(authors);
  }

  /** Publication type identifiers assigned by PubMed. */
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

  /** Abstract section extracted from the citation with optional label. */
  public record AbstractSection(String label, String text) {}
}
