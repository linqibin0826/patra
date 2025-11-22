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

/// 解析后的 PubMed EFetch 响应,包含文章详情视图和可选的 UID 列表视图。
///
/// EFetch 主要返回 XML 格式的详细文章内容。当指定 `rettype=uilist` 和 `retmode=text` 时, PubMed 返回换行符分隔的
/// UID 列表,本类可解析这两种格式。
///
/// **支持的响应格式:**
///
/// - **XML文章详情**: 包含完整的 PubmedPublication 对象列表，根元素为 &lt;PubmedArticleSet&gt;
///   - **纯文本UID列表**: 仅包含标识符列表,用于轻量级批量处理
///
/// **重要说明**: PubMed E-utilities API 返回的XML根元素是 `<PubmedArticleSet>`，
/// 每个文献记录标签为 `<PubmedArticle>`。参见:
/// https://www.ncbi.nlm.nih.gov/books/NBK25499/
///
/// @author linqibin
/// @since 0.1.0
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "PubmedArticleSet")
public final class EFetchResponse {

  @JacksonXmlElementWrapper(useWrapping = false)
  @JacksonXmlProperty(localName = "PubmedArticle")
  private List<PubmedPublication> articles;

  @JsonIgnore private List<String> uids;

  public EFetchResponse() {
    this.articles = List.of();
    this.uids = List.of();
  }

  private EFetchResponse(List<PubmedPublication> articles, List<String> uids) {
    this.articles = articles;
    this.uids = uids;
  }

  /// 不可变的已解析文章记录列表。
  public List<PubmedPublication> articles() {
    return articles != null ? articles : List.of();
  }

  /// `rettype=uilist` 返回的不可变 UID 列表。
  public List<String> uids() {
    return uids != null ? uids : List.of();
  }

  /// 当响应既不包含文章也不包含 UID 列表时返回 true。
  public boolean isEmpty() {
    return articles().isEmpty() && uids().isEmpty();
  }

  /// 将 XML 负载解析为 {@link EFetchResponse}。
  public static EFetchResponse fromXml(XmlMapper xmlMapper, String xml) throws IOException {
    EFetchResponse response = xmlMapper.readValue(xml, EFetchResponse.class);
    response.normalise();
    return response;
  }

  /// 将纯文本 UID 列表负载解析为 {@link EFetchResponse}。
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

  /// 创建空响应占位符。
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
