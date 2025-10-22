package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parsed PubMed EFetch response with curated article view and optional UID list view.
 *
 * <p>EFetch primarily returns XML for detailed article content. When {@code rettype=uilist} and
 * {@code retmode=text}, PubMed returns a newline-delimited UID list which is parsed here.
 *
 * @author
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "PubmedArticleSet")
public final class EFetchResponse {

  @JacksonXmlElementWrapper(useWrapping = false)
  @JacksonXmlProperty(localName = "PubmedArticle")
  private List<PubmedArticle> articles;

  @JsonIgnore private List<String> uids;

  public EFetchResponse() {
    this.articles = List.of();
    this.uids = List.of();
  }

  private EFetchResponse(List<PubmedArticle> articles, List<String> uids) {
    this.articles = articles;
    this.uids = uids;
  }

  /** Immutable list of parsed article records. */
  public List<PubmedArticle> articles() {
    return articles != null ? articles : List.of();
  }

  /** Immutable list of UIDs returned by {@code rettype=uilist}. */
  public List<String> uids() {
    return uids != null ? uids : List.of();
  }

  /** True when the response carries neither articles nor UID list. */
  public boolean isEmpty() {
    return articles().isEmpty() && uids().isEmpty();
  }

  /** Parse XML payload into an {@link EFetchResponse}. */
  public static EFetchResponse fromXml(XmlMapper xmlMapper, String xml) throws IOException {
    EFetchResponse response = xmlMapper.readValue(xml, EFetchResponse.class);
    response.normalise();
    return response;
  }

  /** Parse plain-text UID list payload into an {@link EFetchResponse}. */
  public static EFetchResponse fromUidListText(String text) {
    if (text == null || text.isBlank()) {
      return new EFetchResponse(List.of(), List.of());
    }
    List<String> values = new ArrayList<>();
    text.lines()
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .forEach(
            line -> {
              Arrays.stream(line.split("[\\s,]+"))
                  .map(String::trim)
                  .filter(token -> !token.isEmpty())
                  .forEach(values::add);
            });
    return new EFetchResponse(List.of(), List.copyOf(values));
  }

  /** Create an empty response placeholder. */
  public static EFetchResponse empty() {
    return new EFetchResponse(List.of(), List.of());
  }

  private void normalise() {
    if (articles == null || articles.isEmpty()) {
      articles = List.of();
    } else {
      articles = List.copyOf(articles);
    }
    if (uids == null || uids.isEmpty()) {
      uids = List.of();
    } else {
      uids = List.copyOf(uids);
    }
  }
}
