package com.patra.catalog.infra.adapter.parser.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.patra.catalog.infra.adapter.parser.PubmedXmlElements;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import com.patra.common.model.CanonicalPublication;
import com.patra.common.model.CanonicalPublication.Author;
import com.patra.common.model.CanonicalPublication.Identifier;
import com.patra.common.model.CanonicalPublication.Journal;
import com.patra.common.model.CanonicalPublication.MeshHeading;
import com.patra.common.model.CanonicalPublication.PublicationType;
import com.patra.common.model.enums.PublicationIdentifierType;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// CanonicalPublicationParsingStrategy 单元测试。
///
/// 验证 PubMed 文献解析策略的正确性。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("CanonicalPublicationParsingStrategy 策略")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class CanonicalPublicationParsingStrategyTest {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  private final CanonicalPublicationParsingStrategy strategy =
      CanonicalPublicationParsingStrategy.INSTANCE;

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
      assertNotNull(CanonicalPublicationParsingStrategy.INSTANCE);
      assertEquals(CanonicalPublicationParsingStrategy.INSTANCE, strategy);
    }
  }

  // ========== 标识符解析测试 ==========

  @Nested
  @DisplayName("标识符解析")
  class IdentifierParsing {

    @Test
    @DisplayName("应正确解析 PMID")
    void shouldParsePmid() throws Exception {
      var xml = minimalArticleXml("12345678", "Test Title", "101234567", 2024);
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertThat(result.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(PublicationIdentifierType.PMID, "12345678"));
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(
                  PublicationIdentifierType.DOI, "10.1000/example.doi"));
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(PublicationIdentifierType.PMC, "PMC1234567"));
    }
  }

  // ========== 标题解析测试 ==========

  @Nested
  @DisplayName("标题解析")
  class TitleParsing {

    @Test
    @DisplayName("应正确解析 ArticleTitle 到 title")
    void shouldParseArticleTitle() throws Exception {
      var xml = minimalArticleXml("1", "Sample Article Title", "N", 2024);
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("Sample Article Title", result.getTitle());
    }

    @Test
    @DisplayName("应正确解析 VernacularTitle 到 originalTitle")
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("English Title", result.getTitle());
      assertEquals("中文标题", result.getOriginalTitle());
    }
  }

  // ========== 期刊信息解析测试 ==========

  @Nested
  @DisplayName("期刊信息解析")
  class JournalInfoParsing {

    @Test
    @DisplayName("应解析 NlmUniqueID 到 journal.nlmUniqueId")
    void shouldParseNlmUniqueId() throws Exception {
      var xml = minimalArticleXml("1", "T", "101234567", 2024);
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getJournal());
      assertEquals("101234567", result.getJournal().getNlmUniqueId());
    }

    @Test
    @DisplayName("应解析 Print ISSN 到 journal.issn")
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Journal journal = result.getJournal();
      assertNotNull(journal);
      assertEquals("1234-5678", journal.getIssn());
      assertEquals("print", journal.getIssnType());
    }

    @Test
    @DisplayName("应解析 Electronic ISSN 到 journal.issn（当无 Print ISSN 时）")
    void shouldParseElectronicIssnWhenNoPrint() throws Exception {
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Journal journal = result.getJournal();
      assertNotNull(journal);
      assertEquals("8765-4321", journal.getIssn());
      assertEquals("electronic", journal.getIssnType());
    }

    @Test
    @DisplayName("Print ISSN 应优先于 Electronic ISSN")
    void printIssnShouldTakePrecedenceOverElectronic() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <ISSN IssnType="Print">1234-5678</ISSN>
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Journal journal = result.getJournal();
      assertNotNull(journal);
      assertEquals("1234-5678", journal.getIssn());
      assertEquals("print", journal.getIssnType());
    }

    @Test
    @DisplayName("应解析 ISSNLinking 到 journal.issnLinking")
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("1111-2222", result.getJournal().getIssnLinking());
    }

    @Test
    @DisplayName("应解析期刊标题到 journal.title")
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("Journal of Examples", result.getJournal().getTitle());
    }

    @Test
    @DisplayName("应解析 Volume 和 Issue 到 journal")
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Journal journal = result.getJournal();
      assertEquals("10", journal.getVolume());
      assertEquals("2", journal.getIssue());
    }
  }

  // ========== 出版日期解析测试 ==========

  @Nested
  @DisplayName("出版日期解析")
  class PubDateParsing {

    @Test
    @DisplayName("应解析完整的 PubDate 到 dates.published")
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getDates());
      assertEquals(LocalDate.of(2024, 6, 15), result.getDates().getPublished());
    }

    @Test
    @DisplayName("应解析仅有 Year 的 PubDate（月日默认为1）")
    void shouldParseYearOnlyPubDateWithDefaults() throws Exception {
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(LocalDate.of(2024, 1, 1), result.getDates().getPublished());
    }

    @Test
    @DisplayName("应从 MedlineDate 提取年份")
    void shouldExtractYearFromMedlineDate() throws Exception {
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      // MedlineDate 时，年份应被提取，月日默认为1
      assertEquals(LocalDate.of(2024, 1, 1), result.getDates().getPublished());
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(LocalDate.of(2024, 12, 1), result.getDates().getPublished());
    }
  }

  // ========== 语言解析测试 ==========

  @Nested
  @DisplayName("语言解析")
  class LanguageParsing {

    @Test
    @DisplayName("应将 PubMed 语言代码转换为 ISO 639-1")
    void shouldConvertLanguageCodeToIso6391() throws Exception {
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("en", result.getLanguage());
    }

    @Test
    @DisplayName("应取第一个语言作为主语言")
    void shouldUseFirstLanguageAsPrimary() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Language>chi</Language>
                <Language>eng</Language>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("zh", result.getLanguage());
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("epublish", result.getPublicationStatus());
    }

    @Test
    @DisplayName("应解析 AuthorList CompleteYN 属性到 authorsComplete")
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertTrue(result.getAuthorsComplete());
    }
  }

  // ========== 作者解析测试 ==========

  @Nested
  @DisplayName("作者解析")
  class AuthorParsing {

    @Test
    @DisplayName("应解析作者基本信息")
    void shouldParseAuthorBasicInfo() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList CompleteYN="Y">
                  <Author>
                    <LastName>Smith</LastName>
                    <ForeName>John David</ForeName>
                    <Initials>JD</Initials>
                    <Suffix>Jr</Suffix>
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getAuthors());
      assertThat(result.getAuthors()).hasSize(1);
      Author author = result.getAuthors().getFirst();
      assertEquals("Smith", author.getLastName());
      assertEquals("John David", author.getForeName());
      assertEquals("JD", author.getInitials());
      assertEquals("Jr", author.getSuffix());
    }

    @Test
    @DisplayName("应解析多个作者")
    void shouldParseMultipleAuthors() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList>
                  <Author>
                    <LastName>First</LastName>
                  </Author>
                  <Author>
                    <LastName>Second</LastName>
                  </Author>
                  <Author>
                    <LastName>Third</LastName>
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAuthors()).hasSize(3);
      assertThat(result.getAuthors())
          .extracting(Author::getLastName)
          .containsExactly("First", "Second", "Third");
    }

    @Test
    @DisplayName("应解析集体作者名称到 organizationName")
    void shouldParseCollectiveNameToOrganizationName() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList>
                  <Author>
                    <CollectiveName>COVID-19 Research Consortium</CollectiveName>
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAuthors()).hasSize(1);
      Author author = result.getAuthors().getFirst();
      assertEquals("COVID-19 Research Consortium", author.getOrganizationName());
      assertNull(author.getLastName());
    }

    @Test
    @DisplayName("应解析作者 ORCID 标识符")
    void shouldParseAuthorOrcid() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList>
                  <Author>
                    <LastName>Doe</LastName>
                    <ForeName>John</ForeName>
                    <Identifier Source="ORCID">0000-0001-2345-6789</Identifier>
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Author author = result.getAuthors().getFirst();
      assertNotNull(author.getIdentifiers());
      assertThat(author.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(
                  PublicationIdentifierType.ORCID, "0000-0001-2345-6789"));
    }

    @Test
    @DisplayName("应解析作者机构信息")
    void shouldParseAuthorAffiliations() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList>
                  <Author>
                    <LastName>Doe</LastName>
                    <AffiliationInfo>
                      <Affiliation>Department of Medicine, Harvard University, Boston, MA, USA</Affiliation>
                    </AffiliationInfo>
                    <AffiliationInfo>
                      <Affiliation>Massachusetts General Hospital, Boston, MA, USA</Affiliation>
                    </AffiliationInfo>
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Author author = result.getAuthors().getFirst();
      assertNotNull(author.getAffiliations());
      assertThat(author.getAffiliations()).hasSize(2);
      assertThat(author.getAffiliations().getFirst().getName()).contains("Harvard University");
    }
  }

  // ========== 摘要解析测试 ==========

  @Nested
  @DisplayName("摘要解析")
  class AbstractParsing {

    @Test
    @DisplayName("应解析非结构化摘要")
    void shouldParseUnstructuredAbstract() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Abstract>
                  <AbstractText>This is the full abstract content without structure.</AbstractText>
                </Abstract>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getAbstractContent());
      assertNotNull(result.getAbstractContent().getSections());
      assertThat(result.getAbstractContent().getSections()).hasSize(1);
      assertEquals(
          "This is the full abstract content without structure.",
          result.getAbstractContent().getSections().getFirst().getContent());
      assertNull(result.getAbstractContent().getSections().getFirst().getLabel());
    }

    @Test
    @DisplayName("应解析结构化摘要（带 Label）")
    void shouldParseStructuredAbstract() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Abstract>
                  <AbstractText Label="BACKGROUND">Background content here.</AbstractText>
                  <AbstractText Label="METHODS">Methods content here.</AbstractText>
                  <AbstractText Label="RESULTS">Results content here.</AbstractText>
                  <AbstractText Label="CONCLUSIONS">Conclusions content here.</AbstractText>
                </Abstract>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getAbstractContent());
      assertThat(result.getAbstractContent().getSections()).hasSize(4);
      assertEquals("BACKGROUND", result.getAbstractContent().getSections().get(0).getLabel());
      assertEquals(
          "Background content here.",
          result.getAbstractContent().getSections().get(0).getContent());
      assertEquals("CONCLUSIONS", result.getAbstractContent().getSections().get(3).getLabel());
    }
  }

  // ========== MeSH 主题词解析测试 ==========

  @Nested
  @DisplayName("MeSH 主题词解析")
  class MeshHeadingParsing {

    @Test
    @DisplayName("应解析 MeSH 主题词 Descriptor")
    void shouldParseMeshDescriptor() throws Exception {
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
              <MeshHeadingList>
                <MeshHeading>
                  <DescriptorName UI="D006801" MajorTopicYN="Y">Humans</DescriptorName>
                </MeshHeading>
              </MeshHeadingList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getMeshHeadings());
      assertThat(result.getMeshHeadings()).hasSize(1);
      MeshHeading heading = result.getMeshHeadings().getFirst();
      assertNotNull(heading.getDescriptorName());
      assertEquals("D006801", heading.getDescriptorName().getUi());
      assertEquals("Humans", heading.getDescriptorName().getTerm());
      assertTrue(heading.getDescriptorName().getMajorTopic());
    }

    @Test
    @DisplayName("应解析带 Qualifier 的 MeSH 主题词")
    void shouldParseMeshWithQualifiers() throws Exception {
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
              <MeshHeadingList>
                <MeshHeading>
                  <DescriptorName UI="D000328" MajorTopicYN="N">Adult</DescriptorName>
                  <QualifierName UI="Q000235" MajorTopicYN="Y">genetics</QualifierName>
                  <QualifierName UI="Q000378" MajorTopicYN="N">metabolism</QualifierName>
                </MeshHeading>
              </MeshHeadingList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      MeshHeading heading = result.getMeshHeadings().getFirst();
      assertNotNull(heading.getQualifierNames());
      assertThat(heading.getQualifierNames()).hasSize(2);
      assertEquals("Q000235", heading.getQualifierNames().get(0).getUi());
      assertEquals("genetics", heading.getQualifierNames().get(0).getTerm());
      assertTrue(heading.getQualifierNames().get(0).getMajorTopic());
    }

    @Test
    @DisplayName("应解析多个 MeSH 主题词")
    void shouldParseMultipleMeshHeadings() throws Exception {
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
              <MeshHeadingList>
                <MeshHeading>
                  <DescriptorName UI="D006801">Humans</DescriptorName>
                </MeshHeading>
                <MeshHeading>
                  <DescriptorName UI="D000328">Adult</DescriptorName>
                </MeshHeading>
                <MeshHeading>
                  <DescriptorName UI="D008297">Male</DescriptorName>
                </MeshHeading>
              </MeshHeadingList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getMeshHeadings()).hasSize(3);
      assertThat(result.getMeshHeadings())
          .extracting(h -> h.getDescriptorName().getTerm())
          .containsExactly("Humans", "Adult", "Male");
    }
  }

  // ========== 补充 MeSH 概念解析测试 ==========

  @Nested
  @DisplayName("补充 MeSH 概念解析")
  class SupplMeshParsing {

    @Test
    @DisplayName("应解析 SupplMeshList 中的补充 MeSH 概念")
    void shouldParseSupplMeshNames() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article><ArticleTitle>T</ArticleTitle></Article>
              <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              <SupplMeshList>
                <SupplMeshName UI="C538003" Type="Disease">Aspirin-sensitive asthma</SupplMeshName>
                <SupplMeshName UI="C095232" Type="Protocol">FOLFOX protocol</SupplMeshName>
              </SupplMeshList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getSupplMeshNames()).hasSize(2);

      var first = result.getSupplMeshNames().get(0);
      assertThat(first.getUi()).isEqualTo("C538003");
      assertThat(first.getName()).isEqualTo("Aspirin-sensitive asthma");
      assertThat(first.getType()).isEqualTo("Disease");

      var second = result.getSupplMeshNames().get(1);
      assertThat(second.getUi()).isEqualTo("C095232");
      assertThat(second.getName()).isEqualTo("FOLFOX protocol");
      assertThat(second.getType()).isEqualTo("Protocol");
    }

    @Test
    @DisplayName("无 SupplMeshList 时应返回空列表")
    void shouldReturnEmptyListWhenNoSupplMeshList() throws Exception {
      var xml = minimalArticleXml("1", "T", "N", 2024);
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getSupplMeshNames()).isEmpty();
    }

    @Test
    @DisplayName("应忽略缺少 UI 的 SupplMeshName")
    void shouldIgnoreSupplMeshWithoutUi() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article><ArticleTitle>T</ArticleTitle></Article>
              <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              <SupplMeshList>
                <SupplMeshName Type="Disease">No UI concept</SupplMeshName>
                <SupplMeshName UI="C538003" Type="Disease">Valid concept</SupplMeshName>
              </SupplMeshList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getSupplMeshNames()).hasSize(1);
      assertThat(result.getSupplMeshNames().get(0).getUi()).isEqualTo("C538003");
    }
  }

  // ========== 发表类型解析测试 ==========

  @Nested
  @DisplayName("发表类型解析")
  class PublicationTypeParsing {

    @Test
    @DisplayName("应解析发表类型")
    void shouldParsePublicationType() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <PublicationTypeList>
                  <PublicationType UI="D016428">Journal Article</PublicationType>
                </PublicationTypeList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getPublicationTypes());
      assertThat(result.getPublicationTypes()).hasSize(1);
      PublicationType pubType = result.getPublicationTypes().getFirst();
      assertEquals("D016428", pubType.getId());
      assertEquals("Journal Article", pubType.getValue());
    }

    @Test
    @DisplayName("应解析多个发表类型")
    void shouldParseMultiplePublicationTypes() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <PublicationTypeList>
                  <PublicationType UI="D016428">Journal Article</PublicationType>
                  <PublicationType UI="D016454">Review</PublicationType>
                  <PublicationType UI="D013485">Research Support, Non-U.S. Gov't</PublicationType>
                </PublicationTypeList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getPublicationTypes()).hasSize(3);
      assertThat(result.getPublicationTypes())
          .extracting(PublicationType::getValue)
          .containsExactly("Journal Article", "Review", "Research Support, Non-U.S. Gov't");
    }
  }

  // ========== 页码解析测试 ==========

  @Nested
  @DisplayName("页码解析")
  class PaginationParsing {

    @Test
    @DisplayName("应解析 MedlinePgn 页码")
    void shouldParseMedlinePgn() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Pagination>
                  <MedlinePgn>123-145</MedlinePgn>
                </Pagination>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getPagination());
      assertEquals("123-145", result.getPagination().getMedlinePgn());
    }

    @Test
    @DisplayName("应解析电子文章编号格式的页码")
    void shouldParseElectronicArticleNumber() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Pagination>
                  <MedlinePgn>e12345</MedlinePgn>
                </Pagination>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getPagination());
      assertEquals("e12345", result.getPagination().getMedlinePgn());
    }
  }

  // ========== 完整解析测试 ==========

  @Nested
  @DisplayName("完整文章解析")
  class FullArticleParsing {

    @Test
    @DisplayName("应正确解析完整的 PubmedArticle 到 CanonicalPublication")
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

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);

      // 标识符
      assertThat(result.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(PublicationIdentifierType.PMID, "12345678"),
              org.assertj.core.groups.Tuple.tuple(
                  PublicationIdentifierType.DOI, "10.1000/example.doi"),
              org.assertj.core.groups.Tuple.tuple(PublicationIdentifierType.PMC, "PMC1234567"));

      // 标题
      assertEquals("Sample Article Title", result.getTitle());
      assertEquals("样本文章标题", result.getOriginalTitle());

      // 期刊信息
      Journal journal = result.getJournal();
      assertNotNull(journal);
      assertEquals("101234567", journal.getNlmUniqueId());
      assertEquals("1234-5678", journal.getIssn()); // Print 优先
      assertEquals("print", journal.getIssnType());
      assertEquals("1111-2222", journal.getIssnLinking());
      assertEquals("Journal of Examples", journal.getTitle());
      assertEquals("10", journal.getVolume());
      assertEquals("2", journal.getIssue());

      // 日期
      assertEquals(LocalDate.of(2024, 6, 15), result.getDates().getPublished());

      // 语言（第一个语言转换为 ISO 639-1）
      assertEquals("en", result.getLanguage());

      // 元数据
      assertEquals("epublish", result.getPublicationStatus());
      assertTrue(result.getAuthorsComplete());
    }
  }

  // ========== 其他语言摘要（OtherAbstract）解析测试 ==========

  @Nested
  @DisplayName("OtherAbstract 解析")
  class OtherAbstractParsing {

    @Test
    @DisplayName("应解析单个 OtherAbstract")
    void shouldParseSingleOtherAbstract() throws Exception {
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
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText>这是中文摘要内容。</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getAlternativeAbstracts());
      assertThat(result.getAlternativeAbstracts()).hasSize(1);
      var altAbstract = result.getAlternativeAbstracts().getFirst();
      assertThat(altAbstract.getLanguage()).isEqualTo("zh");
      assertThat(altAbstract.getType()).isEqualTo("Publisher");
      assertThat(altAbstract.getText()).isEqualTo("这是中文摘要内容。");
    }

    @Test
    @DisplayName("应解析多个 OtherAbstract")
    void shouldParseMultipleOtherAbstracts() throws Exception {
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
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText>中文摘要。</AbstractText>
              </OtherAbstract>
              <OtherAbstract Language="jpn" Type="AIMSHP">
                <AbstractText>日本語の要約。</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAlternativeAbstracts()).hasSize(2);
      assertThat(result.getAlternativeAbstracts())
          .extracting(
              CanonicalPublication.AlternativeAbstract::getLanguage,
              CanonicalPublication.AlternativeAbstract::getType)
          .containsExactly(
              org.assertj.core.groups.Tuple.tuple("zh", "Publisher"),
              org.assertj.core.groups.Tuple.tuple("ja", "AIMSHP"));
    }

    @Test
    @DisplayName("应解析结构化 OtherAbstract")
    void shouldParseStructuredOtherAbstract() throws Exception {
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
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText Label="BACKGROUND">背景内容。</AbstractText>
                <AbstractText Label="METHODS">方法内容。</AbstractText>
                <AbstractText Label="RESULTS">结果内容。</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAlternativeAbstracts()).hasSize(1);
      var altAbstract = result.getAlternativeAbstracts().getFirst();
      assertThat(altAbstract.getLanguage()).isEqualTo("zh");
      // 结构化摘要应拼接成完整文本
      assertThat(altAbstract.getText()).contains("背景内容", "方法内容", "结果内容");
    }

    @Test
    @DisplayName("无 OtherAbstract 时应返回空列表")
    void shouldReturnEmptyListWhenNoOtherAbstract() throws Exception {
      var xml = minimalArticleXml("1", "T", "N", 2024);
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAlternativeAbstracts()).isEmpty();
    }

    @Test
    @DisplayName("应忽略空的 OtherAbstract")
    void shouldIgnoreEmptyOtherAbstract() throws Exception {
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
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText></AbstractText>
              </OtherAbstract>
              <OtherAbstract Language="jpn" Type="AIMSHP">
                <AbstractText>有效的日文摘要。</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      // 空摘要应被忽略，只保留有效的日文摘要
      assertThat(result.getAlternativeAbstracts()).hasSize(1);
      assertThat(result.getAlternativeAbstracts().getFirst().getLanguage()).isEqualTo("ja");
    }

    @Test
    @DisplayName("应正确解析 OtherAbstract 的 CopyrightInformation")
    void shouldParseCopyrightInformation() throws Exception {
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
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText>中文摘要。</AbstractText>
                <CopyrightInformation>© 2024 Publisher.</CopyrightInformation>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAlternativeAbstracts()).hasSize(1);
      var altAbstract = result.getAlternativeAbstracts().getFirst();
      assertThat(altAbstract.getCopyright()).isEqualTo("© 2024 Publisher.");
    }

    @Test
    @DisplayName("应处理 plain-language 类型的 OtherAbstract")
    void shouldParsePlainLanguageType() throws Exception {
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
              <OtherAbstract Language="eng" Type="plain-language-summary">
                <AbstractText>Plain language summary for patients.</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAlternativeAbstracts()).hasSize(1);
      var altAbstract = result.getAlternativeAbstracts().getFirst();
      assertThat(altAbstract.getLanguage()).isEqualTo("en");
      assertThat(altAbstract.getType()).isEqualTo("plain-language-summary");
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
