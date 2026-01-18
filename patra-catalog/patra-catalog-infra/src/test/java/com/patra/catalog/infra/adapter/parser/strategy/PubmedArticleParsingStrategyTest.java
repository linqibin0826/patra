package com.patra.catalog.infra.adapter.parser.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.patra.catalog.domain.model.vo.publication.pubmed.PubmedArticle;
import com.patra.catalog.infra.adapter.parser.PubmedXmlElements;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// PubmedArticleParsingStrategy 单元测试。
///
/// 验证 PubMed 文献解析策略的正确性。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedArticleParsingStrategy 策略")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class PubmedArticleParsingStrategyTest {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  private final PubmedArticleParsingStrategy strategy = PubmedArticleParsingStrategy.INSTANCE;

  // ========== 策略契约测试 ==========

  @Nested
  @DisplayName("策略契约")
  class StrategyContract {

    @Test
    @DisplayName("rootElementName() 应返回 PubmedArticle")
    void rootElementName_shouldReturnPubmedArticle() {
      assertEquals(PubmedXmlElements.Container.PUBMED_ARTICLE, strategy.rootElementName());
    }

    @Test
    @DisplayName("INSTANCE 应为非空单例")
    void instance_shouldBeNonNullSingleton() {
      assertNotNull(PubmedArticleParsingStrategy.INSTANCE);
      assertEquals(PubmedArticleParsingStrategy.INSTANCE, strategy);
    }
  }

  // ========== PMID 解析测试 ==========

  @Nested
  @DisplayName("PMID 解析")
  class PmidParsing {

    @Test
    @DisplayName("应正确解析 PMID")
    void shouldParsePmid() throws Exception {
      var xml = minimalArticleXml("12345678", "Test Title", "101234567", 2024);
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertEquals("12345678", result.pmid());
    }

    @Test
    @DisplayName("缺少 PMID 时应返回 null")
    void shouldReturnNullWhenMissingPmid() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <Article>
                <ArticleTitle>Test</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>101234567</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }
  }

  // ========== 标题解析测试 ==========

  @Nested
  @DisplayName("标题解析")
  class TitleParsing {

    @Test
    @DisplayName("应正确解析 ArticleTitle")
    void shouldParseArticleTitle() throws Exception {
      var xml = minimalArticleXml("1", "Sample Article Title", "N", 2024);
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("Sample Article Title", result.articleTitle());
    }

    @Test
    @DisplayName("应正确解析 VernacularTitle")
    void shouldParseVernacularTitle() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>English Title</ArticleTitle>
                <VernacularTitle>中文标题</VernacularTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("English Title", result.articleTitle());
      assertEquals("中文标题", result.vernacularTitle());
    }
  }

  // ========== 期刊信息解析测试 ==========

  @Nested
  @DisplayName("期刊信息解析")
  class JournalInfoParsing {

    @Test
    @DisplayName("应解析 NlmUniqueID")
    void shouldParseNlmUniqueId() throws Exception {
      var xml = minimalArticleXml("1", "T", "101234567", 2024);
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("101234567", result.nlmUniqueId());
    }

    @Test
    @DisplayName("应解析 Print ISSN")
    void shouldParsePrintIssn() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <ISSN IssnType="Print">1234-5678</ISSN>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("1234-5678", result.issnPrint());
    }

    @Test
    @DisplayName("应解析 Electronic ISSN")
    void shouldParseElectronicIssn() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <ISSN IssnType="Electronic">8765-4321</ISSN>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("8765-4321", result.issnElectronic());
    }

    @Test
    @DisplayName("应解析 ISSNLinking")
    void shouldParseIssnLinking() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
                <ISSNLinking>1111-2222</ISSNLinking>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("1111-2222", result.issnLinking());
    }

    @Test
    @DisplayName("应解析期刊标题")
    void shouldParseJournalTitle() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <Title>Journal of Examples</Title>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("Journal of Examples", result.journalTitle());
    }

    @Test
    @DisplayName("应解析 Volume 和 Issue")
    void shouldParseVolumeAndIssue() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <JournalIssue>
                    <Volume>10</Volume>
                    <Issue>2</Issue>
                  </JournalIssue>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("10", result.volume());
      assertEquals("2", result.issue());
    }
  }

  // ========== 出版日期解析测试 ==========

  @Nested
  @DisplayName("出版日期解析")
  class PubDateParsing {

    @Test
    @DisplayName("应解析完整的 PubDate (Year/Month/Day)")
    void shouldParseCompletePubDate() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <JournalIssue>
                    <PubDate>
                      <Year>2024</Year>
                      <Month>Jun</Month>
                      <Day>15</Day>
                    </PubDate>
                  </JournalIssue>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(2024, result.pubYear());
      assertEquals(6, result.pubMonth());
      assertEquals(15, result.pubDay());
    }

    @Test
    @DisplayName("应解析仅有 Year 的 PubDate")
    void shouldParseYearOnlyPubDate() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <JournalIssue>
                    <PubDate>
                      <Year>2024</Year>
                    </PubDate>
                  </JournalIssue>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(2024, result.pubYear());
      assertNull(result.pubMonth());
      assertNull(result.pubDay());
    }

    @Test
    @DisplayName("应解析 MedlineDate")
    void shouldParseMedlineDate() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <JournalIssue>
                    <PubDate>
                      <MedlineDate>2024 Jun-Jul</MedlineDate>
                    </PubDate>
                  </JournalIssue>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("2024 Jun-Jul", result.medlineDate());
      // MedlineDate 时应从字符串中提取年份
      assertEquals(2024, result.pubYear());
    }

    @Test
    @DisplayName("应将月份缩写转换为数字")
    void shouldConvertMonthAbbreviationToNumber() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <JournalIssue>
                    <PubDate>
                      <Year>2024</Year>
                      <Month>Dec</Month>
                    </PubDate>
                  </JournalIssue>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(12, result.pubMonth());
    }
  }

  // ========== ArticleIdList 解析测试 ==========

  @Nested
  @DisplayName("ArticleIdList 解析")
  class ArticleIdListParsing {

    @Test
    @DisplayName("应解析 DOI")
    void shouldParseDoi() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <ArticleIdList>
                <ArticleId IdType="doi">10.1000/example.doi</ArticleId>
              </ArticleIdList>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("10.1000/example.doi", result.doi());
    }

    @Test
    @DisplayName("应解析 PMC ID")
    void shouldParsePmcId() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <ArticleIdList>
                <ArticleId IdType="pmc">PMC1234567</ArticleId>
              </ArticleIdList>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("PMC1234567", result.pmcId());
    }

    @Test
    @DisplayName("应同时解析 DOI 和 PMC ID")
    void shouldParseBothDoiAndPmcId() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <ArticleIdList>
                <ArticleId IdType="pubmed">1</ArticleId>
                <ArticleId IdType="doi">10.1000/example</ArticleId>
                <ArticleId IdType="pmc">PMC9999999</ArticleId>
              </ArticleIdList>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("10.1000/example", result.doi());
      assertEquals("PMC9999999", result.pmcId());
    }
  }

  // ========== 语言解析测试 ==========

  @Nested
  @DisplayName("语言解析")
  class LanguageParsing {

    @Test
    @DisplayName("应解析单个语言")
    void shouldParseSingleLanguage() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Language>eng</Language>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.languages()).containsExactly("eng");
    }

    @Test
    @DisplayName("应解析多个语言")
    void shouldParseMultipleLanguages() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Language>eng</Language>
                <Language>chi</Language>
                <Language>jpn</Language>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.languages()).containsExactly("eng", "chi", "jpn");
    }
  }

  // ========== 其他元数据解析测试 ==========

  @Nested
  @DisplayName("其他元数据解析")
  class OtherMetadataParsing {

    @Test
    @DisplayName("应解析 PublicationStatus")
    void shouldParsePublicationStatus() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <PublicationStatus>epublish</PublicationStatus>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("epublish", result.publicationStatus());
    }

    @Test
    @DisplayName("应解析 AuthorList CompleteYN 属性")
    void shouldParseAuthorsComplete() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList CompleteYN="Y">
                  <Author>
                    <LastName>Doe</LastName>
                    <ForeName>John</ForeName>
                  </Author>
                </AuthorList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertTrue(result.authorsComplete());
    }
  }

  // ========== 完整解析测试 ==========

  @Nested
  @DisplayName("完整文章解析")
  class FullArticleParsing {

    @Test
    @DisplayName("应正确解析完整的 PubmedArticle")
    void shouldParseCompleteArticle() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation Status="MEDLINE">
              <PMID Version="1">12345678</PMID>
              <Article PubModel="Print">
                <Journal>
                  <ISSN IssnType="Print">1234-5678</ISSN>
                  <ISSN IssnType="Electronic">8765-4321</ISSN>
                  <JournalIssue CitedMedium="Print">
                    <Volume>10</Volume>
                    <Issue>2</Issue>
                    <PubDate>
                      <Year>2024</Year>
                      <Month>Jun</Month>
                      <Day>15</Day>
                    </PubDate>
                  </JournalIssue>
                  <Title>Journal of Examples</Title>
                </Journal>
                <ArticleTitle>Sample Article Title</ArticleTitle>
                <VernacularTitle>样本文章标题</VernacularTitle>
                <Language>eng</Language>
                <Language>chi</Language>
                <AuthorList CompleteYN="Y">
                  <Author>
                    <LastName>Doe</LastName>
                    <ForeName>John</ForeName>
                  </Author>
                </AuthorList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>101234567</NlmUniqueID>
                <ISSNLinking>1111-2222</ISSNLinking>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <PublicationStatus>epublish</PublicationStatus>
              <ArticleIdList>
                <ArticleId IdType="pubmed">12345678</ArticleId>
                <ArticleId IdType="doi">10.1000/example.doi</ArticleId>
                <ArticleId IdType="pmc">PMC1234567</ArticleId>
              </ArticleIdList>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      PubmedArticle result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      // 标识符
      assertEquals("12345678", result.pmid());
      assertEquals("10.1000/example.doi", result.doi());
      assertEquals("PMC1234567", result.pmcId());
      // 标题
      assertEquals("Sample Article Title", result.articleTitle());
      assertEquals("样本文章标题", result.vernacularTitle());
      // 期刊信息
      assertEquals("101234567", result.nlmUniqueId());
      assertEquals("1234-5678", result.issnPrint());
      assertEquals("8765-4321", result.issnElectronic());
      assertEquals("1111-2222", result.issnLinking());
      assertEquals("Journal of Examples", result.journalTitle());
      assertEquals("10", result.volume());
      assertEquals("2", result.issue());
      // 日期
      assertEquals(2024, result.pubYear());
      assertEquals(6, result.pubMonth());
      assertEquals(15, result.pubDay());
      // 元数据
      assertThat(result.languages()).containsExactly("eng", "chi");
      assertEquals("epublish", result.publicationStatus());
      assertTrue(result.authorsComplete());
    }
  }

  // ========== 辅助方法 ==========

  /// 创建最小有效的 PubmedArticle XML。
  private String minimalArticleXml(String pmid, String title, String nlmId, int year) {
    return """
        <PubmedArticle>
          <MedlineCitation>
            <PMID>%s</PMID>
            <Article>
              <Journal>
                <JournalIssue>
                  <PubDate>
                    <Year>%d</Year>
                  </PubDate>
                </JournalIssue>
              </Journal>
              <ArticleTitle>%s</ArticleTitle>
            </Article>
            <MedlineJournalInfo>
              <NlmUniqueID>%s</NlmUniqueID>
            </MedlineJournalInfo>
          </MedlineCitation>
        </PubmedArticle>
        """
        .formatted(pmid, year, title, nlmId);
  }

  /// 创建定位到起始元素的 XMLStreamReader。
  private XMLStreamReader createReaderAtStartElement(String xml) throws XMLStreamException {
    var reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(xml));
    while (reader.hasNext()) {
      if (reader.next() == XMLStreamConstants.START_ELEMENT) {
        break;
      }
    }
    return reader;
  }
}
