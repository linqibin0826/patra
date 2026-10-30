package dev.patra.catalog.infra.publication.batch;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication;
import dev.patra.catalog.infra.publication.parser.PubmedXmlParserAdapter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PubmedArticleSplitter` 单元测试：逐篇切出 `<PubmedArticle>`，收集 `DeleteCitation`。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedArticleSplitter 单元测试")
class PubmedArticleSplitterTest {

  private static final String FILE =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <!DOCTYPE PubmedArticleSet PUBLIC "-//NLM//DTD PubMedArticle, 1st January 2025//EN" "https://dtd.nlm.nih.gov/ncbi/pubmed/out/pubmed_250101.dtd">
      <PubmedArticleSet xmlns:mml="http://www.w3.org/1998/Math/MathML">
        <PubmedArticle>
          <MedlineCitation Status="MEDLINE" Owner="NLM">
            <PMID Version="1">34449189</PMID>
            <Article>
              <ArticleTitle>Empagliflozin &amp; heart failure</ArticleTitle>
            </Article>
            <MedlineJournalInfo><NlmUniqueID>0255562</NlmUniqueID></MedlineJournalInfo>
            <CommentsCorrectionsList>
              <CommentsCorrections RefType="CommentIn"><PMID Version="1">99999999</PMID></CommentsCorrections>
            </CommentsCorrectionsList>
          </MedlineCitation>
        </PubmedArticle>
        <PubmedArticle>
          <MedlineCitation Status="MEDLINE" Owner="NLM">
            <PMID Version="1">37000001</PMID>
            <Article>
              <ArticleTitle>CO<sub>2</sub> and <mml:math><mml:mi>x</mml:mi></mml:math></ArticleTitle>
            </Article>
            <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
          </MedlineCitation>
        </PubmedArticle>
        <DeleteCitation>
          <PMID Version="1">11111111</PMID>
          <PMID Version="1">22222222</PMID>
        </DeleteCitation>
      </PubmedArticleSet>
      """;

  @Test
  @DisplayName("逐篇切出原文，DeleteCitation 的 PMID 被收集")
  void should_split_each_pubmed_article_and_collect_delete_citations() throws Exception {
    List<String> deleted = new ArrayList<>();
    List<String> articles = new ArrayList<>();
    try (PubmedArticleSplitter splitter = splitter(deleted)) {
      for (Optional<String> next = splitter.next(); next.isPresent(); next = splitter.next()) {
        articles.add(next.get());
      }
    }

    assertThat(articles).hasSize(2);
    assertThat(articles)
        .allSatisfy(
            a -> {
              assertThat(a).startsWith("<PubmedArticle");
              assertThat(a).endsWith("</PubmedArticle>");
            });
    assertThat(deleted).containsExactly("11111111", "22222222");
  }

  @Test
  @DisplayName("切出的原文能被 parseArticle 解析回同一篇：PMID、转义字符、带命名空间的 MathML")
  void should_round_trip_split_article_through_parse_article() throws Exception {
    PubmedXmlParserAdapter parser = new PubmedXmlParserAdapter();
    List<CanonicalPublication> parsed = new ArrayList<>();
    try (PubmedArticleSplitter splitter = splitter(new ArrayList<>())) {
      for (Optional<String> next = splitter.next(); next.isPresent(); next = splitter.next()) {
        parsed.add(parser.parseArticle(next.get()));
      }
    }

    assertThat(parsed.get(0).getTitle()).isEqualTo("Empagliflozin & heart failure");
    assertThat(PubmedArticleSplitter.pmidOf(splitAll().get(0))).isEqualTo("34449189");
    assertThat(PubmedArticleSplitter.pmidOf(splitAll().get(1))).isEqualTo("37000001");
    assertThat(parsed.get(1).getTitle()).contains("CO").contains("x");
  }

  private List<String> splitAll() throws Exception {
    List<String> articles = new ArrayList<>();
    try (PubmedArticleSplitter splitter = splitter(new ArrayList<>())) {
      for (Optional<String> next = splitter.next(); next.isPresent(); next = splitter.next()) {
        articles.add(next.get());
      }
    }
    return articles;
  }

  /// 两篇文章合计 100,002 个 `&amp;` 时，切分器照常切出两篇、第二篇 PMID 正确。
  ///
  /// @throws Exception 切分失败时（修复前为 `JAXP00010003`）
  @Test
  @DisplayName("全文累计超过 100,000 个 &amp;（真实的 pubmed26n1379）：照常逐篇切出，不因 JDK 实体上限失败")
  void should_split_file_with_more_than_100000_entity_references() throws Exception {
    StringBuilder file = new StringBuilder("<?xml version=\"1.0\"?><PubmedArticleSet>");
    for (int a = 1; a <= 2; a++) {
      file.append("<PubmedArticle><MedlineCitation><PMID Version=\"1\">")
          .append(a)
          .append("</PMID></MedlineCitation><PubmedData><ReferenceList>");
      for (int i = 0; i < 50_001; i++) {
        file.append("<Reference><Citation>Knight &amp; Ortel</Citation></Reference>");
      }
      file.append("</ReferenceList></PubmedData></PubmedArticle>");
    }
    file.append("</PubmedArticleSet>");
    List<String> articles = new ArrayList<>();

    try (PubmedArticleSplitter splitter =
        new PubmedArticleSplitter(
            new ByteArrayInputStream(file.toString().getBytes(StandardCharsets.UTF_8)),
            pmid -> {})) {
      for (Optional<String> next = splitter.next(); next.isPresent(); next = splitter.next()) {
        articles.add(next.get());
      }
    }

    assertThat(articles).hasSize(2);
    assertThat(PubmedArticleSplitter.pmidOf(articles.get(1))).isEqualTo("2");
  }

  private static PubmedArticleSplitter splitter(List<String> deleted) {
    return new PubmedArticleSplitter(
        new ByteArrayInputStream(FILE.getBytes(StandardCharsets.UTF_8)), deleted::add);
  }
}
