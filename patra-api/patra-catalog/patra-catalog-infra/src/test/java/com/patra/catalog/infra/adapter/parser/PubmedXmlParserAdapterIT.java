package com.patra.catalog.infra.adapter.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.patra.common.model.CanonicalPublication;
import com.patra.common.model.enums.PublicationIdentifierType;
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

      List<CanonicalPublication> publications;
      try (var stream = adapter.parse(inputStream)) {
        publications = stream.toList();
      }

      assertThat(publications).hasSize(1);
      CanonicalPublication pub = publications.getFirst();
      assertEquals("12345678", extractPmid(pub));
      assertEquals("Sample Article Title", pub.getTitle());
      assertEquals("10.1000/example", extractDoi(pub));
      assertEquals("101234567", pub.getJournal().getNlmUniqueId());
      assertEquals("1234-5678", pub.getJournal().getIssn());
      assertEquals("print", pub.getJournal().getIssnType());
      assertEquals("10", pub.getJournal().getVolume());
      assertEquals("2", pub.getJournal().getIssue());
      assertEquals(2024, pub.getDates().getPublished().getYear());
      assertEquals(6, pub.getDates().getPublished().getMonthValue());
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

      List<CanonicalPublication> publications;
      try (var stream = adapter.parse(inputStream)) {
        publications = stream.toList();
      }

      assertThat(publications).hasSize(3);
      assertEquals("1", extractPmid(publications.get(0)));
      assertEquals("2", extractPmid(publications.get(1)));
      assertEquals("3", extractPmid(publications.get(2)));
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

      List<CanonicalPublication> publications;
      try (var stream = adapter.parse(inputStream)) {
        publications = stream.toList();
      }

      assertThat(publications).hasSize(2);
      assertEquals("1", extractPmid(publications.get(0)));
      assertEquals("3", extractPmid(publications.get(1)));
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

      List<CanonicalPublication> publications;
      try (var stream = adapter.parse(inputStream)) {
        publications = stream.toList();
      }

      assertThat(publications).isEmpty();
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
      List<CanonicalPublication> publications;
      try (var stream = adapter.parse(inputStream)) {
        publications = stream.limit(2).toList();
      }

      assertThat(publications).hasSize(2);
      assertEquals("1", extractPmid(publications.get(0)));
      assertEquals("2", extractPmid(publications.get(1)));
    }
  }

  // ========== 辅助方法 ==========

  /// 从 CanonicalPublication 中提取 PMID。
  private String extractPmid(CanonicalPublication publication) {
    if (publication.getIdentifiers() == null) {
      return null;
    }
    return publication.getIdentifiers().stream()
        .filter(id -> PublicationIdentifierType.PMID.equals(id.getType()))
        .map(CanonicalPublication.Identifier::getValue)
        .findFirst()
        .orElse(null);
  }

  /// 从 CanonicalPublication 中提取 DOI。
  private String extractDoi(CanonicalPublication publication) {
    if (publication.getIdentifiers() == null) {
      return null;
    }
    return publication.getIdentifiers().stream()
        .filter(id -> PublicationIdentifierType.DOI.equals(id.getType()))
        .map(CanonicalPublication.Identifier::getValue)
        .findFirst()
        .orElse(null);
  }

  private InputStream toInputStream(String xml) {
    return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
  }
}
