package com.patra.catalog.infra.adapter.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.patra.catalog.domain.model.vo.publication.pubmed.PubmedArticle;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// PubmedXmlParserAdapter 集成测试。
///
/// 验证 PubMed XML 解析适配器的端到端正确性。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedXmlParserAdapter 集成测试")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class PubmedXmlParserAdapterIT {

  private final PubmedXmlParserAdapter adapter = new PubmedXmlParserAdapter();

  @Nested
  @DisplayName("parse() 流式解析")
  class ParseStreamTest {

    @Test
    @DisplayName("应正确解析包含单个文章的 XML")
    void shouldParseSingleArticle() {
      var xml =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <!DOCTYPE PubmedArticleSet PUBLIC "-//NLM//DTD PubMedArticle, 1st January 2025//EN"
            "https://dtd.nlm.nih.gov/ncbi/pubmed/out/pubmed_250101.dtd">
          <PubmedArticleSet>
            <PubmedArticle>
              <MedlineCitation Status="MEDLINE">
                <PMID Version="1">12345678</PMID>
                <Article PubModel="Print">
                  <Journal>
                    <ISSN IssnType="Print">1234-5678</ISSN>
                    <JournalIssue CitedMedium="Print">
                      <Volume>10</Volume>
                      <Issue>2</Issue>
                      <PubDate>
                        <Year>2024</Year>
                        <Month>Jun</Month>
                      </PubDate>
                    </JournalIssue>
                    <Title>Journal of Examples</Title>
                  </Journal>
                  <ArticleTitle>Sample Article Title</ArticleTitle>
                  <Language>eng</Language>
                </Article>
                <MedlineJournalInfo>
                  <NlmUniqueID>101234567</NlmUniqueID>
                </MedlineJournalInfo>
              </MedlineCitation>
              <PubmedData>
                <PublicationStatus>epublish</PublicationStatus>
                <ArticleIdList>
                  <ArticleId IdType="doi">10.1000/example</ArticleId>
                </ArticleIdList>
              </PubmedData>
            </PubmedArticle>
          </PubmedArticleSet>
          """;
      InputStream inputStream = toInputStream(xml);

      List<PubmedArticle> articles;
      try (var stream = adapter.parse(inputStream)) {
        articles = stream.toList();
      }

      assertThat(articles).hasSize(1);
      PubmedArticle article = articles.getFirst();
      assertEquals("12345678", article.pmid());
      assertEquals("Sample Article Title", article.articleTitle());
      assertEquals("10.1000/example", article.doi());
      assertEquals("101234567", article.nlmUniqueId());
      assertEquals("1234-5678", article.issnPrint());
      assertEquals("10", article.volume());
      assertEquals("2", article.issue());
      assertEquals(2024, article.pubYear());
      assertEquals(6, article.pubMonth());
    }

    @Test
    @DisplayName("应正确解析包含多个文章的 XML")
    void shouldParseMultipleArticles() {
      var xml =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <PubmedArticleSet>
            <PubmedArticle>
              <MedlineCitation>
                <PMID>1</PMID>
                <Article>
                  <ArticleTitle>First Article</ArticleTitle>
                </Article>
                <MedlineJournalInfo>
                  <NlmUniqueID>N1</NlmUniqueID>
                </MedlineJournalInfo>
              </MedlineCitation>
            </PubmedArticle>
            <PubmedArticle>
              <MedlineCitation>
                <PMID>2</PMID>
                <Article>
                  <ArticleTitle>Second Article</ArticleTitle>
                </Article>
                <MedlineJournalInfo>
                  <NlmUniqueID>N2</NlmUniqueID>
                </MedlineJournalInfo>
              </MedlineCitation>
            </PubmedArticle>
            <PubmedArticle>
              <MedlineCitation>
                <PMID>3</PMID>
                <Article>
                  <ArticleTitle>Third Article</ArticleTitle>
                </Article>
                <MedlineJournalInfo>
                  <NlmUniqueID>N3</NlmUniqueID>
                </MedlineJournalInfo>
              </MedlineCitation>
            </PubmedArticle>
          </PubmedArticleSet>
          """;
      InputStream inputStream = toInputStream(xml);

      List<PubmedArticle> articles;
      try (var stream = adapter.parse(inputStream)) {
        articles = stream.toList();
      }

      assertThat(articles).hasSize(3);
      assertEquals("1", articles.get(0).pmid());
      assertEquals("2", articles.get(1).pmid());
      assertEquals("3", articles.get(2).pmid());
    }

    @Test
    @DisplayName("应跳过缺少 PMID 的无效记录")
    void shouldSkipInvalidRecordsWithoutPmid() {
      var xml =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <PubmedArticleSet>
            <PubmedArticle>
              <MedlineCitation>
                <PMID>1</PMID>
                <Article>
                  <ArticleTitle>Valid Article</ArticleTitle>
                </Article>
                <MedlineJournalInfo>
                  <NlmUniqueID>N1</NlmUniqueID>
                </MedlineJournalInfo>
              </MedlineCitation>
            </PubmedArticle>
            <PubmedArticle>
              <MedlineCitation>
                <Article>
                  <ArticleTitle>Invalid - No PMID</ArticleTitle>
                </Article>
              </MedlineCitation>
            </PubmedArticle>
            <PubmedArticle>
              <MedlineCitation>
                <PMID>3</PMID>
                <Article>
                  <ArticleTitle>Another Valid Article</ArticleTitle>
                </Article>
                <MedlineJournalInfo>
                  <NlmUniqueID>N3</NlmUniqueID>
                </MedlineJournalInfo>
              </MedlineCitation>
            </PubmedArticle>
          </PubmedArticleSet>
          """;
      InputStream inputStream = toInputStream(xml);

      List<PubmedArticle> articles;
      try (var stream = adapter.parse(inputStream)) {
        articles = stream.toList();
      }

      assertThat(articles).hasSize(2);
      assertEquals("1", articles.get(0).pmid());
      assertEquals("3", articles.get(1).pmid());
    }

    @Test
    @DisplayName("应正确处理空的 PubmedArticleSet")
    void shouldHandleEmptyArticleSet() {
      var xml =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <PubmedArticleSet>
          </PubmedArticleSet>
          """;
      InputStream inputStream = toInputStream(xml);

      List<PubmedArticle> articles;
      try (var stream = adapter.parse(inputStream)) {
        articles = stream.toList();
      }

      assertThat(articles).isEmpty();
    }
  }

  @Nested
  @DisplayName("惰性求值验证")
  class LazyEvaluationTest {

    @Test
    @DisplayName("Stream 应支持惰性求值")
    void shouldSupportLazyEvaluation() {
      var xml =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <PubmedArticleSet>
            <PubmedArticle>
              <MedlineCitation>
                <PMID>1</PMID>
                <Article><ArticleTitle>A1</ArticleTitle></Article>
                <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              </MedlineCitation>
            </PubmedArticle>
            <PubmedArticle>
              <MedlineCitation>
                <PMID>2</PMID>
                <Article><ArticleTitle>A2</ArticleTitle></Article>
                <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              </MedlineCitation>
            </PubmedArticle>
            <PubmedArticle>
              <MedlineCitation>
                <PMID>3</PMID>
                <Article><ArticleTitle>A3</ArticleTitle></Article>
                <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              </MedlineCitation>
            </PubmedArticle>
          </PubmedArticleSet>
          """;
      InputStream inputStream = toInputStream(xml);

      // 使用 limit() 验证惰性求值 - 只获取前 2 条
      List<PubmedArticle> articles;
      try (var stream = adapter.parse(inputStream)) {
        articles = stream.limit(2).toList();
      }

      assertThat(articles).hasSize(2);
      assertEquals("1", articles.get(0).pmid());
      assertEquals("2", articles.get(1).pmid());
    }
  }

  // ========== 辅助方法 ==========

  private InputStream toInputStream(String xml) {
    return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
  }
}
